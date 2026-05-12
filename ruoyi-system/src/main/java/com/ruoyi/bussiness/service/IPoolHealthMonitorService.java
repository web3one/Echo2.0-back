package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.time.LocalDate;

/**
 * 资金池健康监控（PRD §21.1）。
 *
 * 每日 UTC 00:30 跑（在 daily_static / agency_team / agency_global / founder_dividend /
 * xgt_release / daily_fee_summary 6 个 cron 之后），核对：
 *
 *   分红基数（spot + contract fee）vs 当日各 cron 实际发奖总额（USDT 名义）
 *
 * 阈值：
 *   ratio = totalDividendsSettled / dividendBaseUsdt
 *   ratio > 1.0  → ERROR（手续费不足覆盖分红，资金池入不敷出）
 *   ratio > 0.8  → WARN（手续费即将不足）
 *   ratio &lt;= 0.8 → OK
 *
 * 结果回写 t_settle_log（jobName=pool_health_monitor_job）：
 *   ERROR → status=failed + errorMessage 详细分析
 *   WARN/OK → status=success + amountSettledUsdt 填差额
 *
 * 后续可接入：Slack/email/钉钉告警（在 ServiceImpl 留 hook）。
 *
 * @date 2026-05-12
 */
public interface IPoolHealthMonitorService {

    SettleResult settle(LocalDate bizDate, Long settleLogId);
}
