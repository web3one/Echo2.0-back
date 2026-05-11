package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * XGT 30 天锁仓释放结算（PRD §13 + §16）。
 *
 * 释放规则：满 30 天一次性释放（不是线性）。每日 UTC 00:25 扫描 status=locked
 * AND release_at &lt;= NOW，对每条到期 plan：
 *   1) UPDATE plan status locked→completed（条件 UPDATE 防并发）
 *   2) UPDATE t_xgt_balance balance_locked-=amount, balance_unlocked+=amount
 *   3) INSERT t_xgt_log change_type=release
 * 不写 t_reward_log（这是已发奖励 30 天后的释放，不算新奖励）。
 *
 * 冻结期：用户 agent_status=frozen → 跳过；解冻后会被下次 release 任务捞起。
 */
public interface IXgtLockReleaseSettleService {

    SettleResult settle(LocalDate bizDate, Long settleLogId);

    /**
     * REQUIRES_NEW 子事务释放单个 plan，返回释放的 XGT 数量。
     * 跳过（已释放/用户冻结/race）返回 null。
     */
    BigDecimal releaseOnePlan(Long planId, LocalDate bizDate, Long settleLogId);
}
