package com.ruoyi.bussiness.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;

/**
 * C2C订单详情VO（包含买卖双方信息）
 */
@Data
public class C2cOrderVO {
    private Long id;
    private String orderNo;
    private Long adId;
    private String adNo;
    private Integer adType;
    private String cryptoCurrency;
    private String fiatCurrency;
    private BigDecimal price;
    private BigDecimal cryptoAmount;
    private BigDecimal fiatAmount;
    private Long buyerId;
    private Long sellerId;
    private Long merchantId;
    private Long takerId;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date releaseTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timeoutAt;

    private String cancelReason;
    private String cancelledBy;

    // 买方信息
    private String buyerName;
    // 卖方信息
    private String sellerName;
    // 商家昵称
    private String merchantNickname;

    // 卖方收款方式
    private TC2cMerchantPayment sellerPayment;

    // 广告自动回复
    private String autoReplyMsg;
}
