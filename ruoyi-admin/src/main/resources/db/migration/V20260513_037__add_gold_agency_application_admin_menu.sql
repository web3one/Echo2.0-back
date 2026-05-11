-- ============================================================================
-- V20260513_037 注入"代理申请审核"菜单（金矿 Phase 2 A2，路线 A 第一组）
-- ============================================================================
-- 业务变更：
--   配套已落地的 admin 模块 TAgentApplicationController + AgencyReviewService
--   + views/bussiness/agentApplication/index.vue，把"代理申请审核"页面挂到
--   金矿管理目录 5200 下面（已存在 5210 代理等级 / 5220 矿机等级 / 5230 矿机实例）。
--
-- 客服上线后立即能看到，用于决策 3 通道 1：H5 申请 → admin 审核 → 通过则
-- IAgentStatusService.changeAgentLevel(source='user_apply')。
--
-- menu_id 5240（接续 5230，order_num=4）。
-- 子权限 F：query / list / approve / reject。
-- admin 角色（role_id=1）自动授权全部新菜单。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5240 AND 5249;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5240 AND 5249;

-- ----------- 二级菜单 代理申请审核 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5240, '代理申请审核', 5200, 4, 'agentApplication', 'bussiness/agentApplication/index', 1, 0, 'C', '0', '0', 'bussiness:agent:application:list', 'edit', 'admin', NOW(), '决策 3 通道 1：H5 申请 V1-V5 + admin 审核');

-- ----------- 按钮级权限 F -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5241, '代理申请 查询', 5240, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:application:query',   '#', 'admin', NOW(), ''),
(5242, '代理申请 列表', 5240, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:application:list',    '#', 'admin', NOW(), ''),
(5243, '代理申请 通过', 5240, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:application:approve', '#', 'admin', NOW(), ''),
(5244, '代理申请 拒绝', 5240, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:application:reject',  '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部新菜单 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5240 AND 5249
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
