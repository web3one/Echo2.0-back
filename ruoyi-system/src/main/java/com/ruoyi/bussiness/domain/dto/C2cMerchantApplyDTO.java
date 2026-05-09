package com.ruoyi.bussiness.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * C2C商家申请DTO
 */
@Data
public class C2cMerchantApplyDTO {
    /** 商家昵称 */
    private String nickname;
    /** 资金密码 */
    private String fundPassword;
}
