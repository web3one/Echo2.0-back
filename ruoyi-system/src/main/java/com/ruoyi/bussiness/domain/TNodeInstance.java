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
 * 用户 AI 矿机实例（金矿 Phase 1）
 *
 * 每台矿机独立 300% 出局额度（决策 1）。购买时快照价格/收益率/封顶/出局目标，
 * 避免后续 admin 改 t_node_level 配置影响存量矿机出局计算。
 * 健康出局累计直接维护在本表（accumulated_reward_usdt vs exit_target_usdt），
 * 不另建 t_health_exit_progress。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_node_instance")
public class TNodeInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_FROZEN = "frozen";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long levelId;

    private String levelCode;

    // 购买时快照
    private BigDecimal priceUsdt;
    private BigDecimal dailyYieldRate;
    private BigDecimal teamDailyCapUsdt;
    private BigDecimal exitTargetUsdt;

    // 健康出局累计（决策 1：每台矿机独立 300%）
    private BigDecimal accumulatedRewardUsdt;
    private BigDecimal accumulatedStaticUsdt;
    private BigDecimal accumulatedReferralUsdt;
    private BigDecimal accumulatedTeamUsdt;

    /** active / expired / frozen / cancelled */
    private String status;

    private Date activatedAt;

    private Date expiredAt;

    private Date frozenAt;

    private String frozenReason;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
