package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 团队代理奖结算服务（金矿 agency_team_reward_job 实现）。
 *
 * PRD §9 + §22.7：
 * - 扫 t_binary_volume_daily 中 biz_date = bizDate 的所有用户行
 * - 双轨弱区当日新增业绩：weak = min(left_today, right_today)；任一为 0 → skip
 * - 用户必须 V1+ 代理 + 当前有效权益矿机（active）+ 未冻结
 * - 公式（PRD §9.3）：gross = weak × 自己代理匹配率
 * - 用户决策 2026-05-09：不走 PRD §9.4 团队日封顶，只看健康出局
 * - 70/30 入账（USDT 现货 + ecosystem_credit balance_locked）
 * - 累计 += credited（PRD §9.5：实际团队代理奖）
 * - 用户聚合达标 → 全部 active 一起 expired（用户决策修订 PRD §22.3）
 *
 * 幂等：reward_log idempotentKey = "bizDate:team:userId"
 *       settle_log uk = (job_name, biz_date) 同日重跑 success 直接跳过
 */
public interface IAgencyTeamRewardSettleService {

    SettleResult settle(LocalDate bizDate, Long settleLogId);

    /**
     * 单用户结算（独立 REQUIRES_NEW 事务）。
     *
     * @return credited（USDT 等值），null 表示跳过（弱区为 0 / 冻结 / 非 V1+ / 无 active 矿机）
     * @throws org.springframework.dao.DuplicateKeyException 幂等命中
     */
    BigDecimal settleOneUser(Long userId, BigDecimal leftToday, BigDecimal rightToday,
                             Long volumeDailyRowId,
                             LocalDate bizDate, Long settleLogId);
}
