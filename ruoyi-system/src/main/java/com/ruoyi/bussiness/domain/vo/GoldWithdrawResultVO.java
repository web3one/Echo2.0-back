package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提现单结果 VO（POST /api/gold/withdraw 响应）。
 */
@Data
public class GoldWithdrawResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String assetType;
    private BigDecimal grossAmount;
    private BigDecimal feeAmount;
    private BigDecimal founderShareAmount;
    private BigDecimal platformFeeAmount;
    private BigDecimal netAmount;
    private BigDecimal feeRate;
    private BigDecimal founderShareRate;
    private String status;
    private Date completedAt;
    /** true 表示幂等命中，没有再次扣款 */
    private Boolean idempotent;

    public static GoldWithdrawResultVO from(TGoldWithdrawOrder order, boolean idempotent) {
        GoldWithdrawResultVO vo = new GoldWithdrawResultVO();
        vo.setOrderId(order.getId());
        vo.setAssetType(order.getAssetType());
        vo.setGrossAmount(order.getGrossAmount());
        vo.setFeeAmount(order.getFeeAmount());
        vo.setFounderShareAmount(order.getFounderShareAmount());
        vo.setPlatformFeeAmount(order.getPlatformFeeAmount());
        vo.setNetAmount(order.getNetAmount());
        vo.setFeeRate(order.getFeeRateSnap());
        vo.setFounderShareRate(order.getFounderShareRateSnap());
        vo.setStatus(order.getStatus());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setIdempotent(idempotent);
        return vo;
    }
}
