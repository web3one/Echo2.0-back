package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CopyFollowDTO {
    private Long traderUserId;
    private BigDecimal copyTotalAmount;
    private BigDecimal maxSingleAmount;
    private BigDecimal stopLossRate;
}
