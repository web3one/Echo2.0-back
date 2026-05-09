-- ================================================================
-- Phase 1.5: 归集任务记录
-- 每天凌晨跑一批归集任务，每条链一个 batch，记录派 gas 和归集结果
-- 失败可重试，便于排查问题
-- ================================================================

DROP TABLE IF EXISTS `t_aggregation_task`;

CREATE TABLE `t_aggregation_task` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT     COMMENT '主键',
    `batch_id`        VARCHAR(64)    NOT NULL                    COMMENT '批次 ID（同一次定时任务用同一批次）',
    `chain`           VARCHAR(20)    NOT NULL                    COMMENT '链',

    `phase`           VARCHAR(20)    NOT NULL                    COMMENT '阶段：GAS_TOPUP(派 gas) / COLLECT(归集 USDT) / DONE',
    `status`          VARCHAR(20)    NOT NULL                    COMMENT '状态：PENDING / RUNNING / SUCCESS / PARTIAL / FAILED',

    `target_count`    INT            NOT NULL DEFAULT 0          COMMENT '本批待处理子地址数',
    `success_count`   INT            NOT NULL DEFAULT 0          COMMENT '成功数',
    `failed_count`    INT            NOT NULL DEFAULT 0          COMMENT '失败数',

    `gas_total`       DECIMAL(30, 8) NOT NULL DEFAULT 0          COMMENT '本批派 gas 累计消耗（原生币）',
    `usdt_collected`  DECIMAL(30, 6) NOT NULL DEFAULT 0          COMMENT '本批归集到主钱包的 USDT 总量',

    `start_time`      DATETIME       NULL DEFAULT NULL,
    `end_time`        DATETIME       NULL DEFAULT NULL,
    `error_msg`       VARCHAR(2000)  NULL DEFAULT NULL           COMMENT '失败原因（截断到 2000 字）',

    `create_by`       VARCHAR(64)    NULL DEFAULT NULL,
    `create_time`     DATETIME       NULL DEFAULT NULL,
    `update_by`       VARCHAR(64)    NULL DEFAULT NULL,
    `update_time`     DATETIME       NULL DEFAULT NULL,
    `remark`          VARCHAR(500)   NULL DEFAULT NULL,

    PRIMARY KEY (`id`),
    KEY `idx_batch` (`batch_id`),
    KEY `idx_chain_status` (`chain`, `status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '归集任务记录';

-- ----------------------------------------------------------------
-- 归集明细：每个子地址一行，记录派 gas 和归集结果
-- ----------------------------------------------------------------

DROP TABLE IF EXISTS `t_aggregation_item`;

CREATE TABLE `t_aggregation_item` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT,
    `task_id`          BIGINT         NOT NULL                   COMMENT '关联 t_aggregation_task.id',
    `chain`            VARCHAR(20)    NOT NULL,
    `user_id`          BIGINT         NOT NULL                   COMMENT '子地址所属用户',
    `from_address`     VARCHAR(64)    NOT NULL                   COMMENT '子地址',
    `to_address`       VARCHAR(64)    NOT NULL                   COMMENT '主钱包地址',

    `usdt_amount`      DECIMAL(30, 6) NOT NULL DEFAULT 0         COMMENT '本次归集的 USDT 数量',
    `gas_topup_amount` DECIMAL(30, 8) NOT NULL DEFAULT 0         COMMENT '派的 gas 数量',

    `gas_topup_hash`   VARCHAR(128)   NULL DEFAULT NULL          COMMENT '派 gas 的交易 hash',
    `collect_hash`     VARCHAR(128)   NULL DEFAULT NULL          COMMENT '归集 USDT 的交易 hash',

    `status`           VARCHAR(20)    NOT NULL                   COMMENT 'PENDING / GAS_SENT / COLLECTING / SUCCESS / FAILED',
    `error_msg`        VARCHAR(1000)  NULL DEFAULT NULL,

    `create_time`      DATETIME       NULL DEFAULT NULL,
    `update_time`      DATETIME       NULL DEFAULT NULL,

    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_chain_user` (`chain`, `user_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '归集明细（每个子地址一行）';
