-- ============================================================================
-- V20260507_019 H5 首页金刚位再调整：恢复 DeFi 挖矿到最后 + C2C 图标处理
-- ============================================================================
-- 变更（基于 V018 上）：
--   1. 把 host_non-collateralized_mining (DeFi 挖矿) 加回金刚位，sort=6（理财之后）
--   2. C2C 项 imgUrl 留空，前端 iconMap 也已删除 c2c 映射，
--      由 image-load 组件 fallback 到文字 "C2C" 展示（保持原横幅 C2C 视觉风格）
-- ============================================================================
-- 顺序：C2C(1) / 金矿(2) / 推广中心(3) / 秒合约(4) / 理财(5) / DeFi 挖矿(6) / U本位(7) / 币币交易(8)
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = '[{"name":"C2C","key":"c2c","imgUrl":"","linkUrl":"/c2c","sort":1,"isOpen":true},{"name":"金矿","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/app-download?flag=home","sort":2,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":3,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":4,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":5,"isOpen":true},{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":6,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":7,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":8,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
