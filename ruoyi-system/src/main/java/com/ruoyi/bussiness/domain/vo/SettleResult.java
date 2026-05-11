package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 6 个定时任务结算的统计结果，由 doSettle 返回，AbstractSettleJob 据此 markSuccess。
 */
@Data
public class SettleResult {

    /** 应处理总数（扫到的目标行数） */
    private int totalCount = 0;

    /** 成功处理数 */
    private int successCount = 0;

    /** 失败数（个别行处理失败但不影响整体结算成功） */
    private int failedCount = 0;

    /** 跳过数（如冻结用户、矿机已 expired、无 active 矿机等） */
    private int skippedCount = 0;

    /** 本次结算总到账金额（USDT 折算） */
    private BigDecimal amountSettledUsdt = BigDecimal.ZERO;

    public static SettleResult empty() {
        return new SettleResult();
    }

    public void incSuccess() {
        this.successCount++;
    }

    public void incFailed() {
        this.failedCount++;
    }

    public void incSkipped() {
        this.skippedCount++;
    }

    public void addAmount(BigDecimal amt) {
        if (amt == null) {
            return;
        }
        if (this.amountSettledUsdt == null) {
            this.amountSettledUsdt = BigDecimal.ZERO;
        }
        this.amountSettledUsdt = this.amountSettledUsdt.add(amt);
    }
}
