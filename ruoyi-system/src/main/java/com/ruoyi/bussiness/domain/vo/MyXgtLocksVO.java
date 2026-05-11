package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 我的 XGT 锁仓汇总（页面顶部 3 张统计卡 + 锁仓计划列表）。
 *
 * totalBalance = balanceLocked + balanceUnlocked（与 t_xgt_balance 一致）
 *
 * @date 2026-05-09
 */
@Data
public class MyXgtLocksVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal totalBalance;
    private BigDecimal balanceLocked;
    private BigDecimal balanceUnlocked;

    private List<XgtLockVO> locks;
}
