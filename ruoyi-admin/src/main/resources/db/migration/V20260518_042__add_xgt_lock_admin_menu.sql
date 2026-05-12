-- ============================================================================
-- V20260518_042 注入 XGT 锁仓管理 admin 菜单（金矿 P1 收尾 §15.5）
-- ============================================================================
-- 业务变更：
--   配套已落地的 TXgtLockPlanController + XgtLockAdminServiceImpl
--   + views/bussiness/xgtLockPlan/index.vue，把"XGT 锁仓管理"挂到金矿管理目录
--   5200 下面（前序已用到 5282）。
--
--   覆盖 PRD §15.5 全部 10 个 admin 能力：list/query/create/freeze/unfreeze/retry/export。
--   admin 仅可手工创建 team_advisor / private_sale / ecosystem_fund / partner 来源；
--   static_reward / founder_seat / credit_unlock 由 cron / 业务流程自动创建，禁止 admin 重复。
--
-- menu_id 分配：
--   5290 XGT 锁仓管理
--   5291 list  (bussiness:gold:xgtLock:list)
--   5292 query (bussiness:gold:xgtLock:query)
--   5293 create (bussiness:gold:xgtLock:create)
--   5294 freeze (bussiness:gold:xgtLock:freeze)  -- 冻结+解冻共用同一权限
--   5295 retry (bussiness:gold:xgtLock:retry)
--   5296 export (bussiness:gold:xgtLock:export)
-- admin 角色（role_id=1）自动授权全部新菜单。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5290 AND 5299;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5290 AND 5299;

-- ----------- 5290 主菜单 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5290, 'XGT 锁仓管理', 5200, 9, 'xgtLockPlan', 'bussiness/xgtLockPlan/index', 1, 0, 'C', '0', '0', 'bussiness:gold:xgtLock:list', 'lock', 'admin', NOW(), 'PRD §15.5: 创建/查看/冻结/恢复/手动补发/导出');

-- ----------- F 按钮权限 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5291, 'XGT 锁仓 列表',     5290, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:list',   '#', 'admin', NOW(), ''),
(5292, 'XGT 锁仓 详情',     5290, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:query',  '#', 'admin', NOW(), ''),
(5293, 'XGT 锁仓 创建',     5290, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:create', '#', 'admin', NOW(), '仅 team_advisor / private_sale / ecosystem_fund / partner'),
(5294, 'XGT 锁仓 冻结/解冻', 5290, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:freeze', '#', 'admin', NOW(), ''),
(5295, 'XGT 锁仓 手动补发',  5290, 5, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:retry',  '#', 'admin', NOW(), '调 releaseOnePlan，仅 locked 且已到期可触发'),
(5296, 'XGT 锁仓 导出',     5290, 6, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:xgtLock:export', '#', 'admin', NOW(), 'Excel 导出，cap 5000');

-- ----------- 给 admin 角色（role_id=1）授权全部新菜单 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5290 AND 5299
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
