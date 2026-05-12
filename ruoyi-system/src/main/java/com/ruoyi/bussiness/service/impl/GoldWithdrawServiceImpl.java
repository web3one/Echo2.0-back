package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.domain.dto.GoldWithdrawDTO;
import com.ruoyi.bussiness.domain.vo.GoldWalletVO;
import com.ruoyi.bussiness.domain.vo.GoldWithdrawResultVO;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TGoldWalletMapper;
import com.ruoyi.bussiness.mapper.TGoldWithdrawOrderMapper;
import com.ruoyi.bussiness.service.IGoldWalletService;
import com.ruoyi.bussiness.service.IGoldWithdrawService;
import com.ruoyi.bussiness.service.ITAppUserService;
import com.ruoyi.bussiness.service.ITAppWalletRecordService;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 金矿子钱包→现货 USDT 提现实现。
 *
 * 5% 拆解 PRD §12.2 第 5 条 + figma founderPerk5：
 *   1% → t_pool_account.founder_share（创世 49 等分；by daily_fee_summary_job 聚合落账）
 *   4% → t_pool_account.platform_fee
 *
 * @date 2026-05-15
 */
@Service
@Slf4j
public class GoldWithdrawServiceImpl implements IGoldWithdrawService {

    private static final String USDT_SYMBOL = "usdt";
    private static final String CFG_FEE_RATE = "gold.withdraw.fee_rate";
    private static final String CFG_FOUNDER_SHARE_RATE = "gold.withdraw.founder_share_rate";
    /** 提现最小金额（USDT）。低于此值的提现没有意义（手续费几乎吃掉本金）。 */
    private static final BigDecimal MIN_WITHDRAW = new BigDecimal("1");

    @Resource
    private IGoldWalletService goldWalletService;
    @Resource
    private TGoldWalletMapper goldWalletMapper;
    @Resource
    private TGoldWithdrawOrderMapper withdrawOrderMapper;
    @Resource
    private TAppAssetMapper appAssetMapper;
    @Resource
    private ITAppUserService appUserService;
    @Resource
    private ITAppWalletRecordService walletRecordService;
    @Resource
    private ISysConfigService sysConfigService;

    @Override
    public GoldWalletVO getMyWallet(Long userId) {
        TGoldWallet w = goldWalletMapper.selectByUserId(userId);
        if (w == null) {
            w = goldWalletService.getOrCreate(userId);
        }
        BigDecimal feeRate = readRate(CFG_FEE_RATE);
        BigDecimal founderShareRate = readRate(CFG_FOUNDER_SHARE_RATE);
        return GoldWalletVO.from(w, feeRate, founderShareRate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoldWithdrawResultVO submitWithdraw(Long userId,
                                               GoldWithdrawDTO dto,
                                               String clientIp,
                                               String clientUserAgent) {
        // 0. 入参校验
        if (dto == null || StrUtil.isBlank(dto.getIdempotentKey())) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.idempotent.empty"));
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(MIN_WITHDRAW) < 0) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.amount.too_small"));
        }
        String assetType = StrUtil.isBlank(dto.getAssetType())
                ? TGoldWithdrawOrder.ASSET_USDT
                : dto.getAssetType().toUpperCase();
        if (!TGoldWithdrawOrder.ASSET_USDT.equals(assetType)) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.asset.unsupported"));
        }

        // 1. 幂等命中（同 idempotentKey 同用户）
        TGoldWithdrawOrder existing = withdrawOrderMapper.selectByIdempotentKey(dto.getIdempotentKey());
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ServiceException(MessageUtils.message("gold.withdraw.duplicate"));
            }
            log.info("gold withdraw idempotent hit: userId={} key={} orderId={}",
                    userId, dto.getIdempotentKey(), existing.getId());
            return GoldWithdrawResultVO.from(existing, true);
        }

        // 2. 用户/冻结校验
        TAppUser user = appUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if ("2".equals(user.getIsFreeze())) {
            throw new ServiceException(MessageUtils.message("c2c.user.frozen"));
        }

        // 3. 资金密码校验（与创世 / 现货大额提现一致）
        TAppUserDetail detail = appUserService.selectUserDetailByUserId(userId);
        if (detail == null || StrUtil.isBlank(detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("user.password_notbind"));
        }
        if (!SecurityUtils.matchesPassword(dto.getFundPassword(), detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.fund_password.invalid"));
        }

        // 4. 读 sys_config 费率（快照到订单）
        BigDecimal feeRate = readRate(CFG_FEE_RATE);
        BigDecimal founderShareRate = readRate(CFG_FOUNDER_SHARE_RATE);
        if (feeRate.compareTo(BigDecimal.ZERO) < 0
                || founderShareRate.compareTo(BigDecimal.ZERO) < 0
                || founderShareRate.compareTo(feeRate) > 0) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.config.invalid"));
        }

        // 5. 算各项金额
        BigDecimal gross = dto.getAmount().setScale(8, RoundingMode.HALF_UP);
        BigDecimal feeAmount = gross.multiply(feeRate).setScale(8, RoundingMode.HALF_UP);
        BigDecimal founderShare = gross.multiply(founderShareRate).setScale(8, RoundingMode.HALF_UP);
        BigDecimal platformFee = feeAmount.subtract(founderShare);
        BigDecimal net = gross.subtract(feeAmount);
        if (net.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.amount.too_small"));
        }

        // 6. 创建提现单 pending（idempotent_key UK 兜底并发）
        TGoldWithdrawOrder order = new TGoldWithdrawOrder();
        order.setUserId(userId);
        order.setAssetType(assetType);
        order.setGrossAmount(gross);
        order.setFeeRateSnap(feeRate);
        order.setFeeAmount(feeAmount);
        order.setFounderShareRateSnap(founderShareRate);
        order.setFounderShareAmount(founderShare);
        order.setPlatformFeeAmount(platformFee);
        order.setNetAmount(net);
        order.setStatus(TGoldWithdrawOrder.STATUS_PENDING);
        order.setFundPasswordVerified(1);
        order.setIdempotentKey(dto.getIdempotentKey());
        order.setClientIp(clientIp);
        order.setClientUserAgent(truncateUA(clientUserAgent));
        try {
            withdrawOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.duplicate"));
        }

        // 7. 从金矿子钱包扣 gross 全额（含 5% 手续费部分）
        //    用 deductBalance 路径：FOR UPDATE 锁 + 余额校验 + 流水
        Long walletLogId = goldWalletService.deductBalance(
                userId,
                gross,
                TGoldWalletLog.CHANGE_WITHDRAW_OUT,
                TGoldWalletLog.BIZ_REF_WITHDRAW_ORDER,
                String.valueOf(order.getId()),
                "withdraw:" + order.getId(),
                null,
                "WITHDRAW " + order.getId());

        // 8. net (95%) 进现货 USDT 余额（t_app_asset）+ 流水（type=79 GOLD_TO_SPOT_IN）
        Long spotRecordId = creditSpotUsdt(user, net, order.getId());

        // 9. UPDATE order 状态 → completed
        TGoldWithdrawOrder upd = new TGoldWithdrawOrder();
        upd.setId(order.getId());
        upd.setStatus(TGoldWithdrawOrder.STATUS_COMPLETED);
        upd.setCompletedAt(new Date());
        upd.setGoldWalletLogId(walletLogId);
        upd.setSpotWalletRecordId(spotRecordId);
        withdrawOrderMapper.updateById(upd);

        order.setStatus(TGoldWithdrawOrder.STATUS_COMPLETED);
        order.setCompletedAt(upd.getCompletedAt());
        order.setGoldWalletLogId(walletLogId);
        order.setSpotWalletRecordId(spotRecordId);

        log.info("gold withdraw success: userId={} orderId={} gross={} fee={} (founder={} platform={}) net={}",
                userId, order.getId(), gross, feeAmount, founderShare, platformFee, net);
        return GoldWithdrawResultVO.from(order, false);
    }

    @Override
    public IPage<TGoldWithdrawOrder> pageMyWithdrawals(Long userId, Integer pageNum, Integer pageSize) {
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<TGoldWithdrawOrder> p = new Page<>(pn, ps);
        LambdaQueryWrapper<TGoldWithdrawOrder> qw = new LambdaQueryWrapper<TGoldWithdrawOrder>()
                .eq(TGoldWithdrawOrder::getUserId, userId)
                .orderByDesc(TGoldWithdrawOrder::getCreateTime);
        // MP 3.4.1 PaginationInnerInterceptor BUG 规避：手动 selectCount + selectList(LIMIT)
        Integer total = withdrawOrderMapper.selectCount(qw);
        long totalLong = total == null ? 0L : total.longValue();
        p.setTotal(totalLong);
        if (totalLong > 0) {
            qw.last("LIMIT " + ((pn - 1) * ps) + ", " + ps);
            p.setRecords(withdrawOrderMapper.selectList(qw));
        }
        return p;
    }

    private BigDecimal readRate(String key) {
        String v = sysConfigService.selectConfigByKey(key);
        if (StrUtil.isBlank(v)) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.config.invalid"));
        }
        try {
            return new BigDecimal(v.trim()).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ServiceException(MessageUtils.message("gold.withdraw.config.invalid"));
        }
    }

    private Long creditSpotUsdt(TAppUser user, BigDecimal amount, Long orderId) {
        Long userId = user.getUserId();
        TAppAsset asset = appAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, USDT_SYMBOL)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        if (asset == null) {
            asset = new TAppAsset();
            asset.setUserId(userId);
            asset.setSymbol(USDT_SYMBOL);
            asset.setType(AssetEnum.PLATFORM_ASSETS.getCode());
            asset.setAmout(amount);
            asset.setAvailableAmount(amount);
            appAssetMapper.insert(asset);
            walletRecordService.generateRecord(userId, amount,
                    RecordEnum.GOLD_TO_SPOT_IN.getCode(),
                    "",
                    "GOLD2SPOT-" + orderId,
                    RecordEnum.GOLD_TO_SPOT_IN.getInfo() + " #" + orderId,
                    BigDecimal.ZERO,
                    amount,
                    USDT_SYMBOL,
                    user.getAdminParentIds());
        } else {
            BigDecimal beforeAvail = nz(asset.getAvailableAmount());
            BigDecimal beforeTotal = nz(asset.getAmout());
            asset.setAmout(beforeTotal.add(amount));
            asset.setAvailableAmount(beforeAvail.add(amount));
            appAssetMapper.updateByUserId(asset);
            walletRecordService.generateRecord(userId, amount,
                    RecordEnum.GOLD_TO_SPOT_IN.getCode(),
                    "",
                    "GOLD2SPOT-" + orderId,
                    RecordEnum.GOLD_TO_SPOT_IN.getInfo() + " #" + orderId,
                    beforeAvail,
                    beforeAvail.add(amount),
                    USDT_SYMBOL,
                    user.getAdminParentIds());
        }
        // generateRecord 不返回 ID（void），仅作审计；后续可拓展返回 ID
        return null;
    }

    private static String truncateUA(String ua) {
        if (StrUtil.isBlank(ua)) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
