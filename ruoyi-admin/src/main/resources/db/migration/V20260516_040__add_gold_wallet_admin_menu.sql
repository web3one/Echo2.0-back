-- ============================================================================
-- V20260516_040 注入金矿子钱包 + 提现单 + 手续费报表 3 个 admin 菜单（B 路线第一组）
-- ============================================================================
-- 业务变更：
--   配套已落地的 TGoldWalletController / TGoldWithdrawOrderController /
--   TDailyFeeReportController + views/bussiness/goldWallet|goldWithdraw|dailyFeeReport/index.vue，
--   挂到"金矿管理"目录 5200 下面（前序已用到 5254）。
--
--   admin 仅可查询/筛选/导出；不开放调账（暂留 IGoldWalletService.deductBalance 由系统/客服 SQL 兜底）。
--
-- menu_id 分配：
--   5260 钱包管理 + 5261/5262/5263 三个 F（list/query/log）
--   5270 提现单管理 + 5271/5272 两个 F（list/query）
--   5280 手续费报表 + 5281/5282 两个 F（list/query）
-- admin 角色（role_id=1）自动授权全部新菜单。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5260 AND 5289;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5260 AND 5289;

-- ----------- 5260 钱包管理 + F 按钮 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5260, '金矿钱包管理', 5200, 6, 'goldWallet', 'bussiness/goldWallet/index', 1, 0, 'C', '0', '0', 'bussiness:gold:wallet:list', 'money', 'admin', NOW(), 'B 路线第一组：金矿子钱包余额 + 流水');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5261, '金矿钱包 列表',   5260, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:wallet:list',     '#', 'admin', NOW(), ''),
(5262, '金矿钱包 详情',   5260, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:wallet:query',    '#', 'admin', NOW(), ''),
(5263, '金矿钱包 流水',   5260, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:wallet:log:list', '#', 'admin', NOW(), '');

-- ----------- 5270 提现单管理 + F 按钮 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5270, '金矿提现单管理', 5200, 7, 'goldWithdraw', 'bussiness/goldWithdraw/index', 1, 0, 'C', '0', '0', 'bussiness:gold:withdraw:list', 'edit', 'admin', NOW(), '金矿子钱包→现货提现单审计');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5271, '提现单 列表', 5270, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:withdraw:list',  '#', 'admin', NOW(), ''),
(5272, '提现单 详情', 5270, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:withdraw:query', '#', 'admin', NOW(), '');

-- ----------- 5280 手续费报表 + F 按钮 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5280, '手续费报表', 5200, 8, 'dailyFeeReport', 'bussiness/dailyFeeReport/index', 1, 0, 'C', '0', '0', 'bussiness:gold:fee:list', 'chart', 'admin', NOW(), 'spot+contract 分红基数 + 金矿提现费聚合（PRD §11.2 §12.3 cron 数据源）');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5281, '手续费报表 列表', 5280, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:fee:list',  '#', 'admin', NOW(), ''),
(5282, '手续费报表 详情', 5280, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:gold:fee:query', '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部新菜单 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5260 AND 5289
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
