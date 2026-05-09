-- ================================================================
-- Phase 1.7: 字典数据 + 清空旧充值/提现数据
-- PM 确认无真实用户，可清表重建
-- 同时插入 sys_dict_type / sys_dict_data，前端选链可直接拉字典
-- ================================================================

-- ----------- 链类型字典 -----------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'chain_type';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'chain_type';

INSERT INTO `sys_dict_type`
(`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
('链类型', 'chain_type', '0', 'admin', NOW(), '充值/提现支持的链');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
(1, 'Ethereum',        'ETH',  'chain_type', '', 'primary', 'N', '0', 'admin', NOW(), 'EVM'),
(2, 'BNB Smart Chain', 'BSC',  'chain_type', '', 'warning', 'N', '0', 'admin', NOW(), 'EVM'),
(3, 'Base',            'BASE', 'chain_type', '', 'info',    'N', '0', 'admin', NOW(), 'EVM'),
(4, 'Tron',            'TRX',  'chain_type', '', 'danger',  'N', '0', 'admin', NOW(), '非 EVM');

-- ----------- 归集状态字典 -----------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'aggregation_status';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'aggregation_status';

INSERT INTO `sys_dict_type`
(`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
('归集任务状态', 'aggregation_status', '0', 'admin', NOW(), '');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`)
VALUES
(1, '待执行',  'PENDING', 'aggregation_status', '', 'info',    'N', '0', 'admin', NOW()),
(2, '执行中',  'RUNNING', 'aggregation_status', '', 'primary', 'N', '0', 'admin', NOW()),
(3, '成功',    'SUCCESS', 'aggregation_status', '', 'success', 'N', '0', 'admin', NOW()),
(4, '部分失败','PARTIAL', 'aggregation_status', '', 'warning', 'N', '0', 'admin', NOW()),
(5, '失败',    'FAILED',  'aggregation_status', '', 'danger',  'N', '0', 'admin', NOW());

-- ----------- 底池流水类型字典 -----------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'pool_change_type';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'pool_change_type';

INSERT INTO `sys_dict_type`
(`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
('底池变动类型', 'pool_change_type', '0', 'admin', NOW(), '');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`)
VALUES
(1, '充值入账', 'RECHARGE', 'pool_change_type', '', 'success', 'N', '0', 'admin', NOW()),
(2, '提现扣减', 'WITHDRAW', 'pool_change_type', '', 'warning', 'N', '0', 'admin', NOW()),
(3, '归集到账', 'AGGREGATE','pool_change_type', '', 'primary', 'N', '0', 'admin', NOW()),
(4, '手工调整', 'MANUAL',   'pool_change_type', '', 'info',    'N', '0', 'admin', NOW());

-- ----------- 清空旧充值/提现数据（PM 确认无真实用户）-----------
TRUNCATE TABLE `t_app_recharge`;
TRUNCATE TABLE `t_withdraw`;

-- 旧的 t_user_symbol_address 不删，但置空（保留表结构防止有遗漏代码引用）
TRUNCATE TABLE `t_user_symbol_address`;
