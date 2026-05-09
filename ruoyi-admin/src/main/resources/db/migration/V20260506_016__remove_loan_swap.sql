-- ============================================================================
-- V20260506_016 移除助力贷 + 闪兑两大业务模块
-- ============================================================================
-- 范围：
--   1. DROP 业务表：t_load_order / t_load_product / t_exchange_coin_record
--   2. 清理 sys_menu：借贷管理(2048+) / 兑换订单(2089+) / 贷款配置(2102) + 全部按钮权限
--      （保留 2090 币种管理；将其升级为顶层菜单 + 重命名 2089 为"币种管理"作为父级保留）
--   3. 清理 sys_role_menu 关联
--   4. DELETE t_setting.LOAD_SETTING 配置项
--   5. UPDATE t_setting.MIDDLE_MENU_SETTING JSON 重写去掉"助理贷 + 闪兑"两条
--   6. 清理 gen_table 代码生成器元数据
-- ============================================================================

-- 1. 删业务表
DROP TABLE IF EXISTS `t_load_order`;
DROP TABLE IF EXISTS `t_load_product`;
DROP TABLE IF EXISTS `t_exchange_coin_record`;

-- 2. 清 sys_role_menu 关联（必须先于 sys_menu）
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
    2048, 2049, 2050,
    2091, 2102,
    2172, 2173, 2174, 2175,
    2176, 2177, 2178, 2179, 2180, 2181
);

-- 3. 清 sys_menu：删借贷管理整组 + 兑换订单 + 贷款配置 + 按钮权限
DELETE FROM `sys_menu` WHERE `menu_id` IN (
    2048, 2049, 2050,
    2091, 2102,
    2172, 2173, 2174, 2175,
    2176, 2177, 2178, 2179, 2180, 2181
);

-- 4. 把 2089 重命名为"币种管理"作为顶层（原本叫"兑换管理"），让 2090 子项继续挂在它下面
UPDATE `sys_menu`
SET `menu_name` = '币种管理',
    `path`      = 'symbol',
    `icon`      = 'cascader',
    `update_time` = NOW()
WHERE `menu_id` = 2089;

-- 5. 删贷款配置 setting
DELETE FROM `t_setting` WHERE `id` = 'LOAD_SETTING';

-- 6. 重写 MIDDLE_MENU_SETTING JSON 去掉"助理贷 + 闪兑"两项（保留其余 8 条）
UPDATE `t_setting`
SET `setting_value` = '[{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":1,"isOpen":true},{"name":"质押挖矿","key":"defi_host_lockup","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c09b72b7a58046d7888286d570b17267.png","linkUrl":"/pledge","sort":2,"isOpen":true},{"name":"下载中心","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/app-download?flag=home","sort":3,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":4,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":5,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":6,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":7,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":8,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';

-- 7. 清 gen_table 代码生成器元数据
DELETE FROM `gen_table_column` WHERE `table_id` IN (
    SELECT `table_id` FROM `gen_table`
    WHERE `table_name` IN ('t_load_order', 't_load_product', 't_exchange_coin_record')
);
DELETE FROM `gen_table` WHERE `table_name` IN ('t_load_order', 't_load_product', 't_exchange_coin_record');
