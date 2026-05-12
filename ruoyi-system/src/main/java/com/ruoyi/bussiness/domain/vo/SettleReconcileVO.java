package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 财务对账 VO（PRD §15.6 第 7 条 "支持财务对账"）。
 *
 * 单日维度聚合：
 *   - 分红基数（CEX spot + contract）
 *   - 每个 cron 当日成功/失败/跳过/金额
 *   - 总分红消耗 USDT
 *   - 资金池健康比率（dividends / feeBase）
 *
 * @date 2026-05-12
 */
@Data
public class SettleReconcileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate bizDate;

    /** 分红基数 = spotFee + contractFee */
    private BigDecimal dividendBaseUsdt;
    private BigDecimal spotFeeUsdt;
    private BigDecimal contractFeeUsdt;
    private BigDecimal goldWithdrawFeeUsdt;
    private BigDecimal goldWithdrawFounderShareUsdt;
    private BigDecimal goldWithdrawPlatformFeeUsdt;

    /** 当日所有 cron 列表（含 status / success_count / failed_count / skipped / amount） */
    private List<JobAgg> jobs;

    /** 当日 USDT 分红总额（static + team + global + founder，不含 XGT 释放与 fee 聚合自身） */
    private BigDecimal totalDividendsSettledUsdt;

    /** 健康比率 = totalDividends / feeBase，feeBase=0 时为 null */
    private BigDecimal ratio;

    /** OK / WARN / CRITICAL / NO_DATA */
    private String healthLevel;

    @Data
    public static class JobAgg implements Serializable {
        private static final long serialVersionUID = 1L;
        private String jobName;
        private String status;
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer skippedCount;
        private BigDecimal amountSettledUsdt;
        private String errorMessage;
    }
}
