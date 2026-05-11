package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TXgtBalance;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.TXgtLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TXgtBalanceMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.mapper.TXgtLogMapper;
import com.ruoyi.bussiness.service.IXgtLockReleaseSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * XGT 30 天锁仓释放实现（PRD §13 + §16）。
 *
 * @date 2026-05-11 v3.10 C-1
 */
@Service
@Slf4j
public class XgtLockReleaseSettleServiceImpl implements IXgtLockReleaseSettleService {

    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;
    @Resource
    private TXgtBalanceMapper xgtBalanceMapper;
    @Resource
    private TXgtLogMapper xgtLogMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;

    @Resource
    @Lazy
    private IXgtLockReleaseSettleService self;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();
        Date now = new Date();

        List<TXgtLockPlan> plans = xgtLockPlanMapper.selectReleasable(now);
        if (plans == null || plans.isEmpty()) {
            log.info("[xgt_lock_release] no plans due at {}", now);
            return r;
        }
        r.setTotalCount(plans.size());

        for (TXgtLockPlan plan : plans) {
            try {
                BigDecimal released = self.releaseOnePlan(plan.getId(), bizDate, settleLogId);
                if (released == null) {
                    r.incSkipped();
                } else {
                    r.incSuccess();
                    r.addAmount(released);
                }
            } catch (DuplicateKeyException e) {
                log.info("[xgt_lock_release] idempotent hit planId={}", plan.getId());
                r.incSkipped();
            } catch (Exception e) {
                log.error("[xgt_lock_release] release plan failed planId={}", plan.getId(), e);
                r.incFailed();
            }
        }

        log.info("[xgt_lock_release] done bizDate={} total={} success={} skipped={} failed={} xgt_released={}",
                bizDate, r.getTotalCount(), r.getSuccessCount(), r.getSkippedCount(),
                r.getFailedCount(), r.getAmountSettledUsdt());
        return r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BigDecimal releaseOnePlan(Long planId, LocalDate bizDate, Long settleLogId) {
        TXgtLockPlan plan = xgtLockPlanMapper.selectById(planId);
        if (plan == null) {
            return null;
        }
        if (!TXgtLockPlan.STATUS_LOCKED.equals(plan.getStatus())) {
            // releasable / completed / frozen 一律跳过
            return null;
        }

        Long userId = plan.getUserId();
        BigDecimal amount = nz(plan.getAmountXgt());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // 防御：金额为 0 也直接置 completed
            xgtLockPlanMapper.markCompleted(planId, new Date());
            return null;
        }

        // 冻结用户跳过，解冻后下次 release 任务捞起
        TAgentStatus agent = agentStatusMapper.selectByUserId(userId);
        if (agent != null && TAgentStatus.STATUS_FROZEN.equals(agent.getStatus())) {
            log.info("[xgt_lock_release] user frozen, skip planId={} userId={}", planId, userId);
            return null;
        }

        Date now = new Date();
        int upd = xgtLockPlanMapper.markCompleted(planId, now);
        if (upd == 0) {
            log.info("[xgt_lock_release] race lost (already completed) planId={}", planId);
            return null;
        }

        // 转移余额：locked → unlocked
        TXgtBalance bal = xgtBalanceMapper.selectByUserId(userId);
        BigDecimal newLocked;
        BigDecimal newUnlocked;
        if (bal == null) {
            // 异常：plan 存在但 balance 缺失，按防御性初始化
            bal = new TXgtBalance();
            bal.setUserId(userId);
            bal.setBalanceLocked(BigDecimal.ZERO);
            bal.setBalanceUnlocked(amount);
            try {
                xgtBalanceMapper.insert(bal);
                newLocked = BigDecimal.ZERO;
                newUnlocked = amount;
            } catch (DuplicateKeyException e) {
                bal = xgtBalanceMapper.selectByUserId(userId);
                BigDecimal curLocked = nz(bal.getBalanceLocked());
                BigDecimal curUnlocked = nz(bal.getBalanceUnlocked());
                newLocked = curLocked.subtract(amount).max(BigDecimal.ZERO);
                newUnlocked = curUnlocked.add(amount);
                bal.setBalanceLocked(newLocked);
                bal.setBalanceUnlocked(newUnlocked);
                xgtBalanceMapper.updateById(bal);
            }
        } else {
            BigDecimal curLocked = nz(bal.getBalanceLocked());
            BigDecimal curUnlocked = nz(bal.getBalanceUnlocked());
            newLocked = curLocked.subtract(amount).max(BigDecimal.ZERO);
            newUnlocked = curUnlocked.add(amount);
            bal.setBalanceLocked(newLocked);
            bal.setBalanceUnlocked(newUnlocked);
            xgtBalanceMapper.updateById(bal);
        }

        // 流水
        TXgtLog row = new TXgtLog();
        row.setUserId(userId);
        row.setChangeType(TXgtLog.CHANGE_RELEASE);
        row.setAmountXgt(amount);
        row.setBalanceLockedAfter(newLocked);
        row.setBalanceUnlockedAfter(newUnlocked);
        row.setRelatedLockPlanId(planId);
        row.setRemark("XGT release: " + plan.getSourceType());
        xgtLogMapper.insert(row);

        return amount;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
