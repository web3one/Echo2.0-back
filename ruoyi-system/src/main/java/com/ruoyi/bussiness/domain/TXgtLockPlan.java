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
 * XGT 锁仓计划（PRD §13：30 天一次性释放，4 状态机）
 *
 * UNIQUE(source_type, source_ref_id) 防止同一笔来源（如静态分红 reward_log.id）
 * 重复创建锁仓计划。cron 重跑碰到同 key 即跳过。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_xgt_lock_plan")
public class TXgtLockPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_STATIC_REWARD = "static_reward";
    public static final String SOURCE_FOUNDER_SEAT = "founder_seat";
    public static final String SOURCE_TEAM_ADVISOR = "team_advisor";
    public static final String SOURCE_PRIVATE_SALE = "private_sale";
    public static final String SOURCE_ECOSYSTEM_FUND = "ecosystem_fund";
    public static final String SOURCE_PARTNER = "partner";
    /** PRD §10 用户为解锁 ecosystem_credit 主动锁仓 XGT 30 天 */
    public static final String SOURCE_CREDIT_UNLOCK = "credit_unlock";

    public static final String STATUS_LOCKED = "locked";
    public static final String STATUS_RELEASABLE = "releasable";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FROZEN = "frozen";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sourceType;

    private String sourceRefId;

    private BigDecimal amountXgt;

    private BigDecimal amountUsdNominal;

    private Date lockedAt;

    private Date releaseAt;

    private Date releasedAt;

    private String status;

    private String frozenReason;

    private String chainAddress;

    private String txHash;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
