CREATE TABLE IF NOT EXISTS `t_tradfi_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int DEFAULT NULL COMMENT '0买入 1卖出',
  `delegate_type` int DEFAULT NULL COMMENT '0限价 1市价',
  `status` int DEFAULT NULL COMMENT '0等待成交 1完全成交 3已撤销',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `symbol` varchar(64) DEFAULT NULL COMMENT 'TradFi标的',
  `coin` varchar(32) DEFAULT 'usdt' COMMENT '结算币种',
  `fee` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '手续费',
  `delegate_total` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '委托总量',
  `delegate_price` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '委托价格',
  `deal_num` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '已成交量',
  `deal_price` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '成交价',
  `delegate_value` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '委托价值',
  `deal_value` decimal(32, 12) DEFAULT 0.000000000000 COMMENT '成交价值',
  `delegate_time` datetime DEFAULT NULL COMMENT '委托时间',
  `deal_time` datetime DEFAULT NULL COMMENT '成交时间',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `search_value` varchar(255) DEFAULT NULL COMMENT '搜索值',
  `admin_parent_ids` varchar(1024) DEFAULT NULL COMMENT '后台代理id',
  PRIMARY KEY (`id`),
  KEY `idx_tradfi_order_user_status` (`user_id`, `status`),
  KEY `idx_tradfi_order_order_no` (`order_no`),
  KEY `idx_tradfi_order_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TradFi交易订单';

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5116, 'TradFi订单', 5100, 2, 'tradfi-order', 'currency/tradfiOrder/index', 1, 0, 'C', '0', '0', 'bussiness:tradfi:order:list', 'list', 'admin', NOW(), 'TradFi交易订单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5116);

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5117, 'TradFi订单查询', 5116, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:order:query', '#', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5117);

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5118, 'TradFi订单新增', 5116, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:order:add', '#', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5118);

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5119, 'TradFi订单修改', 5116, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:order:edit', '#', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5119);

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5120, 'TradFi订单删除', 5116, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:order:remove', '#', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5120);

INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 5121, 'TradFi订单导出', 5116, 5, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:order:export', '#', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 5121);
