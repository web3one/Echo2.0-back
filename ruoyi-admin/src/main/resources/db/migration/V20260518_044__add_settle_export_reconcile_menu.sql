-- ============================================================================
-- V20260518_044 给"结算任务监控"加 export / reconcile 两个 F 权限（金矿 P1 收尾 §15.6）
-- ============================================================================
-- 业务变更：
--   TSettleLogController 新增 POST /export（PRD §15.6 第 6 条 "导出任务日志"）
--   和 GET /reconcile（PRD §15.6 第 7 条 "财务对账"）。
--   配套 admin vue settle/index.vue 已加导出按钮 + 每日对账卡片。
--   menu_id 5244 / 5245（接续 V20260517_041 已用到 5243）。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (5244, 5245);
DELETE FROM `sys_menu` WHERE `menu_id` IN (5244, 5245);

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5244, '结算任务 导出', 5240, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:settle:export', '#', 'admin', NOW(), 'PRD §15.6 第 6 条'),
(5245, '财务对账',     5240, 5, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:settle:reconcile', '#', 'admin', NOW(), 'PRD §15.6 第 7 条 + §21.1 资金池告警');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.menu_id FROM `sys_menu` m
WHERE m.`menu_id` IN (5244, 5245)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.menu_id);
