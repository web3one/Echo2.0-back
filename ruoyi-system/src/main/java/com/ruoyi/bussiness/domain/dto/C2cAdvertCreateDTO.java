package com.ruoyi.bussiness.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * C2C发布广告DTO
 */
@Data
public class C2cAdvertCreateDTO {
    /** 1=出售 2=购买 */
    private Integer adType;
    /** 加密货币(usdt/usdc) */
    private String cryptoCurrency;
    /** 单价(USD) */
    private BigDecimal price;
    /** 总数量 */
    private BigDecimal totalAmount;
    /** 最小限额(USD) */
    private BigDecimal minLimit;
    /** 最大限额(USD) */
    private BigDecimal maxLimit;
    /** 付款时限(分钟) */
    private Integer paymentTimeLimit;
    /** 支持的付款方式ID(逗号分隔) */
    private String paymentMethodIds;
    /** 自动回复消息 */
    private String autoReplyMsg;
    /** 交易条款 */
    private String tradeTerms;
    /** 资金密码(卖出广告需要) */
    private String fundPassword;
}
