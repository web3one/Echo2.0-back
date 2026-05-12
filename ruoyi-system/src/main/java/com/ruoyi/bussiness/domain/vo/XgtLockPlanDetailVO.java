package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Admin XGT 锁仓详情 VO：plan 字段 + 用户当前 balance 快照（PRD §15.5）。
 *
 * @date 2026-05-12
 */
@Data
public class XgtLockPlanDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private XgtLockPlanAdminVO plan;

    /** 用户当前 XGT 锁仓余额 */
    private BigDecimal userBalanceLocked;

    /** 用户当前 XGT 可用余额 */
    private BigDecimal userBalanceUnlocked;
}
