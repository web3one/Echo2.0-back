package com.ruoyi.bussiness.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * C2C商家信息展示VO
 */
@Data
public class C2cMerchantVO {
    private Long id;
    private Long userId;
    private String nickname;
    private Integer status;
    private Integer totalOrders;
    private BigDecimal totalVolume;
    private BigDecimal completionRate;
    private Integer avgReleaseTime;
    private BigDecimal positiveRate;
    private BigDecimal depositAmount;
    private String loginName;
    private String email;
}
