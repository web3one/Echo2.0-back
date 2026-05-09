package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

/**
 * C2C创建申诉DTO
 */
@Data
public class C2cAppealCreateDTO {
    /** 订单ID */
    private Long orderId;
    /** 1=买方未付款 2=卖方未放行 3=付款问题 4=欺诈 5=其他 */
    private Integer appealType;
    /** 申诉原因 */
    private String appealReason;
    /** 证据图片URL(逗号分隔) */
    private String evidenceUrls;
}
