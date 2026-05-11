package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TFounderSeat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 创世席位购买结果（POST /api/founder/purchase）。
 *
 * idempotent=true 表示幂等命中（同 idempotent_key 重复提交），客户端按 paidAt 判断是否要 toast。
 *
 * @date 2026-05-11
 */
@Data
public class FounderPurchaseResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long seatId;
    private Integer seatNo;
    private BigDecimal amountUsdt;
    private Date paidAt;
    private Boolean idempotent;

    public static FounderPurchaseResultVO from(TFounderSeat seat, BigDecimal amount, boolean idempotent) {
        FounderPurchaseResultVO vo = new FounderPurchaseResultVO();
        vo.setSeatId(seat.getId());
        vo.setSeatNo(seat.getSeatNo());
        vo.setAmountUsdt(amount);
        vo.setPaidAt(seat.getPaidAt());
        vo.setIdempotent(idempotent);
        return vo;
    }
}
