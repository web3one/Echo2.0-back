package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

/**
 * ecosystem_credit 解锁请求推进 cron（PRD §10）。
 *
 * 小时级扫所有 in_progress 行：
 *   - trade_volume：累计 t_currency_order(usdt) + t_contract_order 成交价值 ≥ required → 完成
 *   - xgt_lock：xgt_lock_plan.status='completed'（30 天到期）→ 完成
 *
 * 完成时：扣 balance_locked / 写 t_reward_log / 入 t_gold_wallet / UPDATE log status=completed
 */
public interface IEcosystemCreditUnlockCheckService {

    SettleResult check();

    /** REQUIRES_NEW 子事务完成单个 unlock_log */
    void finalizeUnlock(Long unlockLogId);
}
