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
 * 双轨当日新增业绩（PRD §7）
 *
 * 团队代理奖结算依据；每日 UTC 00:30 binary_daily_volume_reset_job
 * 不删历史行，新业务日新行（user_id+biz_date UNIQUE）。
 *
 * @date 2026-05-10
 */
@Data
@TableName("t_binary_volume_daily")
public class TBinaryVolumeDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 业务日 YYYY-MM-DD */
    private Date bizDate;

    private BigDecimal leftVolume;

    private BigDecimal rightVolume;

    /** 弱区业绩快照（结算时写入） */
    private BigDecimal weakVolume;

    /** 团队代理奖应发金额快照（结算时写入） */
    private BigDecimal matchedAmountUsdt;

    private Long settleLogId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
