package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * POST /api/ecosystem-credit/unlock 返回体
 */
@Data
public class EcosystemCreditUnlockResultVO {

    private Long unlockLogId;
    private String unlockType;
    private BigDecimal amountCredit;

    /** A 路径：需累计交易量 */
    private BigDecimal tradeVolumeRequired;

    /** B 路径：关联 XGT 锁仓 plan ID + 释放时间 */
    private Long xgtLockPlanId;
    private Date xgtLockReleaseAt;

    private String status;
    private Date startedAt;

    private boolean idempotent;
}
