-- ============================================================================
-- V20260510_026 AI 矿机三表（金矿 Phase 1 P0 基础）
-- ============================================================================
-- 业务变更：
--   AI 矿机是金矿系统的"权益凭证"——用户没有 active 矿机，所有静态/直推/团队
--   /全网分红一律不发（PRD §22 第 2 条）。本迁移建立矿机相关三张核心表。
--
-- 三张表分工：
--   - t_node_level：矿机等级配置（L1-L4 价格、收益率、封顶），admin 可调
--   - t_node_instance：用户每台矿机实例（每台独立 300% 出局额度，PM 决策 1）
--   - t_node_purchase_log：购买流水（含资产扣款 idempotent_key 防重复扣）
--
-- 关键设计点：
--   1. t_node_instance 内置 accumulated_reward_usdt 与 exit_target_usdt：
--      不另建 t_health_exit_progress 表，每次发奖直接 UPDATE 累计；t_reward_log
--      存完整发奖历史可追溯。每台矿机一行记录 = 决策 1（每台矿机一条进度条）。
--   2. 购买价 / 收益率 / 封顶 / 出局目标在 t_node_instance 都做"快照"——避免后续
--      admin 改 t_node_level 配置影响存量矿机出局计算。
--   3. status 字段 active/expired/frozen/cancelled 与 PRD §3.1 一致。
--   4. L1-L4 默认配置 enabled=0（停用）插入：开发阶段 admin 上线前必须 review，
--      避免误用 PRD 数据生产环境。
--
-- 配套 Java（Phase 1 后续会写）：
--   - domain/TNodeLevel.java + TNodeInstance.java + TNodePurchaseLog.java
--   - service/INodePurchaseService（POST /nodes/buy 入口）
--   - 静态分红定时任务读 t_node_instance WHERE status='active' 发奖
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_node_level 矿机等级配置
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_node_level` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `level_code` VARCHAR(8) NOT NULL COMMENT '等级编码：L1/L2/L3/L4',
    `name_en` VARCHAR(64) NOT NULL COMMENT '英文名（mining-gold React 显示用）',
    `name_zh` VARCHAR(64) NOT NULL COMMENT '中文名',
    `price_usdt` DECIMAL(28, 8) NOT NULL COMMENT '购买价格（USDT）',
    `daily_yield_rate` DECIMAL(10, 6) NOT NULL COMMENT '每日固定收益率（0.0083 = 0.83%）',
    `team_daily_cap_usdt` DECIMAL(28, 8) NOT NULL COMMENT '团队代理奖每日封顶（USDT）',
    `exit_multiplier` DECIMAL(8, 4) NOT NULL DEFAULT 3.0000 COMMENT '健康出局倍数（默认 3.0 = 300%）',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用：0=停用 1=启用',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '展示排序（升序）',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_code` (`level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='AI 矿机等级配置（金矿 Phase 1）';

-- ----------------------------------------------------------------------------
-- 2. t_node_instance 用户矿机实例
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_node_instance` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '所有者用户ID',
    `level_id` BIGINT UNSIGNED NOT NULL COMMENT '矿机等级配置ID（关联 t_node_level.id）',
    `level_code` VARCHAR(8) NOT NULL COMMENT '等级编码冗余（L1/L2/L3/L4，便于查询不连表）',

    -- 购买时快照（避免后续 admin 改 t_node_level 影响存量矿机）
    `price_usdt` DECIMAL(28, 8) NOT NULL COMMENT '购买价格快照',
    `daily_yield_rate` DECIMAL(10, 6) NOT NULL COMMENT '每日收益率快照',
    `team_daily_cap_usdt` DECIMAL(28, 8) NOT NULL COMMENT '团队日封顶快照',
    `exit_target_usdt` DECIMAL(28, 8) NOT NULL COMMENT '健康出局目标 = price × exit_multiplier 快照',

    -- 健康出局累计（决策 1：每台矿机独立 300%）
    `accumulated_reward_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '已计入健康出局的累计毛额（按 100% 计 PRD §22 第 5 条）',
    `accumulated_static_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '其中：静态分红累计（毛额）',
    `accumulated_referral_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '其中：直推奖累计（毛额）',
    `accumulated_team_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '其中：团队代理奖累计（毛额）',

    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / expired / frozen / cancelled',
    `activated_at` DATETIME NULL COMMENT '激活时间（用于"同级取最早激活"算法 PRD §22 第 6 条）',
    `expired_at` DATETIME NULL COMMENT '出局时间（status 转 expired 时写入）',
    `frozen_at` DATETIME NULL COMMENT '冻结时间',
    `frozen_reason` VARCHAR(255) NULL COMMENT '冻结原因（admin 填）',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（购买时刻）',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`) COMMENT '查用户的 active 矿机',
    KEY `idx_user_level_status` (`user_id`, `level_code`, `status`) COMMENT '当前有效权益矿机选择 L4>L3>L2>L1',
    KEY `idx_status_activated` (`status`, `activated_at`) COMMENT '同级取最早激活'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='用户 AI 矿机实例（每台独立 300% 出局额度）';

-- ----------------------------------------------------------------------------
-- 3. t_node_purchase_log 矿机购买流水
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_node_purchase_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '购买用户ID',
    `node_instance_id` BIGINT UNSIGNED NOT NULL COMMENT '生成的矿机实例ID',
    `level_code` VARCHAR(8) NOT NULL COMMENT '等级编码（冗余）',
    `price_usdt` DECIMAL(28, 8) NOT NULL COMMENT '实际扣款金额',
    `payment_currency` VARCHAR(16) NOT NULL DEFAULT 'USDT' COMMENT '支付币种',
    `asset_tx_id` BIGINT NULL COMMENT '关联资产模块扣款流水ID（echo2 主资产账本）',
    `placement_side` VARCHAR(8) NOT NULL DEFAULT 'auto' COMMENT '双轨放置：auto / left / right',
    `idempotent_key` VARCHAR(64) NOT NULL COMMENT '幂等键（客户端生成 UUID 或后端 user_id+timestamp 防重复扣）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent` (`idempotent_key`),
    KEY `idx_user_create` (`user_id`, `create_time` DESC),
    KEY `idx_node_instance` (`node_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='AI 矿机购买流水（含幂等防重复扣款）';

-- ----------------------------------------------------------------------------
-- 4. L1-L4 默认配置（PRD §3.1，enabled=0 等 admin review 后再开）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `t_node_level`
    (`level_code`, `name_en`, `name_zh`, `price_usdt`, `daily_yield_rate`, `team_daily_cap_usdt`, `exit_multiplier`, `enabled`, `sort`, `remark`)
VALUES
    ('L1', 'Explorer',    '探索者',     500.00000000,   0.008300, 500.00000000,   3.0000, 0, 1, 'PRD §3.1 默认值，admin 上线前必须 review 启用'),
    ('L2', 'Pioneer',     '拓荒者',     2000.00000000,  0.010000, 2000.00000000,  3.0000, 0, 2, 'PRD §3.1 默认值'),
    ('L3', 'Visionary',   '远见者',     5000.00000000,  0.012500, 5000.00000000,  3.0000, 0, 3, 'PRD §3.1 默认值'),
    ('L4', 'Trailblazer', '开路先锋',   15000.00000000, 0.016700, 15000.00000000, 3.0000, 0, 4, 'PRD §3.1 默认值');
