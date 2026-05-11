-- ============================================================================
-- V20260515_039 金矿子钱包 + 提现单 + 每日手续费聚合 + 4 项参数配置
-- ============================================================================
-- 业务变更（B 路线第一组）：
--   1. 金矿独立子钱包（PRD §12.2 提现费分成 / figma assetHub 设计）：
--      奖励发放从"直接进现货 USDT 余额"改为"进 t_gold_wallet"；用户从金矿
--      主动 [Withdraw USDT] 时扣 5% 手续费再转 95% 到现货 t_app_asset。
--
--   2. 5% 提现费拆解（PRD §12.2 第 5 条权益 + figma founderPerk5）：
--      - 1% → t_pool_account.account_type='founder_share' 创世 49 等分池
--      - 4% → t_pool_account.account_type='platform_fee' 平台收入
--      拆比例从 sys_config 读，admin 可改。
--
--   3. 每日手续费聚合（PRD §11.2 / §12.3 分红基数）：
--      daily_cex_fee_base = spot_fee + contract_fee（PRD §11.2 明确不含 C2C
--      / 提现费 / 充值费 / 矿机销售额 / 上币费 / 场外 / 储备金 / 人工调整）
--      cron daily_fee_summary_job UTC 00:00 聚合前一天 → 落 t_daily_fee_summary
--      → 为 B 路线第二组 V4/V5 全网分红 + 创世手续费分红 cron 提供数据源。
--
-- 关键设计点：
--   1. t_gold_wallet 只存 USDT：XGT 余额仍走现有 t_xgt_balance，避免双写。
--      mining-gold UserState.balanceUSDT 接 t_gold_wallet.usdt_balance；
--      UserState.xgtWithdrawable/xgtLocked 接 t_xgt_balance（已实现）。
--
--   2. 切换日语义：本迁移上线那天起，新发奖走 t_gold_wallet；历史已发到现货
--      tb_app_wallet_record (type=71/72/73) 的奖励留在现货余额不回填（用户决策）。
--      用户视角：老奖励在 echo2-h5 现货页，新奖励在 mining-gold 资产中心。
--
--   3. t_gold_wallet_log 流水按 change_type 区分来源；不复用 tb_app_wallet_record
--      （后者是现货流水），各管各的减少耦合。提现的 95% 进现货时再写一条
--      tb_app_wallet_record（GOLD_TO_SPOT_IN，本迁移在 RecordEnum 加 type=79）。
--
--   4. t_gold_withdraw_order 字段含 fee_rate_snap / founder_share_rate_snap：
--      下单时快照费率，sys_config 后续改了不影响历史单审计。
--
--   5. t_daily_fee_summary biz_date PRIMARY KEY：天然幂等，cron 重跑 UPSERT。
--
--   6. sys_config 配置项（4 项，admin 在"系统管理→参数设置"页可改）：
--      - gold.withdraw.fee_rate              = 0.05 （5%）
--      - gold.withdraw.founder_share_rate    = 0.01 （1% 给创世，剩 4% 进平台）
--      - gold.pool.warning_ratio             = 0.80 （应发负债/链上储备 >80% 告警）
--      - gold.fee.daily_summary_enabled      = true
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_gold_wallet 金矿子钱包（仅 USDT，XGT 走 t_xgt_balance）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_gold_wallet` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `usdt_balance` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT 'USDT 可提现余额（金矿子钱包）',
    `usdt_total_in` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '累计入账 USDT（审计/对账）',
    `usdt_total_out` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '累计出账 USDT（提现+人工调整）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='金矿子钱包 USDT 余额（PRD §12.2 / figma assetHub 设计）';

-- ----------------------------------------------------------------------------
-- 2. t_gold_wallet_log 金矿子钱包流水（事件溯源）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_gold_wallet_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `change_type` VARCHAR(32) NOT NULL COMMENT '变动类型：reward_static / reward_referral / reward_team / reward_agency_fee / reward_founder_fee / withdraw_out / refund / admin_adjust',
    `amount_usdt` DECIMAL(28, 8) NOT NULL COMMENT '变动金额（正=入账，负=出账）',
    `balance_before` DECIMAL(28, 8) NOT NULL COMMENT '变动前余额',
    `balance_after` DECIMAL(28, 8) NOT NULL COMMENT '变动后余额',
    `biz_ref_type` VARCHAR(32) NULL COMMENT '关联业务类型：reward_log / withdraw_order / admin',
    `biz_ref_id` VARCHAR(64) NULL COMMENT '关联业务ID（reward_log.id / withdraw_order.id 等）',
    `idempotent_key` VARCHAR(128) NULL COMMENT '幂等键（来自上游业务，防重复入账）',
    `operator_admin_id` BIGINT NULL COMMENT 'admin 调账时填',
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idem` (`idempotent_key`),
    KEY `idx_user_create` (`user_id`, `create_time` DESC),
    KEY `idx_change_type_create` (`change_type`, `create_time` DESC),
    KEY `idx_biz_ref` (`biz_ref_type`, `biz_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='金矿子钱包流水（事件溯源，余额错乱时可重算）';

-- ----------------------------------------------------------------------------
-- 3. t_gold_withdraw_order 金矿→现货提现单（用户主动发起）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_gold_withdraw_order` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL,
    `asset_type` VARCHAR(8) NOT NULL DEFAULT 'USDT' COMMENT '资产类型：USDT（本期仅 USDT；XGT 走锁仓释放，无 5% 提现费）',
    `gross_amount` DECIMAL(28, 8) NOT NULL COMMENT '申请提现毛额',
    `fee_rate_snap` DECIMAL(8, 6) NOT NULL COMMENT '下单时快照的提现总费率（如 0.05）',
    `fee_amount` DECIMAL(28, 8) NOT NULL COMMENT '总手续费（gross × fee_rate）',
    `founder_share_rate_snap` DECIMAL(8, 6) NOT NULL COMMENT '下单时快照的创世分成费率（如 0.01）',
    `founder_share_amount` DECIMAL(28, 8) NOT NULL COMMENT '进创世池金额（gross × founder_share_rate）',
    `platform_fee_amount` DECIMAL(28, 8) NOT NULL COMMENT '进平台池金额（fee_amount - founder_share_amount）',
    `net_amount` DECIMAL(28, 8) NOT NULL COMMENT '用户实收（gross - fee_amount）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending / completed / cancelled / failed',
    `fund_password_verified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '资金密码已验证',
    `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键 <user_id>:<request_uuid>',
    `spot_wallet_record_id` BIGINT NULL COMMENT '95% 入现货时写的 tb_app_wallet_record 流水ID',
    `gold_wallet_log_id` BIGINT NULL COMMENT '金矿子钱包出账的 t_gold_wallet_log 流水ID',
    `failed_reason` VARCHAR(255) NULL,
    `completed_at` DATETIME NULL,
    `client_ip` VARCHAR(64) NULL,
    `client_user_agent` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idem` (`idempotent_key`),
    KEY `idx_user_status_create` (`user_id`, `status`, `create_time` DESC),
    KEY `idx_status_create` (`status`, `create_time` DESC) COMMENT 'admin 列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='金矿子钱包→现货 USDT 提现单（扣 5% 手续费 1% 创世池+4% 平台）';

-- ----------------------------------------------------------------------------
-- 4. t_daily_fee_summary 每日手续费聚合（V4/V5 全网分红 + 创世分红 cron 数据源）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_daily_fee_summary` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_date` DATE NOT NULL COMMENT '业务日（UTC）',
    `spot_fee_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '现货手续费 USDT',
    `contract_fee_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '合约手续费 USDT',
    `dividend_base_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '分红基数（PRD §11.2 = spot + contract，不含 C2C/提现/充值/矿机销售）',
    `gold_withdraw_fee_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '金矿提现费总额（独立统计，不进分红基数）',
    `gold_withdraw_founder_share_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '其中归创世池部分',
    `gold_withdraw_platform_fee_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '其中归平台部分',
    `extra_data` TEXT NULL COMMENT 'JSON：明细拆分（按 symbol / pair / 用户级 VIP 折扣等）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'settled' COMMENT '状态：settled / failed',
    `settle_log_id` BIGINT UNSIGNED NULL COMMENT '关联 t_settle_log',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_date` (`biz_date`),
    KEY `idx_biz_date_desc` (`biz_date` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='每日手续费聚合（PRD §11.2 daily_cex_fee_base + 金矿提现费独立统计）';

-- ----------------------------------------------------------------------------
-- 5. 注入 4 个 sys_config 配置项（admin "系统管理→参数设置"页面可改）
-- ----------------------------------------------------------------------------
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '金矿提现总费率', 'gold.withdraw.fee_rate', '0.05', 'Y', 'admin', NOW(), 'PRD §12.2 提现费 5%；admin 改后下一笔提现单生效'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'gold.withdraw.fee_rate');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '金矿提现创世分成费率', 'gold.withdraw.founder_share_rate', '0.01', 'Y', 'admin', NOW(), 'PRD §12.2 创世权益第 5 条：提现费 1% 永久 49 等分；剩余进平台'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'gold.withdraw.founder_share_rate');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '金矿资金池告警阈值', 'gold.pool.warning_ratio', '0.80', 'Y', 'admin', NOW(), '应发负债 / 链上 USDT 储备 > 该值时告警；PRD §21.1 风控'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'gold.pool.warning_ratio');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '金矿日手续费聚合开关', 'gold.fee.daily_summary_enabled', 'true', 'Y', 'admin', NOW(), 'false 时 daily_fee_summary_job 跳过；用于灰度/故障应急'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'gold.fee.daily_summary_enabled');
