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
 * 双轨累计业绩（PRD §7）
 *
 * 代理升级条件依据，永久累计不清零。
 *
 * @date 2026-05-10
 */
@Data
@TableName("t_binary_volume_total")
public class TBinaryVolumeTotal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal leftVolumeTotal;

    private BigDecimal rightVolumeTotal;

    private Integer directReferralCount;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
