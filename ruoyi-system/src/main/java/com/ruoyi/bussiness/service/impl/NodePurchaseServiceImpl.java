package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TBinaryTree;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.TNodeLevel;
import com.ruoyi.bussiness.domain.TNodePurchaseLog;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.dto.NodeBuyDTO;
import com.ruoyi.bussiness.domain.vo.NodeBuyResultVO;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TBinaryTreeMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.mapper.TNodeLevelMapper;
import com.ruoyi.bussiness.mapper.TNodePurchaseLogMapper;
import com.ruoyi.bussiness.service.IBinaryTreeService;
import com.ruoyi.bussiness.service.INodePurchaseService;
import com.ruoyi.bussiness.service.ITAppAssetService;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 矿机购买服务实现（金矿 Phase 1 P0 入口）。
 *
 * @date 2026-05-10
 */
@Service
@Slf4j
public class NodePurchaseServiceImpl implements INodePurchaseService {

    private static final String USDT_SYMBOL = "usdt";
    private static final BigDecimal REFERRAL_RATE = new BigDecimal("0.10");

    @Resource
    private TNodeLevelMapper nodeLevelMapper;

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Resource
    private TNodePurchaseLogMapper nodePurchaseLogMapper;

    @Resource
    private TAppAssetMapper appAssetMapper;

    @Resource
    private ITAppAssetService appAssetService;

    @Resource
    private ITAppUserService appUserService;

    @Resource
    private ITAppWalletRecordService walletRecordService;

    @Resource
    private IBinaryTreeService binaryTreeService;

    @Resource
    private TBinaryTreeMapper binaryTreeMapper;

    @Resource
    private DynamicRewardHelper rewardHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeBuyResultVO buyNode(Long userId, NodeBuyDTO dto) {
        if (userId == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if (dto == null) {
            throw new ServiceException(MessageUtils.message("node.buy.level.invalid"));
        }
        if (StrUtil.isBlank(dto.getLevelCode())) {
            throw new ServiceException(MessageUtils.message("node.buy.level.invalid"));
        }
        if (StrUtil.isBlank(dto.getFundPassword())) {
            throw new ServiceException(MessageUtils.message("tard_password.error"));
        }
        if (StrUtil.isBlank(dto.getIdempotentKey())) {
            throw new ServiceException(MessageUtils.message("node.buy.idempotent.empty"));
        }

        // 1. 幂等命中（同一 idempotent_key 二次提交直接返回首次结果）
        TNodePurchaseLog existing = nodePurchaseLogMapper.selectOne(
                new LambdaQueryWrapper<TNodePurchaseLog>()
                        .eq(TNodePurchaseLog::getIdempotentKey, dto.getIdempotentKey())
                        .last("LIMIT 1"));
        if (existing != null) {
            // 必须是同一用户，不能让 A 用 B 的 key 探到结果
            if (!userId.equals(existing.getUserId())) {
                throw new ServiceException(MessageUtils.message("node.buy.duplicate"));
            }
            TNodeInstance inst = nodeInstanceMapper.selectById(existing.getNodeInstanceId());
            if (inst == null) {
                throw new ServiceException(MessageUtils.message("node.buy.duplicate"));
            }
            log.info("buyNode idempotent hit: userId={} key={} instanceId={}",
                    userId, dto.getIdempotentKey(), inst.getId());
            return NodeBuyResultVO.from(inst, true);
        }

        // 2. 矿机等级有效性
        TNodeLevel level = nodeLevelMapper.selectByLevelCode(dto.getLevelCode());
        if (level == null || level.getEnabled() == null || level.getEnabled() != 1) {
            throw new ServiceException(MessageUtils.message("node.buy.level.invalid"));
        }
        BigDecimal price = level.getPriceUsdt();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("node.buy.level.invalid"));
        }

        // 3. 用户存在 + 未冻结
        TAppUser user = appUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if ("2".equals(user.getIsFreeze())) {
            throw new ServiceException(MessageUtils.message("c2c.user.frozen"));
        }

        // 4. 资金密码校验（与 c2c confirmRelease / withdraw 一致：BCrypt 比对 userTardPwd）
        TAppUserDetail detail = appUserService.selectUserDetailByUserId(userId);
        if (detail == null || StrUtil.isBlank(detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("user.password_notbind"));
        }
        if (!SecurityUtils.matchesPassword(dto.getFundPassword(), detail.getUserTardPwd())) {
            throw new ServiceException(MessageUtils.message("tard_password.error"));
        }

        // 4.5 同 level 矿机同时唯一（用户决策 2026-05-09：同 level 已持 active/frozen → 拒绝；
        // expired/cancelled 不阻塞，等同于"出局后可重买开启新周期"）
        Integer sameLevelCount = nodeInstanceMapper.selectCount(new LambdaQueryWrapper<TNodeInstance>()
                .eq(TNodeInstance::getUserId, userId)
                .eq(TNodeInstance::getLevelCode, level.getLevelCode())
                .in(TNodeInstance::getStatus,
                        TNodeInstance.STATUS_ACTIVE, TNodeInstance.STATUS_FROZEN));
        if (sameLevelCount != null && sameLevelCount > 0) {
            throw new ServiceException(MessageUtils.message("node.buy.level.duplicate"));
        }

        // 5. USDT 现货余额校验（type=PLATFORM_ASSETS / symbol=usdt 小写）
        TAppAsset asset = appAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, USDT_SYMBOL)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        BigDecimal available = (asset != null && asset.getAvailableAmount() != null)
                ? asset.getAvailableAmount() : BigDecimal.ZERO;
        BigDecimal total = (asset != null && asset.getAmout() != null)
                ? asset.getAmout() : BigDecimal.ZERO;
        if (asset == null || available.compareTo(price) < 0 || total.compareTo(price) < 0) {
            throw new ServiceException(MessageUtils.message("node.buy.usdt.insufficient"));
        }

        // 6. 扣 USDT（amout / availableAmount 同时减）
        BigDecimal beforeAvailable = available;
        asset.setAmout(total.subtract(price));
        asset.setAvailableAmount(available.subtract(price));
        int updated = appAssetMapper.updateByUserId(asset);
        if (updated == 0) {
            // 并发竞争失败（极少数）
            throw new ServiceException(MessageUtils.message("node.buy.usdt.insufficient"));
        }

        // 7. 创建矿机实例（购买时快照所有计费参数，避免后续 admin 改 t_node_level 影响存量）
        BigDecimal exitMultiplier = level.getExitMultiplier() != null
                ? level.getExitMultiplier() : new BigDecimal("3.0000");
        BigDecimal exitTarget = price.multiply(exitMultiplier).setScale(8, RoundingMode.HALF_UP);
        Date now = new Date();

        TNodeInstance inst = new TNodeInstance();
        inst.setUserId(userId);
        inst.setLevelId(level.getId());
        inst.setLevelCode(level.getLevelCode());
        inst.setPriceUsdt(price);
        inst.setDailyYieldRate(level.getDailyYieldRate());
        inst.setTeamDailyCapUsdt(level.getTeamDailyCapUsdt());
        inst.setExitTargetUsdt(exitTarget);
        inst.setAccumulatedRewardUsdt(BigDecimal.ZERO);
        inst.setAccumulatedStaticUsdt(BigDecimal.ZERO);
        inst.setAccumulatedReferralUsdt(BigDecimal.ZERO);
        inst.setAccumulatedTeamUsdt(BigDecimal.ZERO);
        inst.setStatus(TNodeInstance.STATUS_ACTIVE);
        inst.setActivatedAt(now);
        nodeInstanceMapper.insert(inst);

        // 8. 写购买流水（含 idempotent_key 防重）
        TNodePurchaseLog plog = new TNodePurchaseLog();
        plog.setUserId(userId);
        plog.setNodeInstanceId(inst.getId());
        plog.setLevelCode(level.getLevelCode());
        plog.setPriceUsdt(price);
        plog.setPaymentCurrency(USDT_SYMBOL);
        plog.setPlacementSide("auto"); // 购买不再二次选择 placement，注册时已定
        plog.setIdempotentKey(dto.getIdempotentKey());
        try {
            nodePurchaseLogMapper.insert(plog);
        } catch (DuplicateKeyException e) {
            // 极小概率：两个并发请求同 key 同时进 service 的情况下，本次插入会撞 UK
            // 触发整体事务回滚（USDT 已扣会回滚），客户端可重试
            throw new ServiceException(MessageUtils.message("node.buy.duplicate"));
        }

        // 9. 写资产账变流水（与 C2C / withdraw 一致的钱包账本）
        walletRecordService.generateRecord(userId, price,
                RecordEnum.GOLD_NODE_PURCHASE.getCode(),
                "",
                "NODE-" + inst.getId(),
                RecordEnum.GOLD_NODE_PURCHASE.getInfo() + " " + level.getLevelCode(),
                beforeAvailable,
                beforeAvailable.subtract(price),
                USDT_SYMBOL,
                user.getAdminParentIds());

        // 10. 沿双轨树向上累计业绩（含 buyer 那一区，daily + total 各加一次）
        try {
            binaryTreeService.accumulateVolume(userId, price);
        } catch (Exception e) {
            // 双轨累计失败不阻塞购买；标记日志，由 admin 后续修复
            // （但因为整体事务原因，此处异常仍会回滚整体）
            log.error("accumulateVolume failed: userId={} amount={} err={}",
                    userId, price, e.getMessage(), e);
            throw e;
        }

        // 11. 直推奖（PRD §6 实时触发，与购买同事务）
        // 公式 = buyer 购买金额 × 10%（PRD §6.4）
        // 70/30 入账（70% USDT 现货 + 30% ecosystem_credit balance_locked）
        // 上级无 active 矿机 → 不发（PRD §6.3）；上级冻结 → 不发（追问 A）
        try {
            awardReferralReward(userId, level.getLevelCode(), price, inst.getId());
        } catch (Exception e) {
            log.error("awardReferralReward failed: buyerUserId={} amount={} err={}",
                    userId, price, e.getMessage(), e);
            throw e;
        }

        log.info("buyNode success: userId={} levelCode={} price={} instanceId={}",
                userId, level.getLevelCode(), price, inst.getId());
        return NodeBuyResultVO.from(inst, false);
    }

    /**
     * 沿"邀请关系树"找 buyer 的直推上级，给上级发直推奖。
     *
     * 直推上级 = t_binary_tree.sponsor_id（邀请码持有者，可能不是双轨直接父级）
     * 幂等：t_reward_log idempotent_key = "REF:NODE-{instanceId}"，
     *       与 nodes/buy idempotent_key 联动，购买重复提交直推奖也不会重发
     */
    private void awardReferralReward(Long buyerUserId, String buyerLevelCode,
                                     BigDecimal buyerAmount, Long buyerInstanceId) {
        TBinaryTree buyerNode = binaryTreeMapper.selectByUserId(buyerUserId);
        if (buyerNode == null || buyerNode.getSponsorId() == null) {
            log.info("[referral] buyer has no sponsor, skip: buyerUserId={}", buyerUserId);
            return;
        }
        Long sponsorId = buyerNode.getSponsorId();

        BigDecimal gross = buyerAmount.multiply(REFERRAL_RATE).setScale(8, RoundingMode.HALF_UP);
        if (gross.compareTo(BigDecimal.ZERO) <= 0) return;

        String idempotentKey = "REF:NODE-" + buyerInstanceId;
        LocalDate bizDate = LocalDate.now(ZoneOffset.UTC);
        String walletInfoSuffix = "from " + buyerLevelCode;

        BigDecimal credited = rewardHelper.awardDynamicReward(
                sponsorId,
                TRewardLog.TYPE_REFERRAL,
                gross,
                buyerUserId,
                buyerAmount,
                REFERRAL_RATE,
                null,
                idempotentKey,
                null,
                bizDate,
                walletInfoSuffix);
        if (credited == null) {
            log.info("[referral] sponsor not eligible, skip: sponsorId={} buyerUserId={}",
                    sponsorId, buyerUserId);
        } else {
            log.info("[referral] credited: sponsorId={} buyerUserId={} gross={} credited={}",
                    sponsorId, buyerUserId, gross, credited);
        }
    }
}
