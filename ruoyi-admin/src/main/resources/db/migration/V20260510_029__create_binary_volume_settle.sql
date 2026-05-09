-- ============================================================================
-- V20260510_029 双轨业绩 + 结算日志（金矿 Phase 1 P0 + Phase 2）
-- ============================================================================
-- 业务变更：
--   双轨制有"两类业绩"（PRD §7）：
--   - **当日新增业绩**：团队代理奖结算依据，每日 UTC 00:30 清零
--   - **累计业绩**：代理升级条件，永久累计不清零
--   两类业绩分两张表存，互不影响。
--
--   结算日志 t_settle_log 是 6 个定时任务的"幂等钥匙"——任务启动前先查
--   "今天本任务有没 success"，命中则直接 return；否则插入 running 行，
--   结束后 UPDATE status=success 或 failed。
--
-- 关键设计点：
--   1. **t_binary_volume_daily 不删**：每日 binary_daily_volume_reset_job
--      不是 DELETE 历史行，而是新业务日写新行（user_id+biz_date UNIQUE）。
--      这样可以追溯任意历史日的双轨业绩做对账。
--
--   2. **业绩 = 矿机金额还是奖励金额？**（PRD §7 第 5 条）：
--      双轨业绩按"下线购买矿机金额"累计，**不**是奖励金额。
--      例：A 在左区，A 买 L2（2000 USDT）→ user 的左区 +2000；A 拿到的静态
--      分红不影响 user 的双轨业绩。
--
--   3. **weak_volume 计算时机**：
--      不在每次写入时实时计算（高并发难维护），而是 agency_team_reward_job
--      读取时计算 = MIN(left_volume, right_volume)，并写入本字段做快照。
--
--   4. **t_settle_log.biz_date 是业务日不是执行日**：
--      UTC 00:05 执行的 daily_static_reward_job，结算的是"前一天" biz_date。
--      需要重跑某天结算时（生产事故修复），按 biz_date 查唯一性即可定位。
--
--   5. **任务幂等 SQL 模板**：
--      ```sql
--      -- 启动前
--      SELECT id FROM t_settle_log
--      WHERE job_name=? AND biz_date=? AND status='success';
--      -- 命中 → return（已发过不重发）
--
--      -- 没命中 → 插入运行中
--      INSERT INTO t_settle_log (job_name, biz_date, status, started_at)
--      VALUES (?, ?, 'running', NOW())
--      ON DUPLICATE KEY UPDATE
--        status='running', started_at=NOW(), error_message=NULL;
--      -- 用 UNIQUE(job_name, biz_date) 解决并发启动冲突
--
--      -- 任务结束
--      UPDATE t_settle_log
--      SET status='success', finished_at=NOW(), success_count=?, ...
--      WHERE id=?;
--      ```
--
-- 配套 Java（Phase 1+2 后续会写）：
--   - domain/TBinaryVolumeDaily.java + TBinaryVolumeTotal.java + TSettleLog.java
--   - 矿机购买 service：购买后递归向上更新 sponsor 链上的 daily / total 双方
--   - 6 个 quartz job 都基于 t_settle_log 做幂等
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. t_binary_volume_daily 双轨当日新增业绩
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_binary_volume_daily` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（这是某用户当日左右区的业绩聚合）',
    `biz_date` DATE NOT NULL COMMENT '业务日（YYYY-MM-DD）',
    `left_volume` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '当日左区下线累计购买金额（USDT）',
    `right_volume` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '当日右区下线累计购买金额（USDT）',
    `weak_volume` DECIMAL(28, 8) NULL COMMENT '弱区业绩快照（结算时写入 = MIN(left, right)）',
    `matched_amount_usdt` DECIMAL(28, 8) NULL COMMENT '团队代理奖应发金额快照（weak_volume × 当时匹配率，结算时写）',
    `settle_log_id` BIGINT UNSIGNED NULL COMMENT '结算它的 t_settle_log.id',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_biz_date` (`user_id`, `biz_date`),
    KEY `idx_biz_date` (`biz_date`) COMMENT '结算时全表扫某天的所有用户'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='双轨当日新增业绩（团队代理奖结算依据，每日新行不删）';

-- ----------------------------------------------------------------------------
-- 2. t_binary_volume_total 双轨累计业绩（不清零）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_binary_volume_total` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `left_volume_total` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '左区累计业绩（永久累计，代理升级用）',
    `right_volume_total` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '右区累计业绩',
    `direct_referral_count` INT NOT NULL DEFAULT 0 COMMENT '直推数（对应升级条件）',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='双轨累计业绩（代理升级条件依据，不清零）';

-- ----------------------------------------------------------------------------
-- 3. t_settle_log 结算日志（6 个定时任务幂等钥匙）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_settle_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_name` VARCHAR(64) NOT NULL COMMENT '任务名：daily_static_reward_job / agency_team_reward_job / agency_global_dividend_job / founder_dividend_job / xgt_lock_release_job / binary_daily_volume_reset_job',
    `biz_date` DATE NOT NULL COMMENT '业务日（结算所属日期，UTC，不是执行日）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'running' COMMENT '状态：running / success / failed',
    `started_at` DATETIME NOT NULL COMMENT '开始时间',
    `finished_at` DATETIME NULL COMMENT '结束时间',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT '应处理总数',
    `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功数',
    `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败数',
    `skipped_count` INT NOT NULL DEFAULT 0 COMMENT '跳过数（如冻结用户）',
    `amount_settled_usdt` DECIMAL(28, 8) NOT NULL DEFAULT 0 COMMENT '本次结算总金额（USDT 折算）',
    `error_message` TEXT NULL COMMENT '失败原因（status=failed 时填）',
    `triggered_by_admin_id` BIGINT NULL COMMENT '手动重试的操作人（cron 自动触发为 NULL）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_biz_date` (`job_name`, `biz_date`) COMMENT '同一任务同一业务日只能有一条（幂等关键）',
    KEY `idx_status_started` (`status`, `started_at`) COMMENT 'admin 监控失败任务'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='6 个定时任务结算日志（幂等校验依据 + admin 监控）';
