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
 * 金矿奖励流水（结算/对账/幂等核心枢纽，PRD §17）
 *
 * idempotent_key 格式：settleLogId:rewardType:userId:nodeInstanceId
 * UNIQUE(idempotent_key) 保证 6 个定时任务重跑不会重复发奖。
 *
 * 金额三件套（PRD §22 第 5 条）：
 *   gross_amount_usdt  = 按 100% 计入健康出局的毛额
 *   usdt_credited      = 健康出局截断后实际到账
 *   exit_truncated     = 被截断作废的金额（永久消失，不能转给下台矿机）
 *
 * 70/30 分账由 reward_type 决定：
 *   static          → 50% USDT + 50% XGT 锁仓
 *   referral / team → 70% USDT + 30% credit
 *   agency_fee / founder_fee → 100% USDT
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_reward_log")
public class TRewardLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_STATIC = "static";
    public static final String TYPE_REFERRAL = "referral";
    public static final String TYPE_TEAM = "team";
    public static final String TYPE_AGENCY_FEE = "agency_fee";
    public static final String TYPE_FOUNDER_FEE = "founder_fee";
    public static final String TYPE_XGT_UNLOCK = "xgt_unlock";
    public static final String TYPE_ECO_CREDIT_UNLOCK = "eco_credit_unlock";

    public static final String STATUS_SETTLED = "settled";
    public static final String STATUS_RELEASED = "released";
    public static final String STATUS_UNLOCKED = "unlocked";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String rewardType;

    private BigDecimal grossAmountUsdt;

    private BigDecimal usdtCredited;

    private BigDecimal exitTruncatedAmount;

    private BigDecimal xgtCredited;

    private Long xgtLockPlanId;

    private BigDecimal ecoCreditAmount;

    private Long relatedNodeInstanceId;

    private Long sourceUserId;

    private BigDecimal sourceAmount;

    private Integer countedInHealthExit;

    private BigDecimal agentMatchRate;

    private String agentLevelSnapshot;

    private Long settleLogId;

    private LocalDate bizDate;

    private String idempotentKey;

    private String status;

    private Date releasedAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
