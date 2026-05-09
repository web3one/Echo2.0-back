-- ============================================================================
-- V20260508_022 H5 首页金刚位首位新增"分享邀请码"
-- ============================================================================
-- 业务变更（金矿邀请体系 Phase 0）：
--   sort=1 新增 share_invite（点击复制当前用户邀请码到剪贴板，未登录跳登录页）
--   原 sort=1..N 全部 +1 顺延：C2C(2) / 金矿(3) / 推广中心(4) / 秒合约(5) / 理财(6) / DeFi挖矿(7) / U本位(8) / 币币交易(9)
-- 配套改动：
--   1. echo2.0-h5/src/views/home/components/FrontPage/menu.vue: routeLink 增加 'action:copy_invite_code' 分支调用 copyInviteCode()
--   2. echo2.0-h5/src/utils/iconMap.js: HOME_MENU_ICON_MAP 增加 share_invite -> 'copy' 图标映射
--   3. echo2.0-h5/src/plugin/i18n/locales/*.json: 21 语言新增 "share_invite" 翻译
-- linkUrl 约定：以 'action:' 开头的 linkUrl 不触发路由，由 menu.vue 内的 action 分发逻辑处理
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = '[{"name":"分享邀请码","key":"share_invite","imgUrl":"","linkUrl":"action:copy_invite_code","sort":1,"isOpen":true},{"name":"C2C","key":"c2c","imgUrl":"","linkUrl":"/c2c","sort":2,"isOpen":true},{"name":"金矿","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/gold/","sort":3,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":4,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":5,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":6,"isOpen":true},{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":7,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":8,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":9,"isOpen":false}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
