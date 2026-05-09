-- ============================================================================
-- V20260510_028 代理体系四表 + V0-V5 默认配置（金矿 Phase 1 + Phase 2）
-- ============================================================================
-- 业务变更：
--   建立"代理等级 + 申请审核 + 等级变更流水"三段结构。Phase 1 P0 直推奖只
--   依赖 t_agent_status（看代理等级算匹配率），但等级修改的两条路径
--   （决策 3）都涉及多张表：
--
--   - 通道 1：用户 H5 申请审核
--     POST /agency/apply → t_agent_application 写一行 pending
--     admin 审核 → 通过则 UPDATE t_agent_status + 写 t_agent_level_change_log
--                  拒绝则 UPDATE t_agent_application.status=rejected
--
--   - 通道 2：客服后台直接修改
--     admin "改用户代理等级" → 直接 UPDATE t_agent_status + 写 t_agent_level_change_log
--                            （source='admin_direct'，不经过 t_agent_application）
--
--   两条路径都强制落 t_agent_level_change_log，便于审计。
--
-- 关键设计点：
--   1. **V0 也是一个等级**：t_agent_status 默认 agent_level='V0'，新用户注册后
--      由 TAppUserServiceImpl 写一行（V0 = 普通用户，匹配率 0%）。
--   2. **status='frozen' 的影响**（追问 A）：6 个定时任务发奖前必查
--      `SELECT status FROM t_agent_status WHERE user_id=? AND status='frozen'`
--      命中则跳过本次发奖；下线业绩照常累计到 t_binary_volume_*。
--   3. **t_agent_level 配置可调**：V0-V5 默认按 PRD §8.1 插入 enabled=1，
--      admin 可后台改匹配率/分红率/升级条件。
--   4. **t_agent_application 不存等级配置快照**：审核时实时读 t_agent_level
--      对比用户实际数据（直推数 / 累计业绩）；这样 admin 调过 t_agent_level
--      后，存量 pending 申请会按新条件审核（业务希望如此）。
--
-- 配套 Java（Phase 1+2 后续会写）：
--   - domain/TAgentStatus.java + TAgentLevel.java + TAgentApplication.java + TAgentLevelChangeLog.java
--   - service/IAgencyApplyService（POST /agency/apply）
--   - service/IAgencyReviewService（admin 审核 + 客服直改 双入口）
--   - 静态/团队奖结算前必查 t_agent_status.status='frozen' 跳过
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_agent_status 用户当前代理等级
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_status` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `agent_level` VARCHAR(8) NOT NULL DEFAULT 'V0' COMMENT '当前代理等级：V0=普通用户 / V1-V5',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / frozen（追问 A：frozen 时所有 6 类奖励停发）',
    `frozen_at` DATETIME NULL COMMENT '冻结时间',
    `frozen_reason` VARCHAR(255) NULL COMMENT '冻结原因',
    `frozen_by_admin_id` BIGINT NULL COMMENT '冻结操作人（admin user id）',
    `promoted_at` DATETIME NULL COMMENT '当前等级生效时间',
    `last_change_log_id` BIGINT UNSIGNED NULL COMMENT '最近一次等级变更记录ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_level_status` (`agent_level`, `status`) COMMENT 'V4/V5 全网分红查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='用户当前代理等级（金矿）';

-- ----------------------------------------------------------------------------
-- 2. t_agent_level 代理等级配置
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_level` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `level_code` VARCHAR(8) NOT NULL COMMENT '等级编码：V0/V1/V2/V3/V4/V5',
    `name_en` VARCHAR(64) NOT NULL,
    `name_zh` VARCHAR(64) NOT NULL,
    `match_rate` DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '团队代理奖匹配率（0.05 = 5%）',
    `global_dividend_rate` DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '全网手续费分红率（V4=0.005 / V5=0.01，其他=0）',
    `min_active_node_value_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '最低 active 矿机金额',
    `min_direct_referral_count` INT NOT NULL DEFAULT 0 COMMENT '最低直推数',
    `min_left_volume_total` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '最低左区累计业绩',
    `min_right_volume_total` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '最低右区累计业绩',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=可申请该等级）',
    `sort` INT NOT NULL DEFAULT 0,
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_code` (`level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='代理等级配置（V0-V5，可 admin 调整）';

-- ----------------------------------------------------------------------------
-- 3. t_agent_application 代理申请审核（决策 3 通道 1）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_application` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `from_level` VARCHAR(8) NOT NULL COMMENT '当前等级（提交时的快照）',
    `target_level` VARCHAR(8) NOT NULL COMMENT '目标等级（V1-V5）',
    `reason_user` VARCHAR(500) NULL COMMENT '用户填的申请理由',
    `proof_urls` TEXT NULL COMMENT '证明材料 URL 列表（JSON 数组）',

    -- 提交时快照（便于审核时对比 admin 调整 t_agent_level 之前/之后是否仍达标）
    `direct_referral_count_snap` INT NOT NULL DEFAULT 0 COMMENT '提交时直推数快照',
    `left_volume_total_snap` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '提交时左区累计快照',
    `right_volume_total_snap` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '提交时右区累计快照',
    `active_node_value_snap` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '提交时 active 矿机总值快照',

    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending / approved / rejected / cancelled',
    `review_admin_id` BIGINT NULL COMMENT '审核人 admin user id',
    `review_at` DATETIME NULL,
    `review_remark` VARCHAR(500) NULL COMMENT '审核备注（拒绝原因 / 通过备注）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_status_create` (`user_id`, `status`, `create_time` DESC),
    KEY `idx_status_create` (`status`, `create_time`) COMMENT 'admin 审核列表（pending 升序）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='代理申请审核（H5 通道 1）';

-- ----------------------------------------------------------------------------
-- 4. t_agent_level_change_log 等级变更流水（双通道统一审计）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_level_change_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '被修改用户ID',
    `from_level` VARCHAR(8) NOT NULL COMMENT '变更前等级',
    `to_level` VARCHAR(8) NOT NULL COMMENT '变更后等级',
    `source` VARCHAR(16) NOT NULL COMMENT '来源：user_apply（H5 申请审核）/ admin_direct（客服直改）/ system_freeze / system_unfreeze',
    `application_id` BIGINT UNSIGNED NULL COMMENT '关联的申请ID（source=user_apply 时必填）',
    `operator_admin_id` BIGINT NULL COMMENT '操作人 admin user id（system_* 时为 NULL）',
    `reason` VARCHAR(500) NOT NULL COMMENT '变更原因（必填便于审计）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_create` (`user_id`, `create_time` DESC),
    KEY `idx_source_create` (`source`, `create_time` DESC) COMMENT '按来源类型审计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='代理等级变更流水（H5 申请 / admin 直改 / 系统冻结 三路统一审计）';

-- ----------------------------------------------------------------------------
-- 5. V0-V5 默认配置（PRD §8.1）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `t_agent_level`
    (`level_code`, `name_en`, `name_zh`, `match_rate`, `global_dividend_rate`,
     `min_active_node_value_usdt`, `min_direct_referral_count`,
     `min_left_volume_total`, `min_right_volume_total`,
     `enabled`, `sort`, `remark`)
VALUES
    ('V0', 'Member',     '普通用户', 0.000000, 0.000000, 0.00000000,    0,  0.00000000,        0.00000000,        1, 0, '默认等级，不可申请，新用户注册即拥有'),
    ('V1', 'Agent V1',   '代理 V1',  0.050000, 0.000000, 2000.00000000, 0,  0.00000000,        0.00000000,        1, 1, 'PRD §8.1：持 ≥2000 USDT active 矿机 + 申请'),
    ('V2', 'Agent V2',   '代理 V2',  0.065000, 0.000000, 2000.00000000, 5,  25000.00000000,    25000.00000000,    1, 2, 'PRD §8.1：V1 + 直推 5 V1 + 左右各 ≥25k（合 50k）'),
    ('V3', 'Agent V3',   '代理 V3',  0.080000, 0.000000, 2000.00000000, 12, 150000.00000000,   150000.00000000,   1, 3, 'PRD §8.1：V2 + 直推 12 + 左右各 ≥150k（合 300k）'),
    ('V4', 'Agent V4',   '代理 V4',  0.100000, 0.005000, 2000.00000000, 25, 500000.00000000,   500000.00000000,   1, 4, 'PRD §8.1：V3 + 直推 25 + 左右各 ≥500k（合 1M）+ 0.5% 全网分红'),
    ('V5', 'Agent V5',   '代理 V5',  0.120000, 0.010000, 2000.00000000, 50, 2500000.00000000,  2500000.00000000,  1, 5, 'PRD §8.1：V4 + 直推 50 + 左右各 ≥2.5M（合 5M）+ 1% 全网分红');
