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
 * AI 矿机购买流水（含幂等防重复扣款）
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_node_purchase_log")
public class TNodePurchaseLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long nodeInstanceId;

    private String levelCode;

    private BigDecimal priceUsdt;

    private String paymentCurrency;

    private Long assetTxId;

    private String placementSide;

    /** 幂等键 防重复扣款 */
    private String idempotentKey;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
