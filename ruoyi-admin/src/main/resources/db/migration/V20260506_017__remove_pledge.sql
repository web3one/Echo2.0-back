-- ============================================================================
-- V20260506_017 移除质押挖矿模块（保留 DEFI 挖矿）
-- ============================================================================
-- 范围：
--   1. 清理 sys_menu：2250 质押挖矿(顶层) + 2251 订单列表 + 2252 挖矿产品
--      + 2254-2258 ming 产品按钮权限 + 2259 order 列表权限
--   2. 清理 sys_role_menu 关联
--   3. UPDATE t_setting.MIDDLE_MENU_SETTING JSON 去掉 defi_host_lockup（"质押挖矿"）
--      保留 host_non-collateralized_mining（"DeFi 挖矿"）
-- ============================================================================
-- 不动后端业务表（TMineOrder/TMingOrder/TMingProductUser 等 DEFI 也用）
-- ============================================================================

-- 1. 清 sys_role_menu 关联（必须先删，避免外键悬空）
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
    2250, 2251, 2252,
    2254, 2255, 2256, 2257, 2258, 2259
);

-- 2. 清 sys_menu
DELETE FROM `sys_menu` WHERE `menu_id` IN (
    2250, 2251, 2252,
    2254, 2255, 2256, 2257, 2258, 2259
);

-- 3. 重写 MIDDLE_MENU_SETTING JSON 去掉"质押挖矿"项
--    上一版（V016）已经只有 8 项：DeFi挖矿/质押挖矿/下载中心/推广中心/秒合约/理财/U本位/币币交易
--    本次再删掉"质押挖矿"，剩 7 项；同时把 sort 重新编号 1..7
UPDATE `t_setting`
SET `setting_value` = '[{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":1,"isOpen":true},{"name":"下载中心","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/app-download?flag=home","sort":2,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":3,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":4,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":5,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":6,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":7,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
