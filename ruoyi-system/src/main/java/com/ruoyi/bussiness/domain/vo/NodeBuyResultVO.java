package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TNodeInstance;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 矿机购买返回。
 *
 * @date 2026-05-10
 */
@Data
public class NodeBuyResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long instanceId;

    private String levelCode;

    private BigDecimal priceUsdt;

    private BigDecimal dailyYieldRate;

    private BigDecimal exitTargetUsdt;

    private String status;

    private Date activatedAt;

    /** 是否幂等命中（true = 此次请求未实际扣款，返回的是已存在实例） */
    private Boolean idempotent;

    public static NodeBuyResultVO from(TNodeInstance inst, boolean idempotent) {
        NodeBuyResultVO vo = new NodeBuyResultVO();
        vo.setInstanceId(inst.getId());
        vo.setLevelCode(inst.getLevelCode());
        vo.setPriceUsdt(inst.getPriceUsdt());
        vo.setDailyYieldRate(inst.getDailyYieldRate());
        vo.setExitTargetUsdt(inst.getExitTargetUsdt());
        vo.setStatus(inst.getStatus());
        vo.setActivatedAt(inst.getActivatedAt());
        vo.setIdempotent(idempotent);
        return vo;
    }
}
