-- ================================================================
-- Phase 1.2: 用户多链充值地址表
-- 每用户每条链一行，HD 钱包派生（私钥不入库）
-- 注意：EVM 三条链（ETH/BSC/BASE）对同一用户派生地址相同，
--       但仍存 3 行（chain 维度独立查询，逻辑清晰）
-- ================================================================

DROP TABLE IF EXISTS `t_user_address`;

CREATE TABLE `t_user_address` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '主键',
    `user_id`      BIGINT       NOT NULL                      COMMENT '用户 ID',
    `chain`        VARCHAR(20)  NOT NULL                      COMMENT '链：ETH / BSC / BASE / TRX',
    `address`      VARCHAR(64)  NOT NULL                      COMMENT '派生出来的充值地址（EVM 0x..., TRX T...）',
    `derive_path` VARCHAR(64)   NOT NULL                      COMMENT 'HD 派生路径，如 m/44''/60''/0''/0/123',
    `derive_index` BIGINT       NOT NULL                      COMMENT '派生索引（一般等于 user_id）',
    `create_by`    VARCHAR(64)  NULL DEFAULT NULL,
    `create_time`  DATETIME     NULL DEFAULT NULL,
    `update_by`    VARCHAR(64)  NULL DEFAULT NULL,
    `update_time`  DATETIME     NULL DEFAULT NULL,
    `remark`       VARCHAR(500) NULL DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_chain` (`user_id`, `chain`)        COMMENT '同一用户同一链唯一',
    UNIQUE KEY `uk_chain_address` (`chain`, `address`)     COMMENT '链上同一地址不能重复（防派生冲突）',
    KEY `idx_address` (`address`)                            COMMENT '链监听按地址反查 user_id',
    KEY `idx_chain` (`chain`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '用户多链充值地址（HD 派生）';
