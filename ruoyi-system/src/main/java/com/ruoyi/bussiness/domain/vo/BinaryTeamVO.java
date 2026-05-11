package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 双轨团队 VO（GET /api/team/binary 返回，PRD §17.4 + §14.4）。
 *
 * mining-gold 子站 renderNetwork 期望字段；前端按 todoToday/累计/弱区/匹配率/预计奖渲染。
 *
 * @date 2026-05-09
 */
@Data
public class BinaryTeamVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // === 当日业绩（团队代理奖结算依据） ===
    private BigDecimal leftTodayVolume;
    private BigDecimal rightTodayVolume;
    private BigDecimal weakTodayVolume;

    // === 累计业绩（代理升级依据） ===
    private BigDecimal leftTotalVolume;
    private BigDecimal rightTotalVolume;

    // === 双轨下线计数 ===
    private Integer leftCount;
    private Integer rightCount;
    private Integer directReferralCount;

    // === 代理等级 / 资格状态 ===
    private String agentLevel;             // V0 / V1 / V2 / ...
    private String agentStatus;            // active / frozen
    private BigDecimal matchRate;          // 团队代理奖匹配率
    private BigDecimal globalDividendRate; // V4=0.005 / V5=0.01 / 其他=0
    private BigDecimal teamDailyCap;       // 当前权益矿机 team_daily_cap_usdt（信息展示，不参与发奖）

    // === 当前权益矿机 ===
    private Boolean hasActiveMiner;
    private String currentMinerLevel;      // L1 / L2 / L3 / L4 / null

    // === 今日预计团队代理奖（健康出局未截断前的毛额预估） ===
    private BigDecimal estimatedTodayReward;

    // === 升级目标 ===
    private String nextLevel;              // 已 V5 / V0 无升级时为 null
    private BigDecimal nextLevelMatchRate;
    private Integer nextLevelMinDirectReferralCount;
    private BigDecimal nextLevelMinLeftVolume;
    private BigDecimal nextLevelMinRightVolume;
    private BigDecimal nextLevelMinActiveNodeValue;
    private Boolean nextLevelEligible;     // 是否已满足升级条件
}
