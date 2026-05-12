package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TAppAsset;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 主站平台现货 USDT 余额，用于金矿购买弹窗前置展示和余额校验。
 */
@Data
public class SpotUsdtVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal availableAmount;
    private BigDecimal totalAmount;

    public static SpotUsdtVO from(TAppAsset asset) {
        SpotUsdtVO vo = new SpotUsdtVO();
        vo.setAvailableAmount(asset != null && asset.getAvailableAmount() != null
                ? asset.getAvailableAmount() : BigDecimal.ZERO);
        vo.setTotalAmount(asset != null && asset.getAmout() != null
                ? asset.getAmout() : BigDecimal.ZERO);
        return vo;
    }
}
