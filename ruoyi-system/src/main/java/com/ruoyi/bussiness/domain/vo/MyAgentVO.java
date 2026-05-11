package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 我的代理状态 + 升级条件矩阵（GET /api/agency/me）。
 *
 * mining-gold 子站 renderAgencyCenter 顶部 hero 用 currentLevel / matchRate /
 * globalDividendRate / agentStatus，中部用 upgradeOptions 矩阵列出 V1-V5 升级条件，
 * 每行 4 个子条件（实际值 vs 目标值 + 是否达标）。
 *
 * @date 2026-05-11
 */
@Data
public class MyAgentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前代理等级 V0-V5 */
    private String currentLevel;
    /** active / frozen */
    private String agentStatus;
    /** 当前等级匹配率 0.05=5% */
    private BigDecimal matchRate;
    /** 当前等级全网手续费分红率 V4=0.005 / V5=0.01 / 其他=0 */
    private BigDecimal globalDividendRate;

    /** 当前用户已聚合的真实快照（升级条件实际值） */
    private BigDecimal activeNodeValueTotal;
    private Integer directReferralAgentV1Plus;
    private BigDecimal leftVolumeTotal;
    private BigDecimal rightVolumeTotal;

    /** 是否有正在审核中的申请（true 时禁止再提交） */
    private Boolean hasPendingApplication;
    /** pending 中那条申请的目标等级（用于 UI 提示"V2 申请审核中"） */
    private String pendingTargetLevel;
    /** pending 中那条申请的 id（用于"撤销"按钮） */
    private Long pendingApplicationId;

    /** 可申请的升级目标列表（V1-V5 中 enabled=1 且 rank > currentLevel rank） */
    private List<UpgradeOption> upgradeOptions;

    @Data
    public static class UpgradeOption implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 目标等级 V1-V5 */
        private String targetLevel;
        private String nameEn;
        private String nameZh;
        private BigDecimal matchRate;
        private BigDecimal globalDividendRate;

        /** 4 个升级子条件 */
        private Condition minerValue;
        private Condition directReferral;
        private Condition leftVolume;
        private Condition rightVolume;

        /** 是否全部 4 个条件都达标（前端按钮置灰 / 高亮依据） */
        private Boolean eligible;
    }

    @Data
    public static class Condition implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 实际值 */
        private BigDecimal actual;
        /** 目标值 */
        private BigDecimal target;
        /** 实际 ≥ 目标 */
        private Boolean met;
    }
}
