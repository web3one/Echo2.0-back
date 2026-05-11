package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TXgtLockPlan;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * XGT 锁仓计划列表项（mining-gold renderXgtLocks 视图）。
 *
 * @date 2026-05-09
 */
@Data
public class XgtLockVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sourceType;
    private BigDecimal amountXgt;
    private BigDecimal amountUsdNominal;
    private Date lockedAt;
    private Date releaseAt;
    private Date releasedAt;
    private String status;

    public static XgtLockVO from(TXgtLockPlan plan) {
        XgtLockVO vo = new XgtLockVO();
        vo.setId(plan.getId());
        vo.setSourceType(plan.getSourceType());
        vo.setAmountXgt(plan.getAmountXgt());
        vo.setAmountUsdNominal(plan.getAmountUsdNominal());
        vo.setLockedAt(plan.getLockedAt());
        vo.setReleaseAt(plan.getReleaseAt());
        vo.setReleasedAt(plan.getReleasedAt());
        vo.setStatus(plan.getStatus());
        return vo;
    }
}
