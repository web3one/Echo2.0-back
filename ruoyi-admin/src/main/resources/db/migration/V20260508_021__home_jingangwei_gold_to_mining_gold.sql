-- ============================================================================
-- V20260508_021 H5 首页金刚位"金矿"linkUrl 改为独立部署的 mining-gold 子站点
-- ============================================================================
-- 业务变更：
--   原"金矿"项 linkUrl=/app-download?flag=home（指向下载页 SPA 路由）
--   改为 linkUrl=/gold/（指向独立 React 子站点 mining-gold，nginx 同域子路径部署）
-- 配套改动：
--   1. echo2.0-h5/src/views/home/components/FrontPage/menu.vue: routeLink 增加 /gold/ 走 location.href
--   2. mining-gold/vite.config.ts: build 时 base='/gold/'
--   3. nginx: location /gold/ { root /usr/share/nginx/html/; try_files $uri $uri/ /gold/index.html; }
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = '[{"name":"C2C","key":"c2c","imgUrl":"","linkUrl":"/c2c","sort":1,"isOpen":true},{"name":"金矿","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/gold/","sort":2,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":3,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":4,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":5,"isOpen":true},{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":6,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":7,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":8,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
