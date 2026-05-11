package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创世席位购买请求（POST /api/founder/purchase）。
 *
 * 决策 4 + 追问 C：资金密码二验 + 现货 USDT 余额扣款。
 *
 * @date 2026-05-11
 */
@Data
public class FounderPurchaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资金密码（明文，service 内 BCrypt 比对） */
    private String fundPassword;

    /** 客户端幂等键（UUID 推荐）防重复扣款 */
    private String idempotentKey;
}
