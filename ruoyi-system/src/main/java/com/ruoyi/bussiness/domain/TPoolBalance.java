package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 平台底池余额 t_pool_balance（按 symbol+chain 唯一）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pool_balance")
public class TPoolBalance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String symbol;
    private String chain;

    private BigDecimal balance;
    private BigDecimal frozenBalance;
    private BigDecimal alertThreshold;
    private BigDecimal maxSingleWithdraw;

    private Integer displayInH5;
    private Integer displayBalance;
}
