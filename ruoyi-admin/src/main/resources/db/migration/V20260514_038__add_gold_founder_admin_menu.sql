-- ============================================================================
-- V20260514_038 注入"创世管理"菜单（金矿 Phase 2 A 路线第二组）
-- ============================================================================
-- 业务变更：
--   配套已落地的 admin 模块 TFounderSeatController + FounderAdminService
--   + views/bussiness/founderSeat/index.vue，把"创世管理"页面挂到金矿管理目录
--   5200 下面（5210/5220/5230 已存在 / 5240 上一轮新增）。
--
--   admin 仅可冻结 / 解冻 / 改备注 / 查购买流水；禁止新增 / 退款（PRD 49 席预创建硬约束）。
--
-- menu_id 5250（接续 5240，order_num=5）。
-- 子权限 F：query / list / freeze / edit（备注）/ log（购买流水查询）。
-- admin 角色（role_id=1）自动授权全部新菜单。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5250 AND 5259;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5250 AND 5259;

-- ----------- 二级菜单 创世管理 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5250, '创世管理', 5200, 5, 'founderSeat', 'bussiness/founderSeat/index', 1, 0, 'C', '0', '0', 'bussiness:founder:seat:list', 'star', 'admin', NOW(), '49 席矩阵 + 列表 + 冻结/解冻/备注 + 购买流水（决策 4 不退款）');

-- ----------- 按钮级权限 F -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5251, '创世席位 查询',     5250, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:founder:seat:query',  '#', 'admin', NOW(), ''),
(5252, '创世席位 冻结/解冻', 5250, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:founder:seat:freeze', '#', 'admin', NOW(), ''),
(5253, '创世席位 改备注',   5250, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:founder:seat:edit',   '#', 'admin', NOW(), ''),
(5254, '创世流水 查询',     5250, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:founder:log:list',    '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部新菜单 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5250 AND 5259
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
