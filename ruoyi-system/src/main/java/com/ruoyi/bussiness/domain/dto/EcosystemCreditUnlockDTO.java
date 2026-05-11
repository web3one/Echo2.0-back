package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * POST /api/ecosystem-credit/unlock 请求体（PRD §10）
 */
@Data
public class EcosystemCreditUnlockDTO {

    /** 申请解锁的 credit 数量 */
    private BigDecimal amountCredit;

    /** 解锁路径：trade_volume / xgt_lock */
    private String unlockType;

    /** 前端生成的幂等键（避免重复点击双开请求） */
    private String idempotentKey;
}
