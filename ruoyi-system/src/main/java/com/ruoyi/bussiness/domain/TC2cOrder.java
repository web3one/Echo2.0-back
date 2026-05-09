package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * C2C交易订单对象 t_c2c_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_c2c_order")
public class TC2cOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 关联t_c2c_advert.id */
    private Long adId;

    /** 广告编号 */
    private String adNo;

    /** 1=卖出广告 2=买入广告 */
    private Integer adType;

    /** 加密货币 */
    private String cryptoCurrency;

    /** 法币 */
    private String fiatCurrency;

    /** 锁定价格 */
    private BigDecimal price;

    /** 加密货币数量 */
    private BigDecimal cryptoAmount;

    /** 法币金额 */
    private BigDecimal fiatAmount;

    /** 买方user_id */
    private Long buyerId;

    /** 卖方user_id */
    private Long sellerId;

    /** 广告所属商家ID */
    private Long merchantId;

    /** 接单方user_id */
    private Long takerId;

    /** 卖方收款方式ID */
    private Long paymentMethodId;

    /** 0=待付款 1=已付款 2=已完成 3=已取消 4=申诉中 5=申诉完成 */
    private Integer status;

    /** 标记付款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 确认放行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date releaseTime;

    /** 取消时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 取消方(buyer/seller/system/admin) */
    private String cancelledBy;

    /** 付款超时截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timeoutAt;

    /** 管理员层级 */
    private String adminParentIds;
}
