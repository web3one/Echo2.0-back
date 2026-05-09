-- ============================================================================
-- V20260506_018 H5 首页金刚位重排：C2C 入金刚 + 下载中心改名为"金矿"
-- ============================================================================
-- 变更：
--   1. 移除 host_non-collateralized_mining（DeFi 挖矿）— 不再展示在金刚位
--      （DEFI 挖矿页面/路由代码保留，仅金刚位入口下架）
--   2. 新增 c2c 项，sort=1（占原 DeFi 挖矿位置）
--   3. download_center 改 sort=2（排在 C2C 旁边），name 改"金矿"
--   4. 其他项依次后移：推广中心(3) / 秒合约(4) / 理财(5) / U本位(6) / 币币交易(7)
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = '[{"name":"C2C","key":"c2c","imgUrl":"","linkUrl":"/c2c","sort":1,"isOpen":true},{"name":"金矿","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/app-download?flag=home","sort":2,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":3,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":4,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":5,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":6,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":7,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
