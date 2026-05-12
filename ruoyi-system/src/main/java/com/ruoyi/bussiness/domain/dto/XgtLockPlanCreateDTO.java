package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Admin 端手工创建 XGT 锁仓计划（PRD §15.5）。
 *
 * 用于 team_advisor / private_sale / ecosystem_fund / partner 等手动分配场景。
 * 静态分红 / 创世 49 席 / credit_unlock 走自动路径，不通过此 DTO。
 *
 * 自动计算：release_at = locked_at + 30 天。
 * 如果 locked_at 不传，使用 NOW()。
 *
 * 幂等：source_ref_id 必填且 UNIQUE(source_type, source_ref_id)；建议带 admin
 * 操作时间戳避免人为重复，如 "admin_{adminId}_{timestamp}"，由 admin 前端拼装。
 *
 * @date 2026-05-12
 */
@Data
public class XgtLockPlanCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    /** team_advisor / private_sale / ecosystem_fund / partner */
    private String sourceType;

    /** 业务关联 ID（admin 手工填，幂等 key 的一部分） */
    private String sourceRefId;

    private BigDecimal amountXgt;

    /** USD 名义价值（admin 选填，记录用） */
    private BigDecimal amountUsdNominal;

    /** 锁仓开始时间（不传默认 NOW，release_at 自动算 +30 天） */
    private Date lockedAt;

    private String remark;
}
