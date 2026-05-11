package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 代理升级申请请求（POST /api/agency/apply）。
 *
 * 后端校验顺序：
 *   1. targetLevel ∈ V1-V5 且 t_agent_level.enabled=1
 *   2. 用户当前等级 rank < targetLevel rank
 *   3. 同用户无 pending 申请
 *   4. SUM(active.priceUsdt) ≥ min_active_node_value_usdt
 *   5. 直推 V1+ 数 ≥ min_direct_referral_count
 *   6. left_total ≥ min_left && right_total ≥ min_right
 *
 * @date 2026-05-11
 */
@Data
public class AgencyApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标等级 V1 / V2 / V3 / V4 / V5 */
    private String targetLevel;

    /** 用户申请理由（可选，最长 500） */
    private String reasonUser;

    /** 证明材料 URL（可选；多张图片传 URL 列表，service 内 JSON.toJSONString 存到 proof_urls） */
    private List<String> proofUrls;
}
