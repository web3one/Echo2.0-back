-- ================================================================
-- Phase 7：注入"资金管理"菜单 + 权限
-- 用大数字 menu_id（5000+）避免和现有菜单冲突
-- 给 admin 角色（role_id=1）自动授权
-- ================================================================

-- 先清掉历史残留（重复执行幂等保险）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5000 AND 5099;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5000 AND 5099;

-- ----------- 一级目录 资金管理 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5000, '资金管理', 0, 50, 'fund', NULL, 1, 0, 'M', '0', '0', '', 'money', 'admin', NOW(), '链上资金管理目录');

-- ----------- 二级菜单 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5010, '链配置',     5000, 1, 'chainConfig',    'bussiness/fund/chainConfig',    1, 0, 'C', '0', '0', 'bussiness:chain:list',       'chain',    'admin', NOW(), '4 条链 RPC/合约/确认数/开关'),
(5020, '平台钱包',   5000, 2, 'platformWallet', 'bussiness/fund/platformWallet', 1, 0, 'C', '0', '0', 'bussiness:wallet:list',      'wallet',   'admin', NOW(), '主/热/冷钱包'),
(5030, '底池监控',   5000, 3, 'poolMonitor',    'bussiness/fund/poolMonitor',    1, 0, 'C', '0', '0', 'bussiness:pool:list',        'monitor',  'admin', NOW(), '按币种+链显示底池余额'),
(5040, '底池流水',   5000, 4, 'poolLog',        'bussiness/fund/poolLog',        1, 0, 'C', '0', '0', 'bussiness:pool:list',        'log',      'admin', NOW(), '底池变动记录'),
(5050, '归集任务',   5000, 5, 'aggregation',    'bussiness/fund/aggregation',    1, 0, 'C', '0', '0', 'bussiness:aggregation:list', 'task',     'admin', NOW(), '每日归集任务列表');

-- ----------- 按钮级权限（F 类型，给"编辑/调整/触发"按钮用）-----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5011, '链配置编辑',     5010, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:chain:edit',       '#', 'admin', NOW(), ''),
(5021, '钱包新增/编辑',   5020, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:wallet:edit',      '#', 'admin', NOW(), ''),
(5022, '钱包删除',        5020, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:wallet:remove',    '#', 'admin', NOW(), ''),
(5031, '底池手工调整',    5030, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:pool:edit',        '#', 'admin', NOW(), ''),
(5051, '归集手动触发',    5050, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:aggregation:trigger','#','admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5000 AND 5099
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = `sys_menu`.`menu_id`);
