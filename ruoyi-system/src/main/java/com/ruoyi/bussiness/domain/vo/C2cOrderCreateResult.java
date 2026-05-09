package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

/**
 * C2C订单创建结果
 */
@Data
public class C2cOrderCreateResult {
    private Long orderId;
    private String orderNo;
    private String error;

    public static C2cOrderCreateResult success(Long orderId, String orderNo) {
        C2cOrderCreateResult result = new C2cOrderCreateResult();
        result.setOrderId(orderId);
        result.setOrderNo(orderNo);
        return result;
    }

    public static C2cOrderCreateResult error(String error) {
        C2cOrderCreateResult result = new C2cOrderCreateResult();
        result.setError(error);
        return result;
    }
}
