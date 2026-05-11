package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V4/V5 全网手续费分红结算服务（金矿 agency_global_dividend_job 实现）。
 *
 * PRD §8.1 + §11 + §22：
 * - 数据源：t_daily_fee_summary[bizDate].dividend_base_usdt（= spot_fee + contract_fee）
 * - V4 池 = base × 0.5%，等分给所有 V4 active 受益人
 * - V5 池 = base × 1.0%，等分给所有 V5 active 受益人
 * - 受益人条件：t_agent_status.status='active' + 至少 1 台 active 矿机（PRD §2.3 表）
 * - 冻结代理跳过（追问 A）
 * - PRD §2.4 表：本类奖励不计入健康出局，不参与聚合截断
 * - 入账方式：100% USDT → 金矿子钱包（无 70/30，无 XGT 锁仓）
 *
 * 幂等：reward_log idempotentKey = "bizDate:agency_fee:userId"
 *       settle_log uk = (job_name, biz_date) 同日重跑 success 直接跳过
 *
 * @date 2026-05-15
 */
public interface IAgencyGlobalDividendSettleService {

    SettleResult settle(LocalDate bizDate, Long settleLogId);

    /**
     * 单用户结算（独立 REQUIRES_NEW 事务）。
     *
     * @param perUserShareUsdt 该等级池子等分后单人份额
     * @return credited（USDT 等值），null 表示跳过（冻结 / 无 active 矿机）
     * @throws org.springframework.dao.DuplicateKeyException 幂等命中
     */
    BigDecimal settleOneUser(Long userId, String agentLevelSnap, BigDecimal perUserShareUsdt,
                             LocalDate bizDate, Long settleLogId);
}
