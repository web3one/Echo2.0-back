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
 * ecosystem_credit 余额（PRD §10）
 *
 * 团队代理奖的 30% 部分进 balance_locked；用户提交解锁申请
 * （3x 交易量 / XGT 锁仓 30 天）成功后，转入 balance_unlocked，
 * 一般立即转 USDT 现货（balance_unlocked 长期接近 0）。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_ecosystem_credit_balance")
public class TEcosystemCreditBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 未解锁的 credit 累计（团队代理奖 30% 部分进这里） */
    private BigDecimal balanceLocked;

    /** 已解锁未划出（一般立即转 USDT 后归零） */
    private BigDecimal balanceUnlocked;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
