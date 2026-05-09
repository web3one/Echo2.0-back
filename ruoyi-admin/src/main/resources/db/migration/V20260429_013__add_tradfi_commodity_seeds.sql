-- ================================================================
-- 补充 TradFi 大宗商品（COMMODITY）种子数据
-- Gate TradFi WS 实测可用、status=open 的大宗合约
-- ================================================================

INSERT INTO `t_tradfi_symbol`
(`symbol`,    `show_symbol`, `gate_contract`, `category`,   `base_coin`, `market`, `status`, `show_flag`, `decimals`, `sort`, `create_by`, `create_time`, `remark`)
VALUES
('XTIUSD',    'WTI 原油',    'XTIUSD',        'COMMODITY',  'USD',       'gate',   1,        1,           2,          50,     'system',    NOW(),         'WTI 原油（美油 USOIL）'),
('XBRUSD',    'Brent 原油',  'XBRUSD',        'COMMODITY',  'USD',       'gate',   1,        1,           2,          51,     'system',    NOW(),         'Brent 原油（布伦特 UKOIL）'),
('NG',        '天然气',      'NG',            'COMMODITY',  'USD',       'gate',   1,        1,           3,          52,     'system',    NOW(),         '天然气 Natural Gas'),
('WHEAT',     '小麦',        'WHEAT',         'COMMODITY',  'USD',       'gate',   1,        1,           2,          53,     'system',    NOW(),         '小麦'),
('COTTON',    '棉花',        'COTTON',        'COMMODITY',  'USD',       'gate',   1,        1,           2,          54,     'system',    NOW(),         '棉花');
