package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 代理申请审核请求（admin 通过 / 拒绝）。
 *
 * @date 2026-05-11
 */
@Data
public class AgencyReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核备注 / 拒绝原因（必填，落 t_agent_application.review_remark + t_agent_level_change_log.reason） */
    private String reviewRemark;
}
