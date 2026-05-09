package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * C2C商家信息对象 t_c2c_merchant
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_c2c_merchant")
public class TC2cMerchant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联t_app_user.user_id */
    private Long userId;

    /** 商家昵称 */
    private String nickname;

    /** 0=待审核 1=已通过 2=已拒绝 3=已禁用 */
    private Integer status;

    /** 总完成订单数 */
    private Integer totalOrders;

    /** 总交易额(USD) */
    private BigDecimal totalVolume;

    /** 完成率% */
    private BigDecimal completionRate;

    /** 平均放行时间(秒) */
    private Integer avgReleaseTime;

    /** 好评率% */
    private BigDecimal positiveRate;

    /** 保证金金额(USDT) */
    private BigDecimal depositAmount;

    /** 拒绝原因 */
    private String rejectReason;

    /** 管理员层级 */
    private String adminParentIds;
}
