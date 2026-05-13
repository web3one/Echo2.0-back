package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * ecosystem_credit 解锁请求（PRD §10，两条路径 + 状态机）
 *
 * @date 2026-05-11 v3.10 C-2
 */
@Data
@TableName("t_ecosystem_credit_unlock_log")
public class TEcosystemCreditUnlockLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_TRADE_VOLUME = "trade_volume";
    public static final String TYPE_XGT_LOCK = "xgt_lock";

    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String unlockType;
    private BigDecimal amountCredit;

    /** A 路径：需累计交易量 = amount × 3 */
    private BigDecimal tradeVolumeRequired;
    private BigDecimal tradeVolumeCompleted;

    /** B 路径：关联的 XGT 锁仓计划 ID */
    private Long xgtLockPlanId;

    private String status;
    private Date startedAt;
    private Date completedAt;
    private String cancelReason;
    private BigDecimal usdtCredited;
    private Long relatedRewardLogId;
    private String idempotentKey;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
