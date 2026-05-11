package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TFounderPurchaseLog;
import com.ruoyi.bussiness.domain.TFounderSeat;
import com.ruoyi.bussiness.domain.dto.FounderPurchaseDTO;
import com.ruoyi.bussiness.domain.vo.FounderPurchaseResultVO;
import com.ruoyi.bussiness.domain.vo.FounderStatusVO;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TFounderPurchaseLogMapper;
import com.ruoyi.bussiness.mapper.TFounderSeatMapper;
import com.ruoyi.bussiness.service.IFounderPurchaseService;
import com.ruoyi.bussiness.service.ITAppUserService;
import com.ruoyi.bussiness.service.ITAppWalletRecordService;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 创世合伙人席位购买服务实现（金矿 Phase 2 A 路线第二组）。
 *
 * @date 2026-05-11
 */
@Service
@Slf4j
public class FounderPurchaseServiceImpl implements IFounderPurchaseService {

    private static final String USDT_SYMBOL = "usdt";
    private static final int TOTAL_SEATS = 49;

    @Resource
    private TFounderSeatMapper founderSeatMapper;

    @Resource
    private TFounderPurchaseLogMapper purchaseLogMapper;

    @Resource
    private TAppAssetMapper appAssetMapper;

    @Resource
    private ITAppUserService appUserService;

    @Resource
    private ITAppWalletRecordService walletRecordService;

    @Override
    public FounderStatusVO getStatus(Long userId) {
        FounderStatusVO vo = new FounderStatusVO();
        vo.setTotalSeats(TOTAL_SEATS);

        List<TFounderSeat> all = founderSeatMapper.selectList(
                new LambdaQueryWrapper<TFounderSeat>().orderByAsc(TFounderSeat::getSeatNo));
        int available = 0, owned = 0, frozen = 0;
        BigDecimal price = null;
        List<FounderStatusVO.SeatBrief> briefs = new ArrayList<>(all.size());
        for (TFounderSeat s : all) {
            String st = s.getStatus();
            if (TFounderSeat.STATUS_AVAILABLE.equals(st)) {
                available++;
                if (price == null && s.getPriceUsdt() != null) price = s.getPriceUsdt();
            } else if (TFounderSeat.STATUS_OWNED.equals(st)) {
                owned++;
            } else if (TFounderSeat.STATUS_FROZEN.equals(st)) {
                frozen++;
            }
            FounderStatusVO.SeatBrief b = new FounderStatusVO.SeatBrief();
            b.setSeatNo(s.getSeatNo());
            b.setStatus(st);
            b.setIsMe(userId != null && userId.equals(s.getOwnerUserId()));
            briefs.add(b);
        }
        vo.setAvailableCount(available);
        vo.setOwnedCount(owned);
        vo.setFrozenCount(frozen);
        // 无 available 时退到任一已知价格（防 sold_out 时前端价格为 null）
        if (price == null && !all.isEmpty() && all.get(0).getPriceUsdt() != null) {
            price = all.get(0).getPriceUsdt();
        }
        vo.setPriceUsdt(price == null ? new BigDecimal("200000.00000000") : price);
        vo.setSeats(briefs);

        if (userId != null) {
            TFounderSeat mine = founderSeatMapper.selectByOwnerUserId(userId);
            if (mine != null) {
                FounderStatusVO.MySeat ms = new FounderStatusVO.MySeat();
                ms.setId(mine.getId());
                ms.setSeatNo(mine.getSeatNo());
                ms.setStatus(mine.getStatus());
                ms.setPriceUsdt(mine.getPriceUsdt());
                ms.setPaidAt(mine.getPaidAt());
                vo.setMySeat(ms);
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FounderPurchaseResultVO buySeat(Long userId, FounderPurchaseDTO dto,
                                           String clientIp, String clientUserAgent) {
        if (userId == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if (dto == null) {
            throw new ServiceException(MessageUtils.message("founder.purchase.idempotent.empty"));
        }
        if (StrUtil.isBlank(dto.getFundPassword())) {
            throw new ServiceException(MessageUtils.message("tard_password.error"));
        }
        if (StrUtil.isBlank(dto.getIdempotentKey())) {
            throw new ServiceException(MessageUtils.message("founder.purchase.idempotent.empty"));
        }

        // 1. 幂等命中
        TFounderPurchaseLog existing = purchaseLogMapper.selectByIdempotentKey(dto.getIdempotentKey());
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ServiceException(MessageUtils.message("founder.purchase.duplicate"));
            }
            TFounderSeat seat = founderSeatMapper.selectOne(new LambdaQueryWrapper<TFounderSeat>()
                    .eq(TFounderSeat::getSeatNo, existing.getSeatNo()).last("LIMIT 1"));
            if (seat == null) {
                throw new ServiceException(MessageUtils.message("founder.purchase.duplicate"));
            }
            log.info("founder buy idempotent hit: userId={} key={} seatNo={}",
                    userId, dto.getIdempotentKey(), seat.getSeatNo());
            return FounderPurchaseResultVO.from(seat, existing.getAmountUsdt(), true);
        }

        // 2. 用户校验
        TAppUser user = appUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if ("2".equals(user.getIsFreeze())) {
            throw new ServiceException(MessageUtils.message("c2c.user.frozen"));
        }

        // 3. 资金密码校验（与 NodePurchase / withdraw 一致：BCrypt 比对 userTardPwd）
        TAppUserDetail detail = appUserService.selectUserDetailByUserId(userId);
        if (detail == null || StrUtil.isBlank(detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("user.password_notbind"));
        }
        if (!SecurityUtils.matchesPassword(dto.getFundPassword(), detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("tard_password.error"));
        }

        // 4. 一人一席校验（UK 兜底，但提前拒绝可给更友好错误）
        TFounderSeat mine = founderSeatMapper.selectByOwnerUserId(userId);
        if (mine != null) {
            throw new ServiceException(MessageUtils.message("founder.purchase.already_owned"));
        }

        // 5. FOR UPDATE 锁第一个 available
        TFounderSeat seat = founderSeatMapper.selectFirstAvailableForUpdate();
        if (seat == null) {
            throw new ServiceException(MessageUtils.message("founder.purchase.sold_out"));
        }
        BigDecimal price = seat.getPriceUsdt() == null
                ? new BigDecimal("200000.00000000") : seat.getPriceUsdt();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("founder.purchase.sold_out"));
        }

        // 6. USDT 现货余额校验
        TAppAsset asset = appAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, USDT_SYMBOL)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        BigDecimal available = asset != null && asset.getAvailableAmount() != null
                ? asset.getAvailableAmount() : BigDecimal.ZERO;
        BigDecimal total = asset != null && asset.getAmout() != null
                ? asset.getAmout() : BigDecimal.ZERO;
        if (asset == null || available.compareTo(price) < 0 || total.compareTo(price) < 0) {
            throw new ServiceException(MessageUtils.message("founder.purchase.usdt.insufficient"));
        }

        // 7. 扣 USDT
        BigDecimal beforeAvailable = available;
        asset.setAmout(total.subtract(price));
        asset.setAvailableAmount(available.subtract(price));
        int updated = appAssetMapper.updateByUserId(asset);
        if (updated == 0) {
            throw new ServiceException(MessageUtils.message("founder.purchase.usdt.insufficient"));
        }

        // 8. UPDATE seat 占席（uk_owner 兜底，并发同用户拿不到第二席）
        Date now = new Date();
        seat.setStatus(TFounderSeat.STATUS_OWNED);
        seat.setOwnerUserId(userId);
        seat.setPaidAt(now);
        try {
            founderSeatMapper.updateById(seat);
        } catch (DuplicateKeyException e) {
            // uk_owner 触发：同一用户并发尝试，已占第一席 → 拒绝
            throw new ServiceException(MessageUtils.message("founder.purchase.already_owned"));
        }

        // 9. 写购买流水
        TFounderPurchaseLog plog = new TFounderPurchaseLog();
        plog.setUserId(userId);
        plog.setSeatNo(seat.getSeatNo());
        plog.setAmountUsdt(price);
        plog.setPaymentCurrency("USDT");
        plog.setFundPasswordVerified(1);
        plog.setIdempotentKey(dto.getIdempotentKey());
        plog.setClientIp(clientIp);
        plog.setClientUserAgent(StrUtil.isBlank(clientUserAgent) ? null
                : (clientUserAgent.length() > 255 ? clientUserAgent.substring(0, 255) : clientUserAgent));
        try {
            purchaseLogMapper.insert(plog);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(MessageUtils.message("founder.purchase.duplicate"));
        }

        // 10. 写资产账变流水（与 NodePurchase 一致钱包账本）
        walletRecordService.generateRecord(userId, price,
                RecordEnum.GOLD_FOUNDER_PURCHASE.getCode(),
                "",
                "FOUNDER-" + seat.getSeatNo(),
                RecordEnum.GOLD_FOUNDER_PURCHASE.getInfo() + " #" + seat.getSeatNo(),
                beforeAvailable,
                beforeAvailable.subtract(price),
                USDT_SYMBOL,
                user.getAdminParentIds());

        log.info("founder buy success: userId={} seatNo={} amount={} ip={}",
                userId, seat.getSeatNo(), price, clientIp);
        return FounderPurchaseResultVO.from(seat, price, false);
    }
}
