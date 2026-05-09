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
 * 代理等级配置（V0-V5）
 *
 * V0=普通用户，不可申请；V1-V5 升级条件按 PRD §8.1。
 * admin 可调整匹配率 / 全网分红率 / 升级条件，但默认值已在 V20260510_028 迁移插入。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_agent_level")
public class TAgentLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 等级编码 V0/V1/V2/V3/V4/V5 */
    private String levelCode;

    private String nameEn;

    private String nameZh;

    /** 团队代理奖匹配率 0.05=5% */
    private BigDecimal matchRate;

    /** 全网手续费分红率（V4=0.005, V5=0.01, 其他 0） */
    private BigDecimal globalDividendRate;

    private BigDecimal minActiveNodeValueUsdt;

    private Integer minDirectReferralCount;

    /** 单边最低累计业绩（左右各需达此值） */
    private BigDecimal minLeftVolumeTotal;

    private BigDecimal minRightVolumeTotal;

    private Integer enabled;

    private Integer sort;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
