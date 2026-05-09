package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

/**
 * C2C管理员处理申诉DTO
 */
@Data
public class C2cAppealResolveDTO {
    /** 申诉ID */
    private Long appealId;
    /** 处理决定: release=放行给买方, cancel=退回给卖方 */
    private String decision;
    /** 处理说明 */
    private String adminResult;
}
