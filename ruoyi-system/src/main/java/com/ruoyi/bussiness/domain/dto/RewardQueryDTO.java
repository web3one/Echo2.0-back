package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 奖励流水查询参数。
 *
 * 字段：
 *   rewardType            非空过滤（static/referral/team/agency_fee/founder_fee/xgt_unlock/eco_credit_unlock）
 *   relatedNodeInstanceId 非空过滤指定矿机
 *   bizDateFrom / bizDateTo 业务日期闭区间
 *   pageNum / pageSize    分页（默认 1 / 20，pageSize 上限 100）
 *
 * @date 2026-05-09
 */
@Data
public class RewardQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String rewardType;
    private Long relatedNodeInstanceId;
    private LocalDate bizDateFrom;
    private LocalDate bizDateTo;

    private Integer pageNum;
    private Integer pageSize;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
