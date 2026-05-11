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
 * 创世合伙人 49 席（金矿 Phase 2，PRD §12，决策 4 直接扣不审核）。
 *
 * 49 行预创建在 V20260510_032 迁移中（seat_no=1..49 / status='available'）。
 * 用户购买时按 seat_no ASC 占第一个 available；UNIQUE uk_owner 限制一人一席。
 *
 * 决策 3 影响：去掉 authorized_v5_user_id 等 V5 授权字段，V5 提升走 admin 直改通道。
 *
 * @date 2026-05-11
 */
@Data
@TableName("t_founder_seat")
public class TFounderSeat implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_OWNED = "owned";
    public static final String STATUS_FROZEN = "frozen";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 席位号 1-49 */
    private Integer seatNo;

    /** available / owned / frozen */
    private String status;

    private Long ownerUserId;

    /** PRD §12: 200,000 USDT */
    private BigDecimal priceUsdt;

    private Date paidAt;

    /** 关联 XGT 锁仓计划 ID（10% 总供应 / 49 等分；本轮先 null，C 路线再补） */
    private Long xgtLockPlanId;

    private Date frozenAt;

    private String frozenReason;

    private Long frozenByAdminId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
