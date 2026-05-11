-- ============================================================================
-- V20260512_035 注入"结算任务监控"admin 菜单（金矿 cron 重试入口）
-- ============================================================================
-- 业务变更：
--   配套 6 个金矿定时任务（PRD §16）+ admin retry 接口（POST /bussiness/settle/retry/{job}/{date}），
--   把"结算任务监控"页挂到金矿管理目录下。客服可看 t_settle_log 列表 + 失败重试。
--
-- menu_id：5240（5200 金矿管理目录下，5210/5220/5230 已用，5240 顺位）
--   5240 结算任务监控（C，对应 views/bussiness/settle/index.vue，前端页本期不做）
--     5241 查询（F：bussiness:settle:query）
--     5242 重试（F：bussiness:settle:retry）
--
-- 注意：前端 views/bussiness/settle/index.vue 本次未实装，菜单 visible='1'（隐藏）
--   等下个迭代加前端页时把 visible 改回 '0'。
--   但权限 perms 已生效——admin 可通过 swagger / postman 直接调用接口。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5240 AND 5249;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5240 AND 5249;

-- ----------- 二级菜单 ：结算任务监控 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5240, '结算任务监控', 5200, 4, 'settle', 'bussiness/settle/index', 1, 0, 'C', '1', '0', 'bussiness:settle:list', 'log', 'admin', NOW(), '金矿 6 个 cron 结算日志 + admin retry，前端页待补');

-- ----------- 按钮级权限 F -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5241, '结算任务 查询', 5240, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:settle:query', '#', 'admin', NOW(), ''),
(5242, '结算任务 重试', 5240, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:settle:retry', '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5240 AND 5249
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
