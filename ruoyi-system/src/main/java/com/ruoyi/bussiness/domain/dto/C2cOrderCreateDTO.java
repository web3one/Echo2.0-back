package com.ruoyi.bussiness.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * C2C创建订单DTO
 */
@Data
public class C2cOrderCreateDTO {
    /** 广告ID */
    private Long adId;
    /** 法币金额(USD) */
    private BigDecimal fiatAmount;
    /** 加密货币数量(可选，二选一) */
    private BigDecimal cryptoAmount;
    /** 卖方收款方式ID */
    private Long paymentMethodId;
}
