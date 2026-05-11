package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TNodeInstance;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 我的矿机列表项（mining-gold 子站 interface MyMiner 对齐）。
 *
 * 字段命名严格匹配 mining-gold App.tsx：
 *   id / level / status / price / dailyRate / dailyStatic /
 *   accumulated / healthTarget / purchasedAt / isCurrentActive
 *
 * 用户聚合字段（2026-05-09 修订，每行同值，前端可读任意一行）：
 *   userAggregateAccumulated / userAggregateExitTarget / userAggregateRemaining
 *
 * 出局额按"用户 active 矿机最高级 × 3"算，所有 active 矿机共享同一个聚合进度条；
 * 单台矿机的 healthTarget（priceUsdt × 3）保留作为信息展示但不再用于截断。
 *
 * @date 2026-05-09
 */
@Data
public class MyMinerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String level;
    private String status;
    private BigDecimal price;
    private BigDecimal dailyRate;
    private BigDecimal dailyStatic;
    private BigDecimal accumulated;
    private BigDecimal healthTarget;
    private Date purchasedAt;
    private Boolean isCurrentActive;

    /** 用户聚合：所有 active 矿机 accumulated_reward_usdt 之和（USDT 等值名义）。 */
    private BigDecimal userAggregateAccumulated;
    /** 用户聚合：active 矿机中最高 priceUsdt × 300%；用户全部 expired 时为 0。 */
    private BigDecimal userAggregateExitTarget;
    /** 用户聚合剩余出局额度 = userAggregateExitTarget - userAggregateAccumulated（≥0）。 */
    private BigDecimal userAggregateRemaining;

    public static MyMinerVO from(TNodeInstance inst) {
        MyMinerVO vo = new MyMinerVO();
        vo.setId(inst.getId());
        vo.setLevel(inst.getLevelCode());
        vo.setStatus(inst.getStatus());
        vo.setPrice(inst.getPriceUsdt());
        vo.setDailyRate(inst.getDailyYieldRate());

        BigDecimal price = nz(inst.getPriceUsdt());
        BigDecimal rate = nz(inst.getDailyYieldRate());
        vo.setDailyStatic(price.multiply(rate).setScale(8, RoundingMode.HALF_UP));

        vo.setAccumulated(nz(inst.getAccumulatedRewardUsdt()));
        vo.setHealthTarget(nz(inst.getExitTargetUsdt()));
        vo.setPurchasedAt(inst.getActivatedAt() != null ? inst.getActivatedAt() : inst.getCreateTime());
        vo.setIsCurrentActive(Boolean.FALSE);
        vo.setUserAggregateAccumulated(BigDecimal.ZERO);
        vo.setUserAggregateExitTarget(BigDecimal.ZERO);
        vo.setUserAggregateRemaining(BigDecimal.ZERO);
        return vo;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
