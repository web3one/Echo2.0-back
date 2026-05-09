package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 归集明细 t_aggregation_item（每个子地址一行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aggregation_item")
public class TAggregationItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String chain;
    private Long userId;

    private String fromAddress;
    private String toAddress;

    private BigDecimal usdtAmount;
    private BigDecimal gasTopupAmount;

    private String gasTopupHash;
    private String collectHash;

    /** PENDING / GAS_SENT / COLLECTING / SUCCESS / FAILED */
    private String status;
    private String errorMsg;
}
