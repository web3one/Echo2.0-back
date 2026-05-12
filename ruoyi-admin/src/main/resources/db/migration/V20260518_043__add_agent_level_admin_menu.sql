-- ============================================================================
-- V20260518_043 注入代理等级配置 admin 菜单（金矿 P1 收尾 §15.3）
-- ============================================================================
-- 业务变更：
--   配套已落地的 TAgentLevelController + AgentLevelAdminServiceImpl
--   + views/bussiness/agentLevel/index.vue，把"代理等级配置"挂到金矿管理目录
--   5200 下面（前序已用到 5296）。
--
--   admin 仅可修改 V0-V5 参数（match_rate / global_dividend_rate / 升级门槛
--   等），不允许新增/删除等级行。修改即时生效，团队代理奖 cron / V4V5 全网
--   分红 cron 下次执行会自动读新值。
--
-- menu_id 分配：
--   5300 代理等级配置（主菜单）
--   5301 list  (bussiness:gold:agentLevel:list)
--   5302 query (bussiness:gold:agentLevel:query)
--   5303 edit  (bussiness:gold:agentLevel:edit)
-- admin 角色（role_id=1）自动授权全部新菜单。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5300 AND 5309;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5300 AND 5309;

-- ----------- 5300 主菜单 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5300, '代理等级配置', 5200, 10, 'agentLevel', 'bussiness/agentLevel/index', 1, 0, 'C', '0', '0', 'bussiness:gold:agentLevel:list', 'tree-table', 'admin', NOW(), 'PRD §15.3: V0-V5 匹配率 / 全网分红率 / 升级门槛');

-- ----------- F 按钮权限 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5301, '代理等级 列表', 5300, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:agentLevel:list',  '#', 'admin', NOW(), ''),
(5302, '代理等级 详情', 5300, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:agentLevel:query', '#', 'admin', NOW(), ''),
(5303, '代理等级 修改', 5300, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:agentLevel:edit',  '#', 'admin', NOW(), '禁止新增/删除等级行，仅可修改参数');

-- ----------- 给 admin 角色（role_id=1）授权全部新菜单 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5300 AND 5309
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
