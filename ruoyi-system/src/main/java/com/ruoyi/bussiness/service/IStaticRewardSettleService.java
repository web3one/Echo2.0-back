package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 静态分红结算服务（金矿 daily_static_reward_job 实现）。
 *
 * 截断算法：按"用户聚合制"（2026-05-09 用户决策修订 PRD §4 + §22.3）：
 * - 出局额 = 该用户当前所有 active 矿机中最高级 priceUsdt × 300%
 * - 累计 = 该用户所有 active 矿机 accumulated_reward_usdt 之和
 * - 升级时旧累计连续计入新额度（不清零）
 * - 同价矿机同时唯一，不同 level 共存
 * - 用户聚合达标 → 该用户所有 active 矿机一起 expired
 * - 重购：旧 active 全 expired 后，新购矿机重新开启周期（自然实现：SUM 仅算 active）
 */
public interface IStaticRewardSettleService {

    /** 整个静态分红日的批量结算入口（外层不开事务，按用户独立子事务）。 */
    SettleResult settle(LocalDate bizDate, Long settleLogId);

    /**
     * 单用户聚合发奖（独立 REQUIRES_NEW 事务）。
     *
     * 一个用户的所有 active 矿机一起结算：
     * - 校验冻结
     * - 算用户聚合 sumAccumulated / aggExitTarget / aggRemainingQuota
     * - 按 levelCode desc + activatedAt asc 顺序循环每台矿机发奖（高级先消耗 quota）
     * - 每台 INSERT 一行 t_reward_log（idempotent_key=bizDate:static:userId:nodeInstanceId）
     * - 累计 += gross（按 100% 毛额）
     * - 全部发完后判用户聚合是否达标 → 一起 expired
     *
     * @return 该用户本轮聚合 credited（USDT 等值名义价值）；null 表示跳过（冻结/无 active 矿机/聚合配额已耗尽）
     * @throws org.springframework.dao.DuplicateKeyException 幂等命中（任意一台矿机的 reward_log UK 冲突），调用方应 catch 后跳过
     */
    BigDecimal settleOneUser(Long userId, LocalDate bizDate, Long settleLogId);
}
