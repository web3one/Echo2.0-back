package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TContractOrder;
import com.ruoyi.bussiness.domain.TCurrencyOrder;
import com.ruoyi.bussiness.domain.TEcosystemCreditBalance;
import com.ruoyi.bussiness.domain.TEcosystemCreditUnlockLog;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TContractOrderMapper;
import com.ruoyi.bussiness.mapper.TCurrencyOrderMapper;
import com.ruoyi.bussiness.mapper.TEcosystemCreditBalanceMapper;
import com.ruoyi.bussiness.mapper.TEcosystemCreditUnlockLogMapper;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.service.IEcosystemCreditUnlockCheckService;
import com.ruoyi.bussiness.service.IGoldWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * ecosystem_credit 解锁推进 cron 实现（PRD §10）。
 *
 * @date 2026-05-11 v3.10 C-2
 */
@Service
@Slf4j
public class EcosystemCreditUnlockCheckServiceImpl implements IEcosystemCreditUnlockCheckService {

    @Resource
    private TEcosystemCreditUnlockLogMapper ecoUnlockLogMapper;
    @Resource
    private TEcosystemCreditBalanceMapper ecoBalanceMapper;
    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;
    @Resource
    private TCurrencyOrderMapper currencyOrderMapper;
    @Resource
    private TContractOrderMapper contractOrderMapper;
    @Resource
    private TRewardLogMapper rewardLogMapper;
    @Resource
    private IGoldWalletService goldWalletService;

    @Resource
    @Lazy
    private IEcosystemCreditUnlockCheckService self;

    @Override
    public SettleResult check() {
        SettleResult r = new SettleResult();
        List<TEcosystemCreditUnlockLog> rows = ecoUnlockLogMapper.selectAllInProgress();
        if (rows == null || rows.isEmpty()) {
            return r;
        }
        r.setTotalCount(rows.size());

        for (TEcosystemCreditUnlockLog row : rows) {
            try {
                boolean shouldFinalize = false;
                if (TEcosystemCreditUnlockLog.TYPE_XGT_LOCK.equals(row.getUnlockType())) {
                    Long planId = row.getXgtLockPlanId();
                    if (planId == null) {
                        r.incSkipped();
                        continue;
                    }
                    TXgtLockPlan plan = xgtLockPlanMapper.selectById(planId);
                    // 30 天到期由 xgt_lock_release_job 把 plan 改 completed；
                    // 这里看到 completed 才推进 unlock_log
                    if (plan != null && TXgtLockPlan.STATUS_COMPLETED.equals(plan.getStatus())) {
                        shouldFinalize = true;
                    }
                } else if (TEcosystemCreditUnlockLog.TYPE_TRADE_VOLUME.equals(row.getUnlockType())) {
                    BigDecimal completed = sumTradeVolume(row.getUserId(), row.getStartedAt());
                    BigDecimal required = row.getTradeVolumeRequired() == null
                            ? BigDecimal.ZERO : row.getTradeVolumeRequired();
                    if (completed.compareTo(required) >= 0) {
                        shouldFinalize = true;
                    } else {
                        // 进度更新（不开新事务，普通 UPDATE）
                        if (completed.compareTo(nz(row.getTradeVolumeCompleted())) > 0) {
                            TEcosystemCreditUnlockLog upd = new TEcosystemCreditUnlockLog();
                            upd.setId(row.getId());
                            upd.setTradeVolumeCompleted(completed);
                            ecoUnlockLogMapper.updateById(upd);
                        }
                        r.incSkipped();
                        continue;
                    }
                }
                if (shouldFinalize) {
                    self.finalizeUnlock(row.getId());
                    r.incSuccess();
                    r.addAmount(nz(row.getAmountCredit()));
                } else {
                    r.incSkipped();
                }
            } catch (Exception e) {
                log.error("[eco_credit_unlock] check row failed id={}", row.getId(), e);
                r.incFailed();
            }
        }
        log.info("[eco_credit_unlock] check done total={} success={} skipped={} failed={} usdt={}",
                r.getTotalCount(), r.getSuccessCount(), r.getSkippedCount(),
                r.getFailedCount(), r.getAmountSettledUsdt());
        return r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finalizeUnlock(Long unlockLogId) {
        TEcosystemCreditUnlockLog row = ecoUnlockLogMapper.selectById(unlockLogId);
        if (row == null || !TEcosystemCreditUnlockLog.STATUS_IN_PROGRESS.equals(row.getStatus())) {
            return;
        }

        Long userId = row.getUserId();
        BigDecimal amount = nz(row.getAmountCredit());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        // 1) 扣 credit balance_locked
        TEcosystemCreditBalance bal = ecoBalanceMapper.selectByUserId(userId);
        if (bal == null || nz(bal.getBalanceLocked()).compareTo(amount) < 0) {
            log.warn("[eco_credit_unlock] finalize but balance insufficient unlockLogId={} userId={}",
                    unlockLogId, userId);
            // 业务上不应发生（submit 时校验过），保守 cancel 防止幂等死锁
            TEcosystemCreditUnlockLog upd = new TEcosystemCreditUnlockLog();
            upd.setId(unlockLogId);
            upd.setStatus(TEcosystemCreditUnlockLog.STATUS_CANCELLED);
            upd.setCancelReason("balance insufficient at finalize");
            ecoUnlockLogMapper.updateById(upd);
            return;
        }
        bal.setBalanceLocked(nz(bal.getBalanceLocked()).subtract(amount));
        ecoBalanceMapper.updateById(bal);

        // 2) 写 t_reward_log type=eco_credit_unlock
        LocalDate bizDate = LocalDate.now(ZoneOffset.UTC);
        String idempotentKey = "eco_unlock:" + unlockLogId;
        TRewardLog reward = new TRewardLog();
        reward.setUserId(userId);
        reward.setRewardType(TRewardLog.TYPE_ECO_CREDIT_UNLOCK);
        reward.setGrossAmountUsdt(amount);
        reward.setUsdtCredited(amount);
        reward.setEcoCreditAmount(BigDecimal.ZERO);
        reward.setExitTruncatedAmount(BigDecimal.ZERO);
        reward.setCountedInHealthExit(0);
        reward.setBizDate(bizDate);
        reward.setIdempotentKey(idempotentKey);
        reward.setStatus(TRewardLog.STATUS_SETTLED);
        reward.setRemark("eco_credit unlock_log_id=" + unlockLogId
                + " type=" + row.getUnlockType());
        rewardLogMapper.insert(reward);
        Long rewardLogId = reward.getId();

        // 3) 入金矿子钱包
        goldWalletService.addBalance(
                userId,
                amount,
                TGoldWalletLog.CHANGE_REWARD_ECO_CREDIT_UNLOCK,
                TGoldWalletLog.BIZ_REF_REWARD_LOG,
                String.valueOf(rewardLogId),
                "eco_unlock:" + rewardLogId,
                "ECO_CREDIT_UNLOCK #" + unlockLogId);

        // 4) UPDATE unlock_log status=completed
        TEcosystemCreditUnlockLog upd = new TEcosystemCreditUnlockLog();
        upd.setId(unlockLogId);
        upd.setStatus(TEcosystemCreditUnlockLog.STATUS_COMPLETED);
        upd.setCompletedAt(new Date());
        upd.setUsdtCredited(amount);
        upd.setRelatedRewardLogId(rewardLogId);
        ecoUnlockLogMapper.updateById(upd);
    }

    /** 按 user_id + 区间累计 currency_order.deal_value(coin='usdt' AND status=1) + contract_order.deal_value(status=1) */
    private BigDecimal sumTradeVolume(Long userId, Date startedAt) {
        BigDecimal sum = BigDecimal.ZERO;
        try {
            List<TCurrencyOrder> orders = currencyOrderMapper.selectList(
                    new LambdaQueryWrapper<TCurrencyOrder>()
                            .eq(TCurrencyOrder::getUserId, userId)
                            .eq(TCurrencyOrder::getCoin, "usdt")
                            .eq(TCurrencyOrder::getStatus, 1)
                            .ge(TCurrencyOrder::getDealTime, startedAt)
                            .select(TCurrencyOrder::getId, TCurrencyOrder::getDealValue));
            for (TCurrencyOrder o : orders) {
                if (o.getDealValue() != null) sum = sum.add(o.getDealValue());
            }
        } catch (Exception e) {
            log.warn("[eco_credit_unlock] sumTradeVolume currency failed userId={}", userId, e);
        }
        try {
            List<TContractOrder> orders = contractOrderMapper.selectList(
                    new LambdaQueryWrapper<TContractOrder>()
                            .eq(TContractOrder::getUserId, userId)
                            .eq(TContractOrder::getStatus, 1)
                            .ge(TContractOrder::getDealTime, startedAt)
                            .select(TContractOrder::getId, TContractOrder::getDealValue));
            for (TContractOrder o : orders) {
                if (o.getDealValue() != null) sum = sum.add(o.getDealValue());
            }
        } catch (Exception e) {
            log.warn("[eco_credit_unlock] sumTradeVolume contract failed userId={}", userId, e);
        }
        return sum;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
