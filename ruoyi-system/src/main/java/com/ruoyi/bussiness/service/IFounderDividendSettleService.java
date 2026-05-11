package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创世合伙人 49 席手续费分红结算服务（金矿 founder_dividend_job 实现）。
 *
 * PRD §12 + §22 + 用户决策 2026-05-15（取 B 口径）：
 * - 数据源：t_daily_fee_summary[bizDate]
 *     creditedPool = (spot_fee + contract_fee) × 10%  +  gold_withdraw_founder_share
 * - 等分 N = COUNT(owned 且未冻结席位)，0 → skip
 * - perSeat = creditedPool / N
 * - PRD §2.3 表：本类不需要 active 矿机即可领（创世权益独立）
 * - PRD §2.4 表：本类不计入健康出局，100% USDT 入账
 * - 冻结席位 / available 席位 不参与分红
 *
 * 幂等：reward_log idempotentKey = "bizDate:founder_fee:seatId"（一人一席 UK，等价 userId 幂等）
 *       settle_log uk = (job_name, biz_date) 同日重跑 success 直接跳过
 *
 * @date 2026-05-15
 */
public interface IFounderDividendSettleService {

    SettleResult settle(LocalDate bizDate, Long settleLogId);

    /**
     * 单席位结算（独立 REQUIRES_NEW 事务）。
     *
     * @return credited（USDT 等值），null 表示跳过
     * @throws org.springframework.dao.DuplicateKeyException 幂等命中
     */
    BigDecimal settleOneSeat(Long seatId, Integer seatNo, Long ownerUserId,
                             BigDecimal perSeatUsdt,
                             LocalDate bizDate, Long settleLogId);
}
