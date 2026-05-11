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
 * XGT 流水（事件溯源，余额错乱时可重算）
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_xgt_log")
public class TXgtLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CHANGE_LOCK = "lock";
    public static final String CHANGE_RELEASE = "release";
    public static final String CHANGE_TRANSFER = "transfer";
    public static final String CHANGE_ADMIN_ADJUST = "admin_adjust";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String changeType;

    private BigDecimal amountXgt;

    private BigDecimal balanceLockedAfter;

    private BigDecimal balanceUnlockedAfter;

    private Long relatedLockPlanId;

    private Long relatedRewardLogId;

    private String chainAddress;

    private String txHash;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
