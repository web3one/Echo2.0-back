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
 * C2C买卖广告对象 t_c2c_advert
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_c2c_advert")
public class TC2cAdvert extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 广告编号 */
    private String adNo;

    /** 关联t_c2c_merchant.id */
    private Long merchantId;

    /** 关联t_app_user.user_id */
    private Long userId;

    /** 1=出售 2=购买 */
    private Integer adType;

    /** 加密货币(USDT/USDC) */
    private String cryptoCurrency;

    /** 法币 */
    private String fiatCurrency;

    /** 1=固定价格 2=浮动价格 */
    private Integer priceType;

    /** 单价(法币) */
    private BigDecimal price;

    /** 浮动比例% */
    private BigDecimal floatRate;

    /** 总数量(加密货币) */
    private BigDecimal totalAmount;

    /** 剩余数量 */
    private BigDecimal remainingAmount;

    /** 最小交易限额(USD) */
    private BigDecimal minLimit;

    /** 最大交易限额(USD) */
    private BigDecimal maxLimit;

    /** 付款时限(分钟) */
    private Integer paymentTimeLimit;

    /** 支持的付款方式ID列表 */
    private String paymentMethodIds;

    /** 自动回复消息 */
    private String autoReplyMsg;

    /** 交易条款 */
    private String tradeTerms;

    /** 要求对方完成KYC */
    private Integer requireKyc;

    /** 0=下架 1=上架 2=已耗尽 3=管理员禁用 */
    private Integer status;

    /** 管理员层级 */
    private String adminParentIds;
}
