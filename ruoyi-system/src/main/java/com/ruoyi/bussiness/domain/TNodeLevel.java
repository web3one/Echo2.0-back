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
 * AI 矿机等级配置 L1-L4（金矿 Phase 1）
 *
 * 默认值已在 V20260510_026 迁移插入 enabled=0；admin 上线前必须 review 启用。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_node_level")
public class TNodeLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 等级编码 L1/L2/L3/L4 */
    private String levelCode;

    private String nameEn;

    private String nameZh;

    private BigDecimal priceUsdt;

    /** 每日固定收益率（0.0083 = 0.83%） */
    private BigDecimal dailyYieldRate;

    private BigDecimal teamDailyCapUsdt;

    /** 健康出局倍数（默认 3.0 = 300%） */
    private BigDecimal exitMultiplier;

    /** 是否启用：0=停用 1=启用 */
    private Integer enabled;

    private Integer sort;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
