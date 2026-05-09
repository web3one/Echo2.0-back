-- ================================================================
-- 注入 "行情管理 → TradFi 标的" 菜单 + 权限
-- menu_id 区段 5100~5199 避免和资金管理（5000~5099）冲突
-- 给 admin 角色（role_id=1）自动授权
-- ================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5100 AND 5199;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5100 AND 5199;

-- ----------- 一级目录 行情管理 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5100, '行情管理', 0, 51, 'market', NULL, 1, 0, 'M', '0', '0', '', 'chart', 'admin', NOW(), '行情数据源 + 标的管理');

-- ----------- 二级菜单 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5110, 'TradFi 标的', 5100, 1, 'tradfi', 'bussiness/tradfi/index', 1, 0, 'C', '0', '0', 'bussiness:tradfi:list', 'list', 'admin', NOW(), '股票/指数/外汇/贵金属配置');

-- ----------- 按钮级权限 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5111, 'TradFi 查询', 5110, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:query',  '#', 'admin', NOW(), ''),
(5112, 'TradFi 新增', 5110, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:add',    '#', 'admin', NOW(), ''),
(5113, 'TradFi 修改', 5110, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:edit',   '#', 'admin', NOW(), ''),
(5114, 'TradFi 删除', 5110, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:remove', '#', 'admin', NOW(), ''),
(5115, 'TradFi 导出', 5110, 5, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:tradfi:export', '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5100 AND 5199
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = `sys_menu`.`menu_id`);
