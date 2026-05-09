-- ================================================================
-- Phase 1.3: 平台钱包表
-- 主钱包（归集汇总点，冷处理）+ 热钱包（提现打款用）+ 备份冷钱包
-- 注意：私钥不入库，靠主助记词 + derive_path 现场派生
-- ================================================================

DROP TABLE IF EXISTS `t_platform_wallet`;

CREATE TABLE `t_platform_wallet` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT       COMMENT '主键',
    `chain`          VARCHAR(20)    NOT NULL                      COMMENT '链：ETH / BSC / BASE / TRX',
    `address`        VARCHAR(64)    NOT NULL                      COMMENT '钱包地址',
    `wallet_type`    VARCHAR(20)    NOT NULL                      COMMENT '类型：MAIN(归集主钱包) / HOT(提现热钱包) / COLD(冷备份)',
    `derive_path`    VARCHAR(64)    NULL DEFAULT NULL             COMMENT 'HD 派生路径（如 m/44''/60''/0''/0/0），COLD 钱包通常为空（外部托管）',
    `usdt_balance`   DECIMAL(30, 6) NOT NULL DEFAULT 0             COMMENT 'USDT 余额（缓存，由监听服务定时刷新）',
    `native_balance` DECIMAL(30, 8) NOT NULL DEFAULT 0             COMMENT '原生币余额（ETH/BNB/TRX，用于支付 gas）',
    `last_sync_time` DATETIME       NULL DEFAULT NULL             COMMENT '最近一次链上余额同步时间',
    `enabled`        TINYINT(1)     NOT NULL DEFAULT 1            COMMENT '是否启用 0停用 1启用',

    `create_by`      VARCHAR(64)    NULL DEFAULT NULL,
    `create_time`    DATETIME       NULL DEFAULT NULL,
    `update_by`      VARCHAR(64)    NULL DEFAULT NULL,
    `update_time`    DATETIME       NULL DEFAULT NULL,
    `remark`         VARCHAR(500)   NULL DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chain_address` (`chain`, `address`),
    KEY `idx_chain_type` (`chain`, `wallet_type`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '平台钱包（主/热/冷）';
