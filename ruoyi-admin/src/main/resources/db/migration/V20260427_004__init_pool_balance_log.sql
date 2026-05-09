-- ================================================================
-- Phase 1.4: 底池表 + 底池流水
-- 底池粒度：symbol + chain（USDT-ETH / USDT-BSC / USDT-BASE / USDT-TRX）
-- 充值入账 +、提现完成 -、归集 +、管理员手工 +/-
-- ================================================================

DROP TABLE IF EXISTS `t_pool_balance`;

CREATE TABLE `t_pool_balance` (
    `id`                    BIGINT         NOT NULL AUTO_INCREMENT   COMMENT '主键',
    `symbol`                VARCHAR(20)    NOT NULL                  COMMENT '币种：USDT / BTC / ETH / ...',
    `chain`                 VARCHAR(20)    NOT NULL                  COMMENT '链：ETH / BSC / BASE / TRX',
    `balance`               DECIMAL(30, 6) NOT NULL DEFAULT 0         COMMENT '当前余额（充值+ 提现-）',
    `frozen_balance`        DECIMAL(30, 6) NOT NULL DEFAULT 0         COMMENT '冻结余额（提现审核中暂占用）',
    `alert_threshold`       DECIMAL(30, 6) NOT NULL DEFAULT 0         COMMENT '告警阈值，余额低于此值会自动停用该链提现',
    `max_single_withdraw`   DECIMAL(30, 6) NOT NULL DEFAULT 0         COMMENT '单笔最大提现限额，0=不限',
    `display_in_h5`         TINYINT(1)     NOT NULL DEFAULT 1         COMMENT 'H5 是否展示该底池 0隐藏 1展示',
    `display_balance`       TINYINT(1)     NOT NULL DEFAULT 1         COMMENT 'H5 是否展示具体余额 0只显存在 1显数字',

    `create_by`             VARCHAR(64)    NULL DEFAULT NULL,
    `create_time`           DATETIME       NULL DEFAULT NULL,
    `update_by`             VARCHAR(64)    NULL DEFAULT NULL,
    `update_time`           DATETIME       NULL DEFAULT NULL,
    `remark`                VARCHAR(500)   NULL DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_symbol_chain` (`symbol`, `chain`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '平台底池余额（按币种+链分组）';

-- 4 个 USDT 底池，初始余额 0，由管理员手工调整或自动累加
INSERT INTO `t_pool_balance`
(`symbol`, `chain`, `balance`, `alert_threshold`, `max_single_withdraw`, `display_in_h5`, `display_balance`, `create_time`)
VALUES
('USDT', 'ETH',  0, 1000, 50000, 1, 1, NOW()),
('USDT', 'BSC',  0, 1000, 50000, 1, 1, NOW()),
('USDT', 'BASE', 0, 1000, 50000, 1, 1, NOW()),
('USDT', 'TRX',  0, 1000, 50000, 1, 1, NOW());

-- ----------------------------------------------------------------

DROP TABLE IF EXISTS `t_pool_log`;

CREATE TABLE `t_pool_log` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT     COMMENT '主键',
    `symbol`         VARCHAR(20)    NOT NULL                    COMMENT '币种',
    `chain`          VARCHAR(20)    NOT NULL                    COMMENT '链',
    `change_type`    VARCHAR(20)    NOT NULL                    COMMENT '变动类型：RECHARGE(充值) / WITHDRAW(提现) / AGGREGATE(归集) / MANUAL(手工)',
    `direction`      TINYINT        NOT NULL                    COMMENT '方向：1=增加 -1=减少',
    `amount`         DECIMAL(30, 6) NOT NULL                    COMMENT '变动金额（取绝对值）',
    `before_balance` DECIMAL(30, 6) NOT NULL                    COMMENT '变动前余额',
    `after_balance`  DECIMAL(30, 6) NOT NULL                    COMMENT '变动后余额',
    `ref_type`       VARCHAR(50)    NULL DEFAULT NULL           COMMENT '关联类型：t_app_recharge / t_withdraw / t_aggregation_task',
    `ref_id`         BIGINT         NULL DEFAULT NULL           COMMENT '关联记录 ID',
    `operator`       VARCHAR(64)    NULL DEFAULT NULL           COMMENT '操作人（系统自动 = SYSTEM，手工调整 = admin 用户名）',

    `create_by`      VARCHAR(64)    NULL DEFAULT NULL,
    `create_time`    DATETIME       NULL DEFAULT NULL,
    `remark`         VARCHAR(500)   NULL DEFAULT NULL           COMMENT '备注，手工调整必填',

    PRIMARY KEY (`id`),
    KEY `idx_symbol_chain_time` (`symbol`, `chain`, `create_time`),
    KEY `idx_ref` (`ref_type`, `ref_id`),
    KEY `idx_change_type` (`change_type`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '底池变动流水';
