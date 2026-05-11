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
 * XGT 站内积分余额（PM 决策 2 + 5：站内积分但预留上链字段）
 *
 * total = balance_locked + balance_unlocked
 * 锁仓中余额由 t_xgt_lock_plan 驱动，30 天到期由 xgt_lock_release_job 移到 unlocked。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_xgt_balance")
public class TXgtBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal balanceLocked;

    private BigDecimal balanceUnlocked;

    private String chainAddress;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
