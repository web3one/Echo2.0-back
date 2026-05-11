package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 创世席位购买流水（200k 大额必含 fund_password_verified=1 + IP/UA 审计）。
 *
 * @date 2026-05-11
 */
@Data
@TableName("t_founder_purchase_log")
public class TFounderPurchaseLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer seatNo;
    private BigDecimal amountUsdt;
    private String paymentCurrency;
    /** 关联现货资产扣款流水ID（如有） */
    private Long assetTxId;
    /** 决策 4 必填 1 */
    private Integer fundPasswordVerified;
    private String idempotentKey;
    private String clientIp;
    private String clientUserAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
