package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 底池变动流水 t_pool_log
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pool_log")
public class TPoolLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String symbol;
    private String chain;

    /** RECHARGE / WITHDRAW / AGGREGATE / MANUAL */
    private String changeType;

    /** 1=增加 -1=减少 */
    private Integer direction;

    private BigDecimal amount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;

    /** 关联类型：t_app_recharge / t_withdraw / t_aggregation_task */
    private String refType;
    /** 关联记录 ID */
    private Long refId;

    /** 操作人：SYSTEM / 管理员用户名 */
    private String operator;
}
