package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TEcosystemCreditBalance;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TEcosystemCreditBalanceMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.service.IGoldWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 动态奖发放公共逻辑（直推奖 + 团队代理奖共用）。
 *
 * 设计要点：
 * - 不开新事务，调用方需保证已在事务内
 * - 70/30 入账（70% USDT 现货 + 30% ecosystem_credit locked）
 * - 用户聚合健康出局截断：sumActiveAccumulated vs maxActivePrice × 3
 * - 累计 += credited（PRD §6.6/§9.5：截断部分永久消失，不入累计）
 * - 用户聚合达标 → 该用户所有 active 矿机一起 expired（用户决策 2026-05-09）
 *
 * 核心入口：{@link #awardDynamicReward}
 *
 * @date 2026-05-09
 */
@Component
@Slf4j
public class DynamicRewardHelper {

    private static final BigDecimal SEVENTY_PCT = new BigDecimal("0.7");
    private static final BigDecimal EXIT_MULTIPLIER = new BigDecimal("3");

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;
    @Resource
    private TRewardLogMapper rewardLogMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;
    @Resource
    private TEcosystemCreditBalanceMapper ecoBalanceMapper;
    @Resource
    private IGoldWalletService goldWalletService;

    /**
     * 通用动态奖发放。
     *
     * 调用方必须已在事务内。
     *
     * @param beneficiaryUserId  受益人 user_id
     * @param rewardType         TRewardLog.TYPE_REFERRAL / TYPE_TEAM
     * @param grossUsdt          毛额（USDT 等值，需要 > 0）
     * @param sourceUserId       直推奖：buyer userId；团队奖可填 null
     * @param sourceAmount       直推奖：buyer 本次购买金额；团队奖：弱区业绩
     * @param agentMatchRate     团队奖：代理匹配率（直推奖填 null 或 0.10）
     * @param agentLevelSnap     团队奖：代理等级快照（直推奖可填 null）
     * @param idempotentKey      幂等 key（UNIQUE，重发抛 DuplicateKeyException）
     * @param settleLogId        团队奖：结算 t_settle_log.id；直推奖：null
     * @param bizDate            业务日
     * @param walletInfoSuffix   钱包流水描述附加（如 buyer 的购买等级 "L2"）
     * @return credited（USDT 等值名义）；null 表示跳过（冻结/无 active 矿机/聚合配额已耗尽）
     */
    public BigDecimal awardDynamicReward(
            Long beneficiaryUserId,
            String rewardType,
            BigDecimal grossUsdt,
            Long sourceUserId,
            BigDecimal sourceAmount,
            BigDecimal agentMatchRate,
            String agentLevelSnap,
            String idempotentKey,
            Long settleLogId,
            LocalDate bizDate,
            String walletInfoSuffix) {

        if (beneficiaryUserId == null
                || grossUsdt == null
                || grossUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 1. 冻结校验（追问 A：冻结代理 4 类奖励停发）
        TAgentStatus agent = agentStatusMapper.selectByUserId(beneficiaryUserId);
        if (agent != null && TAgentStatus.STATUS_FROZEN.equals(agent.getStatus())) {
            return null;
        }

        // 2. 加锁查所有 active 矿机
        List<TNodeInstance> miners = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<TNodeInstance>()
                        .eq(TNodeInstance::getUserId, beneficiaryUserId)
                        .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE)
                        .last("FOR UPDATE"));
        if (miners == null || miners.isEmpty()) {
            // PRD §2.1 / §6.3：无 active 矿机 → 不发动态奖
            return null;
        }

        // 3. 找当前权益矿机（PRD §22.6：最高级 + 同级最早激活）
        TNodeInstance current = pickHighestActive(miners);
        if (current == null) {
            return null;
        }

        // 4. 用户聚合 quota（按 active 最高级 priceUsdt × 3 - SUM(active.accumulated)）
        BigDecimal sumAcc = BigDecimal.ZERO;
        BigDecimal maxPrice = BigDecimal.ZERO;
        for (TNodeInstance m : miners) {
            sumAcc = sumAcc.add(nz(m.getAccumulatedRewardUsdt()));
            BigDecimal p = nz(m.getPriceUsdt());
            if (p.compareTo(maxPrice) > 0) maxPrice = p;
        }
        BigDecimal aggExitTarget = maxPrice.multiply(EXIT_MULTIPLIER).setScale(8, RoundingMode.HALF_UP);
        BigDecimal aggRemaining = aggExitTarget.subtract(sumAcc);
        if (aggRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            // 聚合已达 → 全部 active 一起 expired，本次不发
            markUserActiveExpired(beneficiaryUserId);
            return null;
        }

        // 5. 健康出局截断
        BigDecimal credited = grossUsdt.compareTo(aggRemaining) <= 0 ? grossUsdt : aggRemaining;
        BigDecimal truncated = grossUsdt.subtract(credited);

        // 6. 70/30 分账（精度防丢失：creditPart = credited - usdtPart）
        BigDecimal usdtPart = credited.multiply(SEVENTY_PCT).setScale(8, RoundingMode.HALF_UP);
        BigDecimal creditPart = credited.subtract(usdtPart);

        // 7. INSERT t_reward_log（UK idempotent_key 冲突 → DuplicateKeyException）
        TRewardLog reward = new TRewardLog();
        reward.setUserId(beneficiaryUserId);
        reward.setRewardType(rewardType);
        reward.setGrossAmountUsdt(grossUsdt);
        reward.setUsdtCredited(usdtPart);
        reward.setEcoCreditAmount(creditPart);
        reward.setExitTruncatedAmount(truncated);
        reward.setRelatedNodeInstanceId(current.getId());
        reward.setSourceUserId(sourceUserId);
        reward.setSourceAmount(sourceAmount);
        reward.setAgentMatchRate(agentMatchRate);
        reward.setAgentLevelSnapshot(agentLevelSnap);
        reward.setCountedInHealthExit(1);
        reward.setSettleLogId(settleLogId);
        reward.setBizDate(bizDate);
        reward.setIdempotentKey(idempotentKey);
        reward.setStatus(TRewardLog.STATUS_SETTLED);
        rewardLogMapper.insert(reward);
        Long rewardLogId = reward.getId();

        // 8. 矿机累计 += credited（PRD §6.6/§9.5：累计 += 实际发放金额）
        BigDecimal newAcc = nz(current.getAccumulatedRewardUsdt()).add(credited);
        TNodeInstance upd = new TNodeInstance();
        upd.setId(current.getId());
        upd.setAccumulatedRewardUsdt(newAcc);
        if (TRewardLog.TYPE_REFERRAL.equals(rewardType)) {
            upd.setAccumulatedReferralUsdt(nz(current.getAccumulatedReferralUsdt()).add(credited));
        } else if (TRewardLog.TYPE_TEAM.equals(rewardType)) {
            upd.setAccumulatedTeamUsdt(nz(current.getAccumulatedTeamUsdt()).add(credited));
        }
        nodeInstanceMapper.updateById(upd);

        // 9. USDT 入账金矿子钱包（B 路线第一组改造：从现货 t_app_asset 切到 t_gold_wallet）
        if (usdtPart.compareTo(BigDecimal.ZERO) > 0) {
            String changeType = TRewardLog.TYPE_REFERRAL.equals(rewardType)
                    ? TGoldWalletLog.CHANGE_REWARD_REFERRAL
                    : TGoldWalletLog.CHANGE_REWARD_TEAM;
            String idemSuffix = TRewardLog.TYPE_REFERRAL.equals(rewardType) ? "ref:" : "team:";
            goldWalletService.addBalance(
                    beneficiaryUserId,
                    usdtPart,
                    changeType,
                    TGoldWalletLog.BIZ_REF_REWARD_LOG,
                    String.valueOf(rewardLogId),
                    idemSuffix + rewardLogId,
                    walletInfoSuffix);
        }

        // 10. credit 入账（balance_locked += creditPart；解锁走 POST /ecosystem-credit/unlock）
        if (creditPart.compareTo(BigDecimal.ZERO) > 0) {
            addEcoCreditLocked(beneficiaryUserId, creditPart);
        }

        // 11. 用户聚合达标 → 全部 active 一起 expired
        BigDecimal afterAcc = sumAcc.add(credited);
        if (afterAcc.compareTo(aggExitTarget) >= 0) {
            markUserActiveExpired(beneficiaryUserId);
        }

        return credited;
    }

    /** 该用户当前所有 status=active 矿机一起 → expired + expired_at=NOW。 */
    public void markUserActiveExpired(Long userId) {
        TNodeInstance upd = new TNodeInstance();
        upd.setStatus(TNodeInstance.STATUS_EXPIRED);
        upd.setExpiredAt(new Date());
        nodeInstanceMapper.update(upd, new LambdaUpdateWrapper<TNodeInstance>()
                .eq(TNodeInstance::getUserId, userId)
                .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE));
    }

    /** PRD §22.6：当前有效权益矿机 = 最高级 active + 同级最早激活。 */
    public TNodeInstance pickHighestActive(List<TNodeInstance> activeMiners) {
        TNodeInstance picked = null;
        int pickedRank = -1;
        for (TNodeInstance m : activeMiners) {
            int r = levelRank(m.getLevelCode());
            if (r < 0) continue;
            if (picked == null || r > pickedRank
                    || (r == pickedRank && earlier(m.getActivatedAt(), picked.getActivatedAt()))) {
                picked = m;
                pickedRank = r;
            }
        }
        return picked;
    }

    private void addEcoCreditLocked(Long userId, BigDecimal amount) {
        TEcosystemCreditBalance bal = ecoBalanceMapper.selectByUserId(userId);
        if (bal == null) {
            bal = new TEcosystemCreditBalance();
            bal.setUserId(userId);
            bal.setBalanceLocked(amount);
            bal.setBalanceUnlocked(BigDecimal.ZERO);
            try {
                ecoBalanceMapper.insert(bal);
            } catch (DuplicateKeyException e) {
                bal = ecoBalanceMapper.selectByUserId(userId);
                bal.setBalanceLocked(nz(bal.getBalanceLocked()).add(amount));
                ecoBalanceMapper.updateById(bal);
            }
        } else {
            bal.setBalanceLocked(nz(bal.getBalanceLocked()).add(amount));
            ecoBalanceMapper.updateById(bal);
        }
    }

    private static int levelRank(String levelCode) {
        if (levelCode == null) return -1;
        switch (levelCode) {
            case "L1": return 1;
            case "L2": return 2;
            case "L3": return 3;
            case "L4": return 4;
            default: return -1;
        }
    }

    private static boolean earlier(Date a, Date b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.before(b);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
