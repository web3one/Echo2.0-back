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
 * 金矿子钱包流水（事件溯源，余额错乱时可重算）。
 *
 * @date 2026-05-15
 */
@Data
@TableName("t_gold_wallet_log")
public class TGoldWalletLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 静态分红入账 */
    public static final String CHANGE_REWARD_STATIC = "reward_static";
    /** 直推奖入账 */
    public static final String CHANGE_REWARD_REFERRAL = "reward_referral";
    /** 团队代理奖入账 */
    public static final String CHANGE_REWARD_TEAM = "reward_team";
    /** V4/V5 全网手续费分红入账 */
    public static final String CHANGE_REWARD_AGENCY_FEE = "reward_agency_fee";
    /** 创世合伙人手续费分红入账 */
    public static final String CHANGE_REWARD_FOUNDER_FEE = "reward_founder_fee";
    /** 提现至现货（出账） */
    public static final String CHANGE_WITHDRAW_OUT = "withdraw_out";
    /** 提现失败退款（入账） */
    public static final String CHANGE_REFUND = "refund";
    /** admin 人工调账 */
    public static final String CHANGE_ADMIN_ADJUST = "admin_adjust";

    public static final String BIZ_REF_REWARD_LOG = "reward_log";
    public static final String BIZ_REF_WITHDRAW_ORDER = "withdraw_order";
    public static final String BIZ_REF_ADMIN = "admin";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String changeType;
    /** 正=入账，负=出账 */
    private BigDecimal amountUsdt;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String bizRefType;
    private String bizRefId;
    private String idempotentKey;
    private Long operatorAdminId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
