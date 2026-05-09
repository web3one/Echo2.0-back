-- ================================================================
-- 修复 Gate.io 上不存在/已下架/不同名的币种
-- 实测结果（2026-04-29）：
--   LEVER → Gate 已下架（INVALID_CURRENCY: currency LEVER is delisted）
--   MATIC → Gate 改名 POL（Polygon 2.0 rebrand），需要手动加 POL 条目
--   GMEE  → Gate 有，但本地 DB market='huobi'，V014 没切到
-- ================================================================

-- 1) 禁用 LEVER（已下架，不会有数据）
UPDATE `t_currency_symbol`     SET `enable`    = '2',  `is_show`   = '2' WHERE `coin` = 'lever' OR `symbol` LIKE 'lever%';
UPDATE `t_contract_coin`       SET `enable`    = 1,    `visible`   = 1   WHERE `coin` = 'lever' OR `symbol` LIKE 'lever%';
UPDATE `t_second_coin_config`  SET `status`    = 2,    `show_flag` = 2   WHERE `coin` = 'lever' OR `symbol` LIKE 'lever%';

-- 2) 禁用 MATIC（Gate 已改名 POL，避免显示 0；如需 POL 行情请在 admin 后台手工新增 POL 条目）
UPDATE `t_currency_symbol`     SET `enable`    = '2',  `is_show`   = '2' WHERE `coin` = 'matic' OR `symbol` LIKE 'matic%';
UPDATE `t_contract_coin`       SET `enable`    = 1,    `visible`   = 1   WHERE `coin` = 'matic' OR `symbol` LIKE 'matic%';
UPDATE `t_second_coin_config`  SET `status`    = 2,    `show_flag` = 2   WHERE `coin` = 'matic' OR `symbol` LIKE 'matic%';

-- 3) 把 GMEE 从 huobi 切到 gate（Gate 有 GMEE_USDT，可以用）
UPDATE `t_currency_symbol`     SET `market` = 'gate' WHERE `market` = 'huobi' AND (`coin` = 'gmee' OR `symbol` LIKE 'gmee%');
UPDATE `t_contract_coin`       SET `market` = 'gate' WHERE `market` = 'huobi' AND (`coin` = 'gmee' OR `symbol` LIKE 'gmee%');
UPDATE `t_second_coin_config`  SET `market` = 'gate' WHERE `market` = 'huobi' AND (`coin` = 'gmee' OR `symbol` LIKE 'gmee%');
