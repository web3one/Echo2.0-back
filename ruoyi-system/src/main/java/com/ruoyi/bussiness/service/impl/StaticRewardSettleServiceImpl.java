package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.TXgtBalance;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.TXgtLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.mapper.TXgtBalanceMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.mapper.TXgtLogMapper;
import com.ruoyi.bussiness.service.IGoldWalletService;
import com.ruoyi.bussiness.service.IStaticRewardSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 静态分红结算实现（用户聚合制健康出局，2026-05-09 修订）。
 *
 * 设计要点：
 * - 外层 settle() 不开事务，按 distinct userId 调 self.settleOneUser（REQUIRES_NEW 子事务）
 * - 一个用户失败/UK 冲突只回滚该用户子事务，不影响其他用户
 * - DuplicateKeyException 视为幂等命中，整个用户计入 skipped
 *
 * 出局额 = 用户 active 矿机中 MAX(priceUsdt) × 3。
 * 累计 = 用户 active 矿机 accumulated_reward_usdt 之和。
 * 用户达聚合目标 → 全部 active 矿机一起 expired。
 */
@Service
@Slf4j
public class StaticRewardSettleServiceImpl implements IStaticRewardSettleService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal EXIT_MULTIPLIER = new BigDecimal("3");
    private static final long LOCK_DAYS = 30L;
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;
    @Resource
    private TRewardLogMapper rewardLogMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;
    @Resource
    private TXgtBalanceMapper xgtBalanceMapper;
    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;
    @Resource
    private TXgtLogMapper xgtLogMapper;
    @Resource
    private IGoldWalletService goldWalletService;

    /** self proxy 触发 @Transactional REQUIRES_NEW（同类自调用不走代理）。 */
    @Resource
    @Lazy
    private IStaticRewardSettleService self;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();
        // 收集所有有 active 矿机的 distinct userId
        List<TNodeInstance> all = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<TNodeInstance>()
                        .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE)
                        .select(TNodeInstance::getUserId));
        Set<Long> userIds = new HashSet<>();
        for (TNodeInstance i : all) {
            if (i.getUserId() != null) userIds.add(i.getUserId());
        }
        r.setTotalCount(userIds.size());
        if (userIds.isEmpty()) {
            log.info("[static_reward] no active user, bizDate={}", bizDate);
            return r;
        }

        for (Long userId : userIds) {
            try {
                BigDecimal credited = self.settleOneUser(userId, bizDate, settleLogId);
                if (credited == null) {
                    r.incSkipped();
                } else {
                    r.incSuccess();
                    r.addAmount(credited);
                }
            } catch (DuplicateKeyException e) {
                log.info("[static_reward] idempotent hit, skip: userId={}", userId);
                r.incSkipped();
            } catch (Exception e) {
                log.error("[static_reward] settle one user failed: userId={}", userId, e);
                r.incFailed();
            }
        }
        return r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BigDecimal settleOneUser(Long userId, LocalDate bizDate, Long settleLogId) {
        // 1. 冻结校验（追问 A：冻结代理停发奖，下线照算业绩）
        TAgentStatus agent = agentStatusMapper.selectByUserId(userId);
        if (agent != null && TAgentStatus.STATUS_FROZEN.equals(agent.getStatus())) {
            return null;
        }

        // 2. 加锁查该用户所有 active 矿机
        List<TNodeInstance> miners = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<TNodeInstance>()
                        .eq(TNodeInstance::getUserId, userId)
                        .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE)
                        .last("FOR UPDATE"));
        if (miners == null || miners.isEmpty()) {
            return null;
        }

        // 3. 算用户聚合 + 出局额（按 active 矿机最高级 priceUsdt × 3）
        BigDecimal sumAccumulated = BigDecimal.ZERO;
        BigDecimal maxPrice = BigDecimal.ZERO;
        for (TNodeInstance m : miners) {
            BigDecimal acc = nz(m.getAccumulatedRewardUsdt());
            sumAccumulated = sumAccumulated.add(acc);
            BigDecimal p = nz(m.getPriceUsdt());
            if (p.compareTo(maxPrice) > 0) maxPrice = p;
        }
        BigDecimal aggExitTarget = maxPrice.multiply(EXIT_MULTIPLIER).setScale(8, RoundingMode.HALF_UP);
        BigDecimal aggRemaining = aggExitTarget.subtract(sumAccumulated);

        // 4. 已达聚合出局额（数据异常或并发导致）→ 全部 active 一起 expired，跳过
        if (aggRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            markUserActiveExpired(userId);
            return null;
        }

        // 5. 排序：levelCode desc → activatedAt asc（高级 + 早激活的先消耗 quota）
        miners.sort(minerSettleOrder());

        // 6. 循环发奖
        BigDecimal totalCreditedUsdt = BigDecimal.ZERO;
        for (TNodeInstance m : miners) {
            BigDecimal price = nz(m.getPriceUsdt());
            BigDecimal rate = nz(m.getDailyYieldRate());
            if (price.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal gross = price.multiply(rate).setScale(8, RoundingMode.HALF_UP);
            if (gross.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 健康出局截断（用户聚合视角，循环内动态消耗 quota）
            BigDecimal credited = aggRemaining.compareTo(BigDecimal.ZERO) > 0
                    ? (gross.compareTo(aggRemaining) <= 0 ? gross : aggRemaining)
                    : BigDecimal.ZERO;
            BigDecimal truncated = gross.subtract(credited);

            // 50/50 分账（精度防丢失：xgtPart = credited - usdtPart）
            BigDecimal usdtPart = credited.multiply(HALF).setScale(8, RoundingMode.HALF_UP);
            BigDecimal xgtPart = credited.subtract(usdtPart);

            // INSERT t_reward_log（按 nodeInstanceId 维度幂等：UK idempotent_key 冲突 → 抛 DuplicateKey）
            String idemKey = bizDate + ":static:" + userId + ":" + m.getId();
            TRewardLog reward = new TRewardLog();
            reward.setUserId(userId);
            reward.setRewardType(TRewardLog.TYPE_STATIC);
            reward.setGrossAmountUsdt(gross);
            reward.setUsdtCredited(usdtPart);
            reward.setExitTruncatedAmount(truncated);
            reward.setXgtCredited(xgtPart);
            reward.setEcoCreditAmount(BigDecimal.ZERO);
            reward.setRelatedNodeInstanceId(m.getId());
            reward.setCountedInHealthExit(1);
            reward.setSettleLogId(settleLogId);
            reward.setBizDate(bizDate);
            reward.setIdempotentKey(idemKey);
            reward.setStatus(TRewardLog.STATUS_SETTLED);
            rewardLogMapper.insert(reward);
            Long rewardLogId = reward.getId();

            // 矿机累计 += credited（PRD §5.5：累计 += actual_static，不是 += gross；
            // truncated 永久消失既不入用户账户也不计入累计）
            BigDecimal newAccumulated = nz(m.getAccumulatedRewardUsdt()).add(credited);
            BigDecimal newStatic = nz(m.getAccumulatedStaticUsdt()).add(credited);
            TNodeInstance upd = new TNodeInstance();
            upd.setId(m.getId());
            upd.setAccumulatedRewardUsdt(newAccumulated);
            upd.setAccumulatedStaticUsdt(newStatic);
            nodeInstanceMapper.updateById(upd);

            // USDT 入账金矿子钱包（B 路线第一组改造：从现货 t_app_asset 切到 t_gold_wallet）
            if (usdtPart.compareTo(BigDecimal.ZERO) > 0) {
                goldWalletService.addBalance(
                        userId,
                        usdtPart,
                        TGoldWalletLog.CHANGE_REWARD_STATIC,
                        TGoldWalletLog.BIZ_REF_REWARD_LOG,
                        String.valueOf(rewardLogId),
                        "static:" + rewardLogId,
                        "STATIC " + m.getLevelCode());
            }
            // XGT 锁仓 30 天
            if (xgtPart.compareTo(BigDecimal.ZERO) > 0) {
                lockXgt(userId, rewardLogId, xgtPart);
            }

            aggRemaining = aggRemaining.subtract(credited);
            totalCreditedUsdt = totalCreditedUsdt.add(credited);
        }

        // 7. 用户聚合 quota 耗尽 → 全部 active 一起 expired（用户决策：所有矿机一起停）
        if (aggRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            markUserActiveExpired(userId);
        }

        return totalCreditedUsdt;
    }

    /** 该用户当前所有 status=active 矿机一起 → expired + expired_at=NOW。 */
    private void markUserActiveExpired(Long userId) {
        TNodeInstance upd = new TNodeInstance();
        upd.setStatus(TNodeInstance.STATUS_EXPIRED);
        upd.setExpiredAt(new Date());
        nodeInstanceMapper.update(upd, new LambdaUpdateWrapper<TNodeInstance>()
                .eq(TNodeInstance::getUserId, userId)
                .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE));
    }

    /**
     * 矿机发奖顺序：levelCode desc（L4 先）→ activatedAt asc（早激活先）
     */
    private static Comparator<TNodeInstance> minerSettleOrder() {
        return (a, b) -> {
            int la = levelRank(a.getLevelCode());
            int lb = levelRank(b.getLevelCode());
            if (la != lb) return Integer.compare(lb, la);
            Date pa = a.getActivatedAt();
            Date pb = b.getActivatedAt();
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1;
            if (pb == null) return -1;
            return pa.compareTo(pb);
        };
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void lockXgt(Long userId, Long rewardLogId, BigDecimal xgtAmount) {
        Date now = new Date();
        Date releaseAt = new Date(now.getTime() + LOCK_DAYS * ONE_DAY_MILLIS);

        TXgtLockPlan plan = new TXgtLockPlan();
        plan.setUserId(userId);
        plan.setSourceType(TXgtLockPlan.SOURCE_STATIC_REWARD);
        plan.setSourceRefId(String.valueOf(rewardLogId));
        plan.setAmountXgt(xgtAmount);
        plan.setAmountUsdNominal(xgtAmount);
        plan.setLockedAt(now);
        plan.setReleaseAt(releaseAt);
        plan.setStatus(TXgtLockPlan.STATUS_LOCKED);
        xgtLockPlanMapper.insert(plan);
        Long planId = plan.getId();

        TXgtBalance bal = xgtBalanceMapper.selectByUserId(userId);
        BigDecimal newLocked;
        BigDecimal currentUnlocked;
        if (bal == null) {
            bal = new TXgtBalance();
            bal.setUserId(userId);
            bal.setBalanceLocked(xgtAmount);
            bal.setBalanceUnlocked(BigDecimal.ZERO);
            try {
                xgtBalanceMapper.insert(bal);
            } catch (DuplicateKeyException e) {
                bal = xgtBalanceMapper.selectByUserId(userId);
                newLocked = nz(bal.getBalanceLocked()).add(xgtAmount);
                bal.setBalanceLocked(newLocked);
                xgtBalanceMapper.updateById(bal);
            }
            newLocked = bal.getBalanceLocked();
            currentUnlocked = nz(bal.getBalanceUnlocked());
        } else {
            newLocked = nz(bal.getBalanceLocked()).add(xgtAmount);
            currentUnlocked = nz(bal.getBalanceUnlocked());
            bal.setBalanceLocked(newLocked);
            xgtBalanceMapper.updateById(bal);
        }

        TXgtLog xlog = new TXgtLog();
        xlog.setUserId(userId);
        xlog.setChangeType(TXgtLog.CHANGE_LOCK);
        xlog.setAmountXgt(xgtAmount);
        xlog.setBalanceLockedAfter(newLocked);
        xlog.setBalanceUnlockedAfter(currentUnlocked);
        xlog.setRelatedLockPlanId(planId);
        xlog.setRelatedRewardLogId(rewardLogId);
        xgtLogMapper.insert(xlog);
    }
}
