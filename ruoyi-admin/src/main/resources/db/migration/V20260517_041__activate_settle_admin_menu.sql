-- ============================================================================
-- V20260517_041 激活"结算任务监控"admin 前端页 + 补 list 按钮权限
-- ============================================================================
-- C-3 收尾：echo2.0-admin/src/views/bussiness/settle/index.vue 已建，把 V035
-- 时 visible='1' 隐藏的菜单改回 '0' 可见；同时补 list 权限按钮（V035 当时只
-- 建了 query/retry，controller 实际还有 bussiness:settle:list 用于 GET /list）。
-- ============================================================================

UPDATE `sys_menu` SET `visible` = '0', `update_time` = NOW(), `remark` = '金矿 6 个 cron 结算日志 + admin retry，C-3 收尾激活'
WHERE `menu_id` = 5240;

DELETE FROM `sys_role_menu` WHERE `menu_id` = 5243;
DELETE FROM `sys_menu` WHERE `menu_id` = 5243;

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5243, '结算任务 列表', 5240, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:settle:list', '#', 'admin', NOW(), '');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, 5243
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = 5243);
