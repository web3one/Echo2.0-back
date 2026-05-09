-- ============================================================================
-- V20260510_030 XGT 三表（金矿 Phase 1 + Phase 3）
-- ============================================================================
-- 业务变更：
--   $XGT 是金矿系统的"积分代币"。PM 决策 2 锁定"站内积分（不上链）—— 但预留
--   链上字段"（决策 5）。所有 XGT 表都加 chain_address VARCHAR(64) NULL +
--   tx_hash VARCHAR(80) NULL，未来上链时填，不需要 ALTER TABLE。
--
--   XGT 30 天锁仓机制（PRD §13）：
--   - 静态分红的 50% 部分 → 触发新锁仓计划（30 天后释放）
--   - 创世合伙人 49 席 $XGT 分配 → 锁仓计划（按合约规则）
--   - 团队顾问 / 私募 / 生态基金 / 合作伙伴 → 各自锁仓计划
--   - 满 30 天**一次性释放**，不是线性
--
-- 关键设计点：
--   1. **t_xgt_balance 双余额字段**：
--      - balance_locked：锁仓中（不可用）
--      - balance_unlocked：已释放可用 USDT 兑换、提现等
--      total = locked + unlocked。状态机由 t_xgt_lock_plan 驱动。
--
--   2. **t_xgt_lock_plan 4 状态机**（PRD §13）：
--      - locked：锁仓中（current_time < release_at）
--      - releasable：已到期但还没领走（xgt_lock_release_job 跑过后状态）
--      - completed：用户已领（balance_locked - amount → balance_unlocked + amount）
--      - frozen：风控冻结（admin 操作）
--
--   3. **source_type + source_ref_id 幂等**：
--      UNIQUE(source_type, source_ref_id) 防止同一笔静态分红重复创建锁仓计划。
--      例：t_reward_log.id=12345 触发的锁仓 → source_type='static_reward',
--      source_ref_id=12345。重跑结算碰到同 key 即跳过。
--
--   4. **t_xgt_log 流水**：
--      change_type ∈ {lock / release / transfer / admin_adjust}
--      所有 XGT 余额变化都先写一条流水，再 UPDATE balance；做"事件溯源"，
--      余额错乱时可以从流水重算。
--
--   5. **链上字段当前阶段全 NULL**：
--      上链切换时由迁移脚本批量回填 chain_address（与用户钱包绑定）和
--      tx_hash（每条流水关联的链上交易）。
--
-- 配套 Java（Phase 1+3 后续会写）：
--   - domain/TXgtBalance.java + TXgtLockPlan.java + TXgtLog.java
--   - service/IXgtService（lock / release / 余额查询）
--   - quartz xgt_lock_release_job：UTC 00:25 扫 status=locked AND release_at<=NOW()
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_xgt_balance XGT 余额
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_xgt_balance` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance_locked` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '锁仓中余额（不可用）',
    `balance_unlocked` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '已释放可用余额',
    `chain_address` VARCHAR(64) NULL DEFAULT NULL COMMENT '上链地址（决策 5 预留，当前 NULL）',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='XGT 站内积分余额（locked + unlocked，预留上链字段）';

-- ----------------------------------------------------------------------------
-- 2. t_xgt_lock_plan XGT 锁仓计划
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_xgt_lock_plan` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型：static_reward / founder_seat / team_advisor / private_sale / ecosystem_fund / partner',
    `source_ref_id` VARCHAR(64) NOT NULL COMMENT '关联业务表ID（t_reward_log.id 或 t_founder_seat.id 等）',
    `amount_xgt` DECIMAL(28, 8) NOT NULL COMMENT 'XGT 锁仓数量',
    `amount_usd_nominal` DECIMAL(28, 8) NULL COMMENT 'USD 名义价值（PRD §22 第 5 条静态分红健康出局按此计）',
    `locked_at` DATETIME NOT NULL COMMENT '锁仓开始时间',
    `release_at` DATETIME NOT NULL COMMENT '预计释放时间（locked_at + 30 天）',
    `released_at` DATETIME NULL COMMENT '实际释放时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'locked' COMMENT '状态：locked / releasable / completed / frozen',
    `frozen_reason` VARCHAR(255) NULL COMMENT 'frozen 时填',
    `chain_address` VARCHAR(64) NULL DEFAULT NULL COMMENT '上链地址（决策 5 预留）',
    `tx_hash` VARCHAR(80) NULL DEFAULT NULL COMMENT '上链交易哈希（决策 5 预留）',
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source` (`source_type`, `source_ref_id`) COMMENT '同一笔奖励/合约不重复锁',
    KEY `idx_user_status_release` (`user_id`, `status`, `release_at`) COMMENT 'GET /xgt/locks/my 查询路径',
    KEY `idx_status_release` (`status`, `release_at`) COMMENT 'xgt_lock_release_job 扫到期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='XGT 锁仓计划（30 天一次性释放，4 状态机）';

-- ----------------------------------------------------------------------------
-- 3. t_xgt_log XGT 流水
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_xgt_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `change_type` VARCHAR(32) NOT NULL COMMENT '变动类型：lock / release / transfer / admin_adjust',
    `amount_xgt` DECIMAL(28, 8) NOT NULL COMMENT '变动金额（正数=入账，负数=出账）',
    `balance_locked_after` DECIMAL(28, 8) NOT NULL COMMENT '变动后锁仓余额',
    `balance_unlocked_after` DECIMAL(28, 8) NOT NULL COMMENT '变动后可用余额',
    `related_lock_plan_id` BIGINT UNSIGNED NULL COMMENT '关联锁仓计划ID',
    `related_reward_log_id` BIGINT UNSIGNED NULL COMMENT '关联奖励流水ID',
    `chain_address` VARCHAR(64) NULL DEFAULT NULL COMMENT '上链地址（决策 5 预留）',
    `tx_hash` VARCHAR(80) NULL DEFAULT NULL COMMENT '上链交易哈希（决策 5 预留）',
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_create` (`user_id`, `create_time` DESC) COMMENT '用户流水查询',
    KEY `idx_lock_plan` (`related_lock_plan_id`),
    KEY `idx_reward_log` (`related_reward_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='XGT 流水（事件溯源，余额错乱时可重算）';
