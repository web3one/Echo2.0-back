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
 * 代理申请审核（金矿 Phase 2，决策 3 通道 1）
 *
 * H5 用户主动申请升级 V1-V5；admin 客服审核通过后由
 * IAgentStatusService.changeAgentLevel(source='user_apply') 统一改 t_agent_status。
 * 申请提交时快照 4 个升级条件实际值，便于审核时和 t_agent_level
 * 当前配置对比（admin 调过等级条件后存量 pending 按新条件评估）。
 *
 * @date 2026-05-11
 */
@Data
@TableName("t_agent_application")
public class TAgentApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 申请提交时用户的代理等级（快照） */
    private String fromLevel;

    /** 目标等级 V1-V5 */
    private String targetLevel;

    /** 用户填的申请理由（可选） */
    private String reasonUser;

    /** 证明材料 URL 列表（JSON 数组字符串，可选） */
    private String proofUrls;

    // === 提交时快照（admin 审核详情对比用） ===

    private Integer directReferralCountSnap;
    private BigDecimal leftVolumeTotalSnap;
    private BigDecimal rightVolumeTotalSnap;
    private BigDecimal activeNodeValueSnap;

    /** pending / approved / rejected / cancelled */
    private String status;

    private Long reviewAdminId;

    private Date reviewAt;

    private String reviewRemark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
