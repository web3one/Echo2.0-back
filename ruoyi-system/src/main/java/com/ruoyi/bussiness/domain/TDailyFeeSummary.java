package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 每日手续费聚合：B 路线第二组 V4/V5 全网分红 + 创世手续费分红 cron 的数据源。
 *
 * PRD §11.2 分红基数 = 现货手续费 + 合约手续费（不含 C2C / 提现费 / 充值费 /
 * 矿机销售额 / 上币费 / 场外收入 / 平台储备金 / 人工调整收入）。
 *
 * 金矿提现费独立统计（gold_withdraw_*），不进分红基数；用于后续创世池 / 平台收入对账。
 *
 * @date 2026-05-15
 */
@Data
@TableName("t_daily_fee_summary")
public class TDailyFeeSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_SETTLED = "settled";
    public static final String STATUS_FAILED = "failed";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDate bizDate;
    private BigDecimal spotFeeUsdt;
    private BigDecimal contractFeeUsdt;
    /** PRD §11.2 daily_cex_fee_base = spotFeeUsdt + contractFeeUsdt */
    private BigDecimal dividendBaseUsdt;
    private BigDecimal goldWithdrawFeeUsdt;
    private BigDecimal goldWithdrawFounderShareUsdt;
    private BigDecimal goldWithdrawPlatformFeeUsdt;
    private String extraData;
    private String status;
    private Long settleLogId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
