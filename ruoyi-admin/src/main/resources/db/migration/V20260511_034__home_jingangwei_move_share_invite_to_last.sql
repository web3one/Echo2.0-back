-- ============================================================================
-- V20260511_034 H5 首页金刚位"分享邀请码"移到末位
-- ============================================================================
-- 业务变更（金矿邀请体系 v2）：
--   share_invite 从 sort=1 移到 sort=9（末位），原 sort=2..9 全部前移到 sort=1..8
-- 新顺序：
--   1 c2c / 2 金矿 / 3 推广中心 / 4 秒合约 / 5 理财 / 6 DeFi挖矿
--   7 U本位(off) / 8 币币交易(off) / 9 share_invite
-- linkUrl='action:copy_invite_code' 仍由 menu.vue 内分支处理，无需前端配套修改
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = '[{"name":"C2C","key":"c2c","imgUrl":"","linkUrl":"/c2c","sort":1,"isOpen":true},{"name":"金矿","key":"download_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.06b5507d34ff446689898b107e17a0236.png","linkUrl":"/gold/","sort":2,"isOpen":true,"change":true},{"name":"推广中心","key":"promotion_center","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e1d2a1ca070842209daaed0935a7444e.png","linkUrl":"/plug","sort":3,"isOpen":true},{"name":"秒合约","key":"trade_tab6","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.03f4cf21fd0204d90901fb274bcc148d9.png","linkUrl":"/trade","sort":4,"isOpen":true},{"name":"理财","key":"financial","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d0c698942bad4c02ac807de56bea46c3.png","linkUrl":"/financial","sort":5,"isOpen":true},{"name":"DeFi挖矿","key":"host_non-collateralized_mining","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0d2dcc84c36494678b47956abe16c2438.png","linkUrl":"/defi","sort":6,"isOpen":true},{"name":"U本位","key":"trade_tab5","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0c82a7b6d9d604768b6f40d484bc93f49.png","linkUrl":"/tradeU","sort":7,"isOpen":false},{"name":"币币交易","key":"trade_tab3","imgUrl":"https://echo-res.oss-cn-hongkong.aliyuncs.com/echo2.0e361119483654d25bfdad32e6e1a262d.png","linkUrl":"/trade?flag=BB&show=onlyB","sort":8,"isOpen":false},{"name":"分享邀请码","key":"share_invite","imgUrl":"","linkUrl":"action:copy_invite_code","sort":9,"isOpen":true}]'
WHERE `id` = 'MIDDLE_MENU_SETTING';
