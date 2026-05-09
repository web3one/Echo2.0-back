-- ================================================================
-- TradFi 标的表（股票/股指/外汇/贵金属/大宗商品）
-- 数据源默认 Gate.io TradFi REST，2.5s 轮询；后续可挂 mt5/finnhub
-- 与 4 张币种表分开，避免污染加密币逻辑
-- ================================================================

DROP TABLE IF EXISTS `t_tradfi_symbol`;

CREATE TABLE `t_tradfi_symbol` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `symbol`         VARCHAR(32)  NOT NULL                COMMENT '内部统一符号 EURUSD/XAUUSD/SPX500/AAPL',
    `show_symbol`    VARCHAR(32)           DEFAULT NULL   COMMENT 'H5 展示用 EUR/USD、XAU/USD',
    `gate_contract`  VARCHAR(64)  NOT NULL                COMMENT 'Gate TradFi 合约名 EURUSD / XAUUSD / SPX500 / AAPL.US',
    `category`       VARCHAR(16)  NOT NULL                COMMENT 'FX 外汇 / METAL 贵金属 / INDEX 股指 / STOCK 股票 / COMMODITY 大宗',
    `base_coin`      VARCHAR(16)           DEFAULT 'USD'  COMMENT '计价货币 默认 USD',
    `logo`           VARCHAR(256)          DEFAULT NULL,
    `market`         VARCHAR(16)  NOT NULL DEFAULT 'gate' COMMENT '数据源 gate/mt5/finnhub',
    `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用 2=禁用',
    `show_flag`      TINYINT      NOT NULL DEFAULT 1      COMMENT '1=H5 展示 2=隐藏',
    `decimals`       INT          NOT NULL DEFAULT 4      COMMENT '价格小数位 外汇/贵金属 4 位、股指/股票 2 位',
    `sort`           INT          NOT NULL DEFAULT 0      COMMENT '排序，小在前',
    `create_by`      VARCHAR(64)           DEFAULT NULL,
    `create_time`    DATETIME              DEFAULT NULL,
    `update_by`      VARCHAR(64)           DEFAULT NULL,
    `update_time`    DATETIME              DEFAULT NULL,
    `remark`         VARCHAR(500)          DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_symbol` (`symbol`),
    KEY `idx_category_status` (`category`, `status`),
    KEY `idx_show_flag` (`show_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TradFi 标的（股票/指数/外汇/贵金属）';

-- ----------------------------------------------------------------
-- 14 条种子数据 — gate_contract 是占位（实际 Gate API 端点跑通后第一次启动会校准）
-- decimals：FX/METAL=4 位、INDEX=2 位、STOCK=2 位
-- sort 按品类内热度排
-- ----------------------------------------------------------------
INSERT INTO `t_tradfi_symbol`
(`symbol`,    `show_symbol`, `gate_contract`, `category`,  `base_coin`, `market`, `status`, `show_flag`, `decimals`, `sort`, `create_by`, `create_time`, `remark`)
VALUES
-- FX 外汇
('EURUSD',    'EUR/USD',     'EURUSD',        'FX',        'USD',       'gate',   1,        1,           5,          10,     'system',    NOW(),         '欧元/美元'),
('GBPUSD',    'GBP/USD',     'GBPUSD',        'FX',        'USD',       'gate',   1,        1,           5,          11,     'system',    NOW(),         '英镑/美元'),
('USDJPY',    'USD/JPY',     'USDJPY',        'FX',        'JPY',       'gate',   1,        1,           3,          12,     'system',    NOW(),         '美元/日元'),
('AUDUSD',    'AUD/USD',     'AUDUSD',        'FX',        'USD',       'gate',   1,        1,           5,          13,     'system',    NOW(),         '澳元/美元'),
-- METAL 贵金属
('XAUUSD',    'XAU/USD',     'XAUUSD',        'METAL',     'USD',       'gate',   1,        1,           2,          20,     'system',    NOW(),         '黄金'),
('XAGUSD',    'XAG/USD',     'XAGUSD',        'METAL',     'USD',       'gate',   1,        1,           3,          21,     'system',    NOW(),         '白银'),
-- INDEX 股指（Gate 用 US500 表示标普 500，SPX500 不存在）
('US500',     'S&P 500',     'US500',         'INDEX',     'USD',       'gate',   1,        1,           2,          30,     'system',    NOW(),         '标普 500'),
('NAS100',    'NAS100',      'NAS100',        'INDEX',     'USD',       'gate',   1,        1,           2,          31,     'system',    NOW(),         '纳斯达克 100'),
('US30',      'US30',        'US30',          'INDEX',     'USD',       'gate',   1,        1,           2,          32,     'system',    NOW(),         '道琼斯 30'),
('HK50',      'HK50',        'HK50',          'INDEX',     'HKD',       'gate',   1,        1,           2,          33,     'system',    NOW(),         '恒生 50'),
-- STOCK 美股 CFD（Gate 股票符号不带 .US 后缀；status=closed 时只在交易时段推送）
('AAPL',      'Apple',       'AAPL',          'STOCK',     'USD',       'gate',   1,        1,           2,          40,     'system',    NOW(),         '苹果'),
('TSLA',      'Tesla',       'TSLA',          'STOCK',     'USD',       'gate',   1,        1,           2,          41,     'system',    NOW(),         '特斯拉'),
('NVDA',      'NVIDIA',      'NVDA',          'STOCK',     'USD',       'gate',   1,        1,           2,          42,     'system',    NOW(),         '英伟达'),
('META',      'Meta',        'META',          'STOCK',     'USD',       'gate',   1,        1,           2,          43,     'system',    NOW(),         'Meta');

-- ----------------------------------------------------------------
-- 字典：tradfi_category（用于 admin 下拉框）
-- 若已存在同名字典 type，先清理避免冲突
-- ----------------------------------------------------------------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'tradfi_category';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'tradfi_category';

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('TradFi 品类', 'tradfi_category', '0', 'system', NOW(), '股票/指数/外汇/贵金属/大宗');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
(1, '外汇',     'FX',        'tradfi_category', '', 'primary', 'Y', '0', 'system', NOW(), ''),
(2, '贵金属',   'METAL',     'tradfi_category', '', 'warning', 'N', '0', 'system', NOW(), ''),
(3, '股指',     'INDEX',     'tradfi_category', '', 'success', 'N', '0', 'system', NOW(), ''),
(4, '股票',     'STOCK',     'tradfi_category', '', 'info',    'N', '0', 'system', NOW(), ''),
(5, '大宗商品', 'COMMODITY', 'tradfi_category', '', 'default', 'N', '0', 'system', NOW(), '');

-- ----------------------------------------------------------------
-- 字典：tradfi_market（数据源）
-- ----------------------------------------------------------------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'tradfi_market';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'tradfi_market';

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('TradFi 数据源', 'tradfi_market', '0', 'system', NOW(), 'gate/mt5/finnhub');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
(1, 'Gate',    'gate',    'tradfi_market', '', 'primary', 'Y', '0', 'system', NOW(), ''),
(2, 'MT5',     'mt5',     'tradfi_market', '', 'info',    'N', '0', 'system', NOW(), ''),
(3, 'Finnhub', 'finnhub', 'tradfi_market', '', 'success', 'N', '0', 'system', NOW(), '');
