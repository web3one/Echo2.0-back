package com.ruoyi.bussiness.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;

/**
 * C2C广告展示VO（包含商家信息）
 */
@Data
public class C2cAdvertVO {
    private Long id;
    private String adNo;
    private Integer adType;
    private String cryptoCurrency;
    private String fiatCurrency;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private BigDecimal remainingAmount;
    private BigDecimal minLimit;
    private BigDecimal maxLimit;
    private Integer paymentTimeLimit;
    private String autoReplyMsg;
    private String tradeTerms;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    // 商家信息
    private Long merchantId;
    private Long merchantUserId;
    private String merchantNickname;
    private Integer merchantTotalOrders;
    private BigDecimal merchantCompletionRate;
    private BigDecimal merchantPositiveRate;
    private Integer merchantAvgReleaseTime;

    // 付款方式
    private String paymentMethodIds;
    private List<TC2cMerchantPayment> paymentMethods;
}
