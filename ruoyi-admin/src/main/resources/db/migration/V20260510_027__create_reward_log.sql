-- ============================================================================
-- V20260510_027 奖励流水 t_reward_log（金矿 Phase 1 P0 核心）
-- ============================================================================
-- 业务变更：
--   金矿系统所有奖励发放都先落本表一行，再同步去更新 t_xgt_balance /
--   t_ecosystem_credit_balance / t_node_instance.accumulated_reward_usdt /
--   主资产账户。本表是对账与幂等的核心枢纽。
--
-- 关键设计点：
--   1. **gross_amount_usdt vs usdt_credited 双字段**：
--      gross 是按 100% 计入健康出局的"毛额"（PRD §22 第 5 条）。
--      usdt_credited 是健康出局截断后实际到账。差额 = exit_truncated_amount。
--      例：本应发 100，矿机剩余出局额度 30，则 gross=100, credited=30,
--      truncated=70（这 70 永久消失，不能转给下台矿机 PRD §22 第 5 条）。
--
--   2. **分账由 reward_type 决定**（PRD §2.3 + 业务修订 2026-05-13）：
--      static          → 50% USDT (usdt_credited) + 50% XGT 锁仓 (xgt_credited)
--      referral        → 100% USDT (usdt_credited)，无 XGT / ecosystem_credit
--      team            → 70% USDT (usdt_credited) + 30% credit (eco_credit_amount)
--      agency_fee / founder_fee → 100% USDT (usdt_credited)
--      xgt_unlock      → 100% XGT (xgt_credited)
--      eco_credit_unlock → 100% USDT (usdt_credited，credit 解锁后转 USDT)
--
--   3. **related_node_instance_id 绑定矿机**（决策 1）：
--      static / referral / team 必须填该字段——挂在哪台矿机上发的奖。
--      agency_fee / founder_fee / xgt_unlock 可空（不计入健康出局）。
--      counted_in_health_exit 字段同时标记是否计入。
--
--   4. **idempotent_key 幂等**：
--      格式：<settle_log_id>:<reward_type>:<user_id>:<node_instance_id>
--      6 个定时任务重试时碰到重复 key 直接跳过，已发的不会发第二次。
--      注：注释/COMMENT 里禁用 dollar+大括号 写法（被 Flyway 当占位符替换会启动失败），统一用 <...>。
--
--   5. **source_user_id 追溯奖金来源**：
--      直推奖：source_user_id = 触发本次奖励的下级用户ID
--      团队奖：source_user_id 可为 NULL（按整个弱区结算，没有单一来源）
--      静态分红：source_user_id = NULL
--
--   6. **status 字段**：
--      settled  = 静态/直推/团队/全网/创世分红已结算入账
--      released = XGT 锁仓 30 天后释放
--      unlocked = ecosystem_credit 解锁转 USDT
--
-- 配套 Java（Phase 1 后续会写）：
--   - domain/TRewardLog.java
--   - service/IRewardSettleService（核心结算入口，所有定时任务调它）
--   - 6 个 quartz job 在 settle 前查 t_settle_log 当日 status=success 跳过；
--     发奖时 INSERT t_reward_log 用 idempotent_key UK 防重复，再 UPDATE
--     t_node_instance.accumulated_reward_usdt += gross 同事务。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_reward_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '受益人用户ID',
    `reward_type` VARCHAR(32) NOT NULL COMMENT '奖励类型：static / referral / team / agency_fee / founder_fee / xgt_unlock / eco_credit_unlock',

    -- 金额三件套
    `gross_amount_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '毛额（USDT 计价）—— 健康出局按此计 100%',
    `usdt_credited` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '实际到账 USDT（健康出局截断后）',
    `exit_truncated_amount` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '被健康出局截断作废的金额',

    -- XGT 入账
    `xgt_credited` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT 'XGT 入账数量（静态分红 50% / xgt_unlock 用）',
    `xgt_lock_plan_id` BIGINT UNSIGNED NULL COMMENT '触发 XGT 锁仓时关联的锁仓计划ID',

    -- ecosystem_credit 入账
    `eco_credit_amount` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '生态额度入账（团队代理奖 30% 用；直推奖为 0）',

    -- 来源追溯
    `related_node_instance_id` BIGINT UNSIGNED NULL COMMENT '绑定矿机ID（决策 1：static/referral/team 必填）',
    `source_user_id` BIGINT NULL COMMENT '奖金来源用户（直推奖=触发下级；其他可空）',
    `source_amount` DECIMAL(28, 8) NULL COMMENT '产生本奖的下级业绩或原始金额（追溯用）',

    -- 业务标记
    `counted_in_health_exit` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否计入健康出局（PRD §2.3）：static/referral/team=1，其他=0',
    `agent_match_rate` DECIMAL(10, 6) NULL COMMENT '团队代理奖时受益人匹配率快照（5%=0.05）',
    `agent_level_snapshot` VARCHAR(8) NULL COMMENT '受益人当时代理等级快照（V0-V5）',

    -- 结算 & 幂等
    `settle_log_id` BIGINT UNSIGNED NULL COMMENT '关联的定时任务执行记录（t_settle_log.id）',
    `biz_date` DATE NULL COMMENT '业务日（结算所属的日期）',
    `idempotent_key` VARCHAR(160) NOT NULL COMMENT '幂等键：<settle_log_id>:<type>:<user_id>:<node_instance_id>',

    -- 状态机
    `status` VARCHAR(16) NOT NULL DEFAULT 'settled' COMMENT '状态：settled / released / unlocked',
    `released_at` DATETIME NULL COMMENT 'XGT 锁仓释放或 credit 解锁时间',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent` (`idempotent_key`),
    KEY `idx_user_type_create` (`user_id`, `reward_type`, `create_time` DESC) COMMENT 'GET /rewards/daily 主查询路径',
    KEY `idx_user_node` (`user_id`, `related_node_instance_id`) COMMENT '矿机筛选查奖励',
    KEY `idx_settle_log` (`settle_log_id`) COMMENT '按结算批次回溯',
    KEY `idx_user_biz_date` (`user_id`, `biz_date`) COMMENT '按业务日查报表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='金矿奖励流水（结算/对账/幂等核心枢纽）';
