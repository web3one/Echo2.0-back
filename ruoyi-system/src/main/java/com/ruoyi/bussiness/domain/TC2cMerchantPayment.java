package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * C2C商家收付款方式对象 t_c2c_merchant_payment
 */
@Data
@TableName("t_c2c_merchant_payment")
public class TC2cMerchantPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联t_c2c_merchant.id */
    private Long merchantId;

    /** 关联t_app_user.user_id */
    private Long userId;

    /** 1=银行转账 */
    private Integer paymentType;

    /** 银行名称 */
    private String bankName;

    /** 支行名称 */
    private String bankBranch;

    /** 开户人姓名 */
    private String accountName;

    /** 银行账号 */
    private String accountNumber;

    /** ABA routing number */
    private String routingNumber;

    /** SWIFT/BIC代码 */
    private String swiftCode;

    /** 1=启用 0=禁用 */
    private Integer isEnabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
