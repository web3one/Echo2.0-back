-- ============================================================================
-- V20260510_032 创世合伙人 + 资金池三表（金矿 Phase 3 + 监控）
-- ============================================================================
-- 业务变更：
--   1. **创世合伙人 49 席**（PRD §12，决策 4 直接扣不审核）：
--      单席 200,000 USDT，先到先得；用户点购→校验资金密码→现货 USDT 余额
--      ≥200k→行级锁占第一个 available 席→扣款→写流水→触发 V5 提升 + XGT
--      锁仓 + 加入分红名单。每位创世人享有 PRD §12 列出的 6 大权益。
--
--   2. **资金池监控** t_pool_account（记忆 phase0 必埋的 3 个监控点）：
--      - 金矿钱包总余额监控（vs 公司链上 USDT 储备 80% 红线）
--      - 手续费回流到独立账户（日后补窟窿储备）
--      - 每日 pending_obligation 落库（应发但未发负债快照）
--
-- 关键设计点：
--   1. **t_founder_seat 预创建 49 行**：
--      迁移末尾 INSERT 49 行 (seat_no=1..49, status='available')。用户购买
--      时按 seat_no ASC 占第一个 available；这样不会出现 seat_no 跳号或并发
--      多卖。
--
--   2. **决策 3 影响 t_founder_seat schema 瘦身**：
--      原 PRD 设计每席享"V5 授权 1 名"权益，schema 应有 authorized_v5_user_id
--      / authorized_at / revoke_at / transfer_log。决策 3 改为"统一后台修改
--      代理等级"，所以这些字段全部不要——客服直接走 admin 改 t_agent_status
--      即可，t_agent_level_change_log 落 source='admin_direct' 流水。
--
--   3. **决策 4 fund_password_verified 必填字段**：
--      t_founder_purchase_log.fund_password_verified TINYINT 强制 1，
--      表示扣款前已通过资金密码二次验证。审计时必查这个字段=1。
--
--   4. **idempotent_key 幂等**：
--      格式：<user_id>:<seat_no>:<request_uuid>
--      重复点击只能扣一次。
--      注：注释/COMMENT 里禁用 dollar+大括号 写法（被 Flyway 当占位符替换会启动失败），统一用 <...>。
--
--   5. **资金池 t_pool_account 设计**：
--      不存"实时余额"——而是每日定时任务做"快照"。
--      account_type ∈:
--      - gold_wallet：金矿钱包总余额（= sum(t_xgt_balance) + USDT 等）
--      - exchange_main：交易所主钱包 USDT 储备
--      - fee_pool：手续费回流账户余额（独立监控）
--      - pending_obligation：当日新增"应发未发"快照（金矿对用户的负债）
--
-- 配套 Java（Phase 3 后续会写）：
--   - domain/TFounderSeat.java + TFounderPurchaseLog.java + TPoolAccount.java
--   - service/IFounderPurchaseService（POST /founder/purchase 入口）
--   - quartz pool_snapshot_job（每日凌晨拍快照，监控用）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_founder_seat 创世 49 席
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_founder_seat` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `seat_no` INT NOT NULL COMMENT '席位号 1-49',
    `status` VARCHAR(16) NOT NULL DEFAULT 'available' COMMENT '状态：available / owned / frozen',
    `owner_user_id` BIGINT NULL COMMENT '所有者用户ID（available 时为 NULL）',
    `price_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 200000.00000000 COMMENT '席位价格（PRD §12：200k）',
    `paid_at` DATETIME NULL COMMENT '购买完成时间',
    `xgt_lock_plan_id` BIGINT UNSIGNED NULL COMMENT '关联的 XGT 锁仓计划ID（席位享 $XGT 总供应 10%/49 配额）',
    `frozen_at` DATETIME NULL,
    `frozen_reason` VARCHAR(255) NULL,
    `frozen_by_admin_id` BIGINT NULL,
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_seat_no` (`seat_no`),
    UNIQUE KEY `uk_owner` (`owner_user_id`) COMMENT '一个用户最多占一席（业务可放宽则改 KEY）',
    KEY `idx_status_seat` (`status`, `seat_no`) COMMENT '购买时按 seat_no ASC 占第一个 available'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='创世合伙人 49 席（决策 3：去 V5 授权字段 / 决策 4：直接扣不审核）';

-- ----------------------------------------------------------------------------
-- 2. t_founder_purchase_log 创世席位购买流水
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_founder_purchase_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL,
    `seat_no` INT NOT NULL COMMENT '占到的席位号',
    `amount_usdt` DECIMAL(28, 8) NOT NULL COMMENT '实际扣款金额（200k）',
    `payment_currency` VARCHAR(16) NOT NULL DEFAULT 'USDT',
    `asset_tx_id` BIGINT NULL COMMENT '关联现货资产扣款流水ID',
    `fund_password_verified` TINYINT(1) NOT NULL COMMENT '资金密码已验证（决策 4 必填 1）',
    `idempotent_key` VARCHAR(96) NOT NULL COMMENT '幂等键 <user_id>:<seat_no>:<request_uuid>',
    `client_ip` VARCHAR(64) NULL COMMENT '客户端 IP（审计用）',
    `client_user_agent` VARCHAR(255) NULL COMMENT '客户端 UA（审计用）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent` (`idempotent_key`),
    KEY `idx_user_create` (`user_id`, `create_time` DESC),
    KEY `idx_seat` (`seat_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='创世席位购买流水（200k 大额必须含 fund_password_verified=1 + IP/UA 审计）';

-- ----------------------------------------------------------------------------
-- 3. t_pool_account 资金池监控（每日快照）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_pool_account` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_type` VARCHAR(32) NOT NULL COMMENT '账户类型：gold_wallet / exchange_main / fee_pool / pending_obligation',
    `snapshot_date` DATE NOT NULL COMMENT '快照业务日',
    `balance_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT 'USDT 折算余额',
    `extra_data` TEXT NULL COMMENT 'JSON：账户类型相关明细（如 fee_pool 的 spot/contract/c2c 拆分）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_date` (`account_type`, `snapshot_date`),
    KEY `idx_type_date_desc` (`account_type`, `snapshot_date` DESC) COMMENT 'admin 报表查最近 N 天'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='资金池每日快照（金矿钱包/交易所主钱包/手续费池/应发未发负债 监控用）';

-- ----------------------------------------------------------------------------
-- 4. 预创建 49 个创世席位（status='available'）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `t_founder_seat` (`seat_no`, `status`, `price_usdt`)
SELECT n, 'available', 200000.00000000
FROM (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
    UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40
    UNION ALL SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45
    UNION ALL SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49
) seats;
