package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 金矿子钱包→现货提现请求 DTO。
 *
 * @date 2026-05-15
 */
@Data
public class GoldWithdrawDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资产类型，目前仅 USDT */
    private String assetType;
    /** 提现毛额 */
    private BigDecimal amount;
    /** 资金密码（与现货大额提现一致：BCrypt 比对 userTardPwd） */
    private String fundPassword;
    /** 幂等键 <user_id>:<request_uuid> */
    private String idempotentKey;
}
