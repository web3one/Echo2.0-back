-- ============================================================================
-- V20260510_031 ecosystem_credit 二表（金矿 Phase 3）
-- ============================================================================
-- 业务变更：
--   ecosystem_credit（生态额度）来自直推奖 + 团队代理奖的 30% 部分（PRD §10）。
--   不能直接当 USDT 使用，必须"解锁"才能转为可用余额。
--
--   两种解锁方式：
--   - A. **3 倍交易量解锁**：累积交易量达到 ecosystem_credit × 3 时自动解锁
--        例：用户有 1000 credit，需累计交易 3000 USDT 等值（现货+合约）即解锁
--   - B. **锁等值 XGT 30 天解锁**：把同等数量的 XGT 锁仓 30 天即解锁
--
--   解锁后 ecosystem_credit 转为 USDT 可用余额（注意是按 1:1 转 USDT，不是
--   重新发奖；这是用户已经赚到的钱，只是延迟兑现）。
--
-- 关键设计点：
--   1. **t_ecosystem_credit_balance 双余额**：
--      - balance_locked：累计未解锁的 credit（直推/团队的 30% 部分进这里）
--      - balance_unlocked：已解锁未划出的 credit（解锁后转 USDT 时从这里扣）
--      实际上 balance_unlocked 一般会立即转 USDT，长期不会留太多。
--
--   2. **t_ecosystem_credit_unlock_log**：
--      每次解锁请求一行（不是每次 credit 入账一行；入账走 t_reward_log 即可）。
--      unlock_type='trade_volume' 时 trade_volume_required + completed 跟踪
--      进度；'xgt_lock' 时关联 xgt_lock_plan_id。
--
--   3. **3 倍交易量统计粒度**：
--      "累计交易量"是从用户提交解锁请求那刻起算的、之后产生的现货+合约
--      成交量。不是历史已有交易量。这是 PRD §10 的隐含规则——避免用户发起
--      解锁就立刻满足条件套利。
--      （实施时由 quartz 定时扫 status='in_progress' 的解锁请求，对比
--       t_trade_log 中 started_at 之后的累计成交量，达标则置 completed
--       并把 amount_credit 转入 USDT）
--
--   4. **状态机**：
--      in_progress → completed（成功解锁）
--      in_progress → cancelled（用户取消 / xgt_lock 解锁路径锁仓被冻结）
--
-- 配套 Java（Phase 3 后续会写）：
--   - domain/TEcosystemCreditBalance.java + TEcosystemCreditUnlockLog.java
--   - service/IEcosystemCreditService（POST /ecosystem-credit/unlock 入口）
--   - quartz 定时扫 in_progress 解锁请求（小时级即可，不需要分钟级）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_ecosystem_credit_balance 余额
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ecosystem_credit_balance` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance_locked` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '未解锁的 credit 累计（直推/团队 30% 部分进这里）',
    `balance_unlocked` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '已解锁未划出（一般立即转 USDT 后归零）',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='ecosystem_credit 余额（直推/团队 30% 累积，需解锁后才能用）';

-- ----------------------------------------------------------------------------
-- 2. t_ecosystem_credit_unlock_log 解锁请求
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ecosystem_credit_unlock_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `unlock_type` VARCHAR(16) NOT NULL COMMENT '解锁路径：trade_volume / xgt_lock',
    `amount_credit` DECIMAL(28, 8) NOT NULL COMMENT '本次申请解锁的 credit 数量',

    -- A 路径：交易量
    `trade_volume_required` DECIMAL(28, 8) NULL COMMENT 'A 路径需累计交易量（= amount × 3）',
    `trade_volume_completed` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT 'A 路径当前已完成交易量',

    -- B 路径：XGT 锁仓
    `xgt_lock_plan_id` BIGINT UNSIGNED NULL COMMENT 'B 路径关联的锁仓计划ID',

    `status` VARCHAR(16) NOT NULL DEFAULT 'in_progress' COMMENT '状态：in_progress / completed / cancelled',
    `started_at` DATETIME NOT NULL COMMENT '请求开始时间（A 路径交易量从此刻起算）',
    `completed_at` DATETIME NULL COMMENT '完成时间',
    `cancel_reason` VARCHAR(255) NULL,
    `usdt_credited` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '完成时转入 USDT 的金额（status=completed 时填）',
    `related_reward_log_id` BIGINT UNSIGNED NULL COMMENT 'completed 后会写一条 t_reward_log type=eco_credit_unlock，此处反向关联',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_started` (`status`, `started_at`) COMMENT '定时扫 in_progress 检查交易量进度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='ecosystem_credit 解锁请求（trade_volume / xgt_lock 两路 + 进度追踪）';
