package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Admin 端代理等级参数更新 DTO（PRD §15.3）。
 *
 * level_code / id / 时间字段不允许通过此 DTO 改动。
 *
 * @date 2026-05-12
 */
@Data
public class AgentLevelUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String nameEn;
    private String nameZh;

    /** 团队代理奖匹配率 0.05=5% */
    private BigDecimal matchRate;

    /** 全网手续费分红率（V4=0.005, V5=0.01, 其他 0） */
    private BigDecimal globalDividendRate;

    private BigDecimal minActiveNodeValueUsdt;

    private Integer minDirectReferralCount;

    private BigDecimal minLeftVolumeTotal;

    private BigDecimal minRightVolumeTotal;

    /** 1=允许申请，0=禁用 */
    private Integer enabled;

    private Integer sort;

    private String remark;
}
