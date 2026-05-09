-- ================================================================
-- Phase 1.1: 链配置表
-- 每条链一行，存 RPC、USDT 合约、确认数、归集 gas、充提开关等
-- ================================================================

DROP TABLE IF EXISTS `t_chain_config`;

CREATE TABLE `t_chain_config` (
    `chain`                       VARCHAR(20)    NOT NULL                  COMMENT '链标识：ETH / BSC / BASE / TRX',
    `chain_name`                  VARCHAR(50)    NOT NULL                  COMMENT '显示名称：Ethereum / BNB Smart Chain / Base / Tron',
    `native_symbol`               VARCHAR(20)    NOT NULL                  COMMENT '原生币：ETH / BNB / ETH / TRX',
    `bip44_coin_type`             INT            NOT NULL                  COMMENT 'BIP44 coin_type：EVM 链统一 60，TRX 是 195',

    -- RPC + 浏览器
    `rpc_url`                     VARCHAR(500)   NOT NULL                  COMMENT '主 RPC URL（公共节点）',
    `backup_rpc_url`              VARCHAR(500)   NULL DEFAULT NULL         COMMENT '备用 RPC URL',
    `explorer_url`                VARCHAR(200)   NULL DEFAULT NULL         COMMENT '区块浏览器基址，拼 tx hash 给前端跳转用',

    -- USDT
    `usdt_contract`               VARCHAR(64)    NULL DEFAULT NULL         COMMENT 'USDT 合约地址（ERC20/TRC20），NULL=不支持',
    `usdt_decimals`               INT            NOT NULL DEFAULT 6        COMMENT 'USDT 精度，TRC20=6，ERC20=6',

    -- 监听
    `min_confirmations`           INT            NOT NULL DEFAULT 12       COMMENT '入账最少确认块数',
    `block_polling_interval_ms`   INT            NOT NULL DEFAULT 5000     COMMENT '区块轮询间隔（毫秒）',
    `last_scanned_block`          BIGINT         NOT NULL DEFAULT 0        COMMENT '最近扫描到的区块高度（容灾备份，主存 Redis）',

    -- 归集
    `gas_top_up_amount`           DECIMAL(30, 8) NOT NULL DEFAULT 0        COMMENT '归集时给子地址转的 gas 数量（TRX:30、ETH:0.001、BNB:0.0005）',
    `min_aggregation_amount`      DECIMAL(30, 6) NOT NULL DEFAULT 1        COMMENT '归集触发阈值，子地址 USDT 余额小于此值不归集（避免亏 gas）',

    -- 开关
    `deposit_enabled`             TINYINT(1)     NOT NULL DEFAULT 1        COMMENT '充值开关 0关 1开',
    `withdraw_enabled`            TINYINT(1)     NOT NULL DEFAULT 1        COMMENT '提现开关 0关 1开',

    `create_by`                   VARCHAR(64)    NULL DEFAULT NULL,
    `create_time`                 DATETIME       NULL DEFAULT NULL,
    `update_by`                   VARCHAR(64)    NULL DEFAULT NULL,
    `update_time`                 DATETIME       NULL DEFAULT NULL,
    `remark`                      VARCHAR(500)   NULL DEFAULT NULL,

    PRIMARY KEY (`chain`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '链配置（RPC/合约/确认数/归集gas/充提开关）';

-- ----------------------------------------------------------------
-- 初始化 4 条链配置（USDT 合约地址来自官方）
-- ETH/BSC/Base 都用 EVM 派生（coin_type=60），TRX 用 195
-- RPC 全部用免费公共节点，生产环境建议替换为付费节点
-- ----------------------------------------------------------------
INSERT INTO `t_chain_config`
(`chain`, `chain_name`, `native_symbol`, `bip44_coin_type`,
 `rpc_url`, `backup_rpc_url`, `explorer_url`,
 `usdt_contract`, `usdt_decimals`, `min_confirmations`, `block_polling_interval_ms`,
 `gas_top_up_amount`, `min_aggregation_amount`,
 `deposit_enabled`, `withdraw_enabled`, `create_time`)
VALUES
('ETH',  'Ethereum',          'ETH',  60,
 'https://ethereum-rpc.publicnode.com', 'https://eth.llamarpc.com', 'https://etherscan.io',
 '0xdAC17F958D2ee523a2206206994597C13D831ec7', 6, 12, 12000,
 0.005, 5, 1, 1, NOW()),

('BSC',  'BNB Smart Chain',   'BNB',  60,
 'https://bsc-dataseed.binance.org', 'https://bsc.publicnode.com', 'https://bscscan.com',
 '0x55d398326f99059fF775485246999027B3197955', 18, 15, 3000,
 0.001, 5, 1, 1, NOW()),

('BASE', 'Base',              'ETH',  60,
 'https://mainnet.base.org', 'https://base.publicnode.com', 'https://basescan.org',
 '0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2', 6, 10, 2000,
 0.0005, 5, 1, 1, NOW()),

('TRX',  'Tron',              'TRX',  195,
 'https://api.trongrid.io', 'https://api.tronstack.io', 'https://tronscan.org',
 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t', 6, 19, 3000,
 30, 5, 1, 1, NOW());
