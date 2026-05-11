package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 代理申请详情 VO（admin 审核详情 + H5 申请历史复用）。
 *
 * admin 审核页 el-descriptions 渲染：左列"提交时快照"（snap 字段），右列"当前真值"
 * （current 字段，service 实时查 BinaryTeamVO + SUM(active.priceUsdt)）。eligibleNow
 * 按当前真值判断；admin 通过/拒绝按钮文案根据 eligibleNow 着色，但不强制阻止操作。
 *
 * @date 2026-05-11
 */
@Data
public class AgentApplicationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    /** 用户登录名（admin 列表展示，service 内 JOIN t_app_user） */
    private String userName;

    private String fromLevel;
    private String targetLevel;
    private String reasonUser;
    /** service 内 JSON.parseArray 后给前端 */
    private List<String> proofUrls;

    // === 提交时快照 ===
    private Integer directReferralCountSnap;
    private BigDecimal leftVolumeTotalSnap;
    private BigDecimal rightVolumeTotalSnap;
    private BigDecimal activeNodeValueSnap;

    // === 当前真值（仅 admin 详情接口填，H5 我的申请历史可留 null） ===
    private Integer directReferralCountCurrent;
    private BigDecimal leftVolumeTotalCurrent;
    private BigDecimal rightVolumeTotalCurrent;
    private BigDecimal activeNodeValueCurrent;

    // === 目标等级当前配置（来自 t_agent_level；admin 可能改过） ===
    private Integer minDirectReferralCount;
    private BigDecimal minLeftVolumeTotal;
    private BigDecimal minRightVolumeTotal;
    private BigDecimal minActiveNodeValue;

    /** 按"当前真值 vs 当前配置"判断是否达标 */
    private Boolean eligibleNow;

    private String status;
    private Long reviewAdminId;
    private String reviewAdminName;
    private Date reviewAt;
    private String reviewRemark;
    private Date createTime;
    private Date updateTime;
}
