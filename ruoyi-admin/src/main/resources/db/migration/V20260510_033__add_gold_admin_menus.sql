-- ============================================================================
-- V20260510_033 注入"金矿管理"admin 菜单 + 8 个权限标识
-- ============================================================================
-- 业务变更：
--   配套已落地的 admin 模块（V20260510_026~032 schema + 后端 Java + 前端 Vue），
--   把 3 个新页面挂到 admin 后台菜单。客服上线后立刻可用，不需要手动配菜单。
--
-- menu_id 区段 5200~5299 避免冲突：
--   - 5000~5099 资金管理（V20260427_008）
--   - 5100~5199 行情管理 / TradFi（V20260429_012）
--   - 5200~5299 金矿管理 ← 本迁移
--
-- 菜单层级：
--   5200 金矿管理（一级目录 M）
--     5210 代理等级管理（二级菜单 C，对应 views/bussiness/agentStatus/index.vue）
--       5211 改等级（F：bussiness:agent:status:edit）
--       5212 冻结/解冻（F：bussiness:agent:status:freeze）
--     5220 矿机等级配置（C，对应 views/bussiness/node/level/index.vue）
--       5221 查询（F：bussiness:node:level:query）
--       5222 新增（F：bussiness:node:level:add）
--       5223 修改（F：bussiness:node:level:edit）
--       5224 删除（F：bussiness:node:level:remove）
--     5230 矿机实例管理（C，对应 views/bussiness/node/instance/index.vue）
--       5231 查询（F：bussiness:node:instance:query）
--       5232 冻结/解冻（F：bussiness:node:instance:freeze）
--
-- 注意：menu 自身的 perms 字段直接用 :list 作为"是否能看到此菜单"的总开关；
-- 子按钮的 F 类是"在该页面里能否点对应按钮"。两层权限独立。
-- admin 角色（role_id=1）自动授权全部，客服角色由人工分配。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5200 AND 5299;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5200 AND 5299;

-- ----------- 一级目录 金矿管理 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5200, '金矿管理', 0, 52, 'gold', NULL, 1, 0, 'M', '0', '0', '', 'guide', 'admin', NOW(), 'Web3 AI Compute Gold Rush 后台 / PRD §15');

-- ----------- 二级菜单 -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5210, '代理等级管理',   5200, 1, 'agentStatus',   'bussiness/agentStatus/index',   1, 0, 'C', '0', '0', 'bussiness:agent:status:query',  'people',   'admin', NOW(), '决策 3 通道 2：客服直改 / 冻结 / 解冻'),
(5220, '矿机等级配置',   5200, 2, 'nodeLevel',     'bussiness/node/level/index',    1, 0, 'C', '0', '0', 'bussiness:node:level:list',     'tool',     'admin', NOW(), 'L1-L4 价格 / 收益率 / 出局倍数 / 启停'),
(5230, '矿机实例管理',   5200, 3, 'nodeInstance',  'bussiness/node/instance/index', 1, 0, 'C', '0', '0', 'bussiness:node:instance:list',  'monitor',  'admin', NOW(), '查询 + 出局进度 + 冻结 / 解冻（禁止物理删除）');

-- ----------- 按钮级权限 F -----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
-- 代理等级管理子权限
(5211, '代理等级 改等级',   5210, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:status:edit',   '#', 'admin', NOW(), ''),
(5212, '代理等级 冻结/解冻', 5210, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:agent:status:freeze', '#', 'admin', NOW(), ''),

-- 矿机等级配置子权限
(5221, '矿机等级 查询', 5220, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:level:query',  '#', 'admin', NOW(), ''),
(5222, '矿机等级 新增', 5220, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:level:add',    '#', 'admin', NOW(), ''),
(5223, '矿机等级 修改', 5220, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:level:edit',   '#', 'admin', NOW(), ''),
(5224, '矿机等级 删除', 5220, 4, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:level:remove', '#', 'admin', NOW(), ''),

-- 矿机实例管理子权限
(5231, '矿机实例 查询',     5230, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:instance:query',  '#', 'admin', NOW(), ''),
(5232, '矿机实例 冻结/解冻', 5230, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:node:instance:freeze', '#', 'admin', NOW(), '');

-- ----------- 给 admin 角色（role_id=1）授权全部 -----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5200 AND 5299
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
