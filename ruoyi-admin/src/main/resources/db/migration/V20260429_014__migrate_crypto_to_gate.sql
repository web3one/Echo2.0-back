-- ================================================================
-- 加密币行情数据源切换：Binance → Gate.io
-- 原因：服务器 IP 被 Binance 屏蔽（HTTP 451），改用 Gate 公开 WebSocket
-- 影响：所有 market='binance' 的现货/合约/秒合约/echo-refer 全部切到 'gate'
-- 不动：market='mt5'（外汇贵金属占位）/ market='huobi'（少量遗留）保持原样
-- 切换后由 GateSpotWebSocketSubscriber 统一接管推送
-- ================================================================

UPDATE `t_currency_symbol`     SET `market` = 'gate' WHERE `market` = 'binance';
UPDATE `t_contract_coin`       SET `market` = 'gate' WHERE `market` = 'binance';
UPDATE `t_second_coin_config`  SET `market` = 'gate' WHERE `market` = 'binance';

-- t_kline_symbol：market='echo' 自发币的 referMarket 也跟着切，
-- 这样 referCoin+usdt 的实时价由 Gate 推（ChainExecutor 透明替换）
UPDATE `t_kline_symbol` SET `refer_market` = 'gate' WHERE `market` = 'echo' AND `refer_market` = 'binance';

-- 校验输出（Flyway 不会保留 SELECT 输出，仅作为执行记录）
-- 期望：上述 UPDATE 命中行数 > 0
