package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.time.LocalDate;

/**
 * 每日手续费聚合 Service（B 路线第一组）。
 *
 * PRD §11.2：daily_cex_fee_base = spot_fee + contract_fee
 * 金矿提现费独立统计（不进分红基数；按 1%/4% 拆分对账用）
 *
 * @date 2026-05-15
 */
public interface IDailyFeeSummaryService {

    /** 聚合指定业务日（UTC）的三类手续费。结果 UPSERT 到 t_daily_fee_summary。 */
    SettleResult settle(LocalDate bizDate, Long settleLogId);
}
