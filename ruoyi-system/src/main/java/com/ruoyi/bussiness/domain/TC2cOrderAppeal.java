package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * C2C订单申诉对象 t_c2c_order_appeal
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_c2c_order_appeal")
public class TC2cOrderAppeal extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 申诉编号 */
    private String appealNo;

    /** 关联t_c2c_order.id */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 申诉发起人user_id */
    private Long initiatorId;

    /** 被申诉方user_id */
    private Long respondentId;

    /** 1=买方未付款 2=卖方未放行 3=付款问题 4=欺诈 5=其他 */
    private Integer appealType;

    /** 申诉原因 */
    private String appealReason;

    /** 证据图片URL(逗号分隔) */
    private String evidenceUrls;

    /** 0=待处理 1=处理中 2=已解决-放行 3=已解决-取消 4=已关闭 */
    private Integer status;

    /** 处理管理员ID */
    private Long adminId;

    /** 管理员处理结果说明 */
    private String adminResult;

    /** 管理员层级 */
    private String adminParentIds;
}
