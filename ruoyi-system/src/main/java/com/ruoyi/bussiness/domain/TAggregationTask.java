package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 归集任务 t_aggregation_task
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aggregation_task")
public class TAggregationTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String batchId;
    private String chain;

    /** GAS_TOPUP / COLLECT / DONE */
    private String phase;
    /** PENDING / RUNNING / SUCCESS / PARTIAL / FAILED */
    private String status;

    private Integer targetCount;
    private Integer successCount;
    private Integer failedCount;

    private BigDecimal gasTotal;
    private BigDecimal usdtCollected;

    private Date startTime;
    private Date endTime;
    private String errorMsg;
}
