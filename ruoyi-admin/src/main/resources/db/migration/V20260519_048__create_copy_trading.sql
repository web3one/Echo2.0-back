-- ============================================================================
-- V20260519_048 U本位跟单：交易员申请、跟随关系、复制订单、盈利分成
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_copy_trader` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '交易员用户ID',
    `display_name` VARCHAR(64) NULL COMMENT '展示昵称',
    `avatar_url` VARCHAR(255) NULL COMMENT '头像',
    `bio` VARCHAR(500) NULL COMMENT '简介',
    `status` VARCHAR(16) NOT NULL DEFAULT 'approved' COMMENT '状态：approved / disabled',
    `profit_share_rate` DECIMAL(8,6) NOT NULL DEFAULT 0.100000 COMMENT '盈利分成比例',
    `follower_count` INT NOT NULL DEFAULT 0 COMMENT '跟随人数缓存',
    `total_copy_amount` DECIMAL(28,8) NOT NULL DEFAULT 0 COMMENT '跟随资金缓存',
    `total_profit` DECIMAL(28,8) NOT NULL DEFAULT 0 COMMENT '交易员带单累计收益缓存',
    `win_rate` DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT '胜率缓存',
    `review_admin_id` BIGINT NULL COMMENT '审核管理员ID',
    `review_remark` VARCHAR(255) NULL,
    `review_time` DATETIME NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_status_create` (`status`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='U本位跟单交易员';

CREATE TABLE IF NOT EXISTS `t_copy_trader_apply` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `display_name` VARCHAR(64) NULL COMMENT '申请展示昵称',
    `bio` VARCHAR(500) NULL COMMENT '申请简介',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending / approved / rejected',
    `review_admin_id` BIGINT NULL,
    `review_remark` VARCHAR(255) NULL,
    `review_time` DATETIME NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_create` (`status`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='U本位跟单交易员申请';

CREATE TABLE IF NOT EXISTS `t_copy_relation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `follower_user_id` BIGINT NOT NULL COMMENT '跟随者用户ID',
    `trader_user_id` BIGINT NOT NULL COMMENT '交易员用户ID',
    `copy_total_amount` DECIMAL(28,8) NOT NULL COMMENT '跟单总额 USDT',
    `max_single_amount` DECIMAL(28,8) NOT NULL COMMENT '单交易员最大占用 USDT',
    `stop_loss_rate` DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT '止损比例，0.2=20%',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / stopped',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_trader` (`follower_user_id`, `trader_user_id`),
    KEY `idx_trader_status` (`trader_user_id`, `status`),
    KEY `idx_follower_status` (`follower_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='U本位跟单关系';

CREATE TABLE IF NOT EXISTS `t_copy_order` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `relation_id` BIGINT UNSIGNED NOT NULL COMMENT '跟随关系ID',
    `trader_user_id` BIGINT NOT NULL,
    `follower_user_id` BIGINT NOT NULL,
    `trader_position_id` BIGINT NOT NULL COMMENT '交易员仓位ID',
    `follower_position_id` BIGINT NULL COMMENT '跟随者仓位ID',
    `symbol` VARCHAR(32) NOT NULL,
    `type` INT NOT NULL COMMENT '0买多 1卖空',
    `leverage` DECIMAL(18,8) NOT NULL,
    `copy_ratio` DECIMAL(18,10) NOT NULL COMMENT '复制资金比例',
    `trader_margin` DECIMAL(28,8) NOT NULL COMMENT '交易员开仓保证金',
    `follower_margin` DECIMAL(28,8) NOT NULL COMMENT '跟随者开仓保证金',
    `profit_share_rate_snap` DECIMAL(8,6) NOT NULL COMMENT '盈利分成比例快照',
    `open_status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending / success / failed',
    `close_status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending / success / failed / skipped',
    `fail_reason` VARCHAR(255) NULL,
    `close_fail_reason` VARCHAR(255) NULL,
    `follower_earn` DECIMAL(28,8) NULL,
    `profit_share_amount` DECIMAL(28,8) NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_relation_trader_position` (`relation_id`, `trader_position_id`),
    KEY `idx_trader_position` (`trader_position_id`),
    KEY `idx_follower_position` (`follower_position_id`),
    KEY `idx_follower_create` (`follower_user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='U本位跟单复制订单';

CREATE TABLE IF NOT EXISTS `t_copy_profit_share_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `copy_order_id` BIGINT UNSIGNED NOT NULL,
    `trader_user_id` BIGINT NOT NULL,
    `follower_user_id` BIGINT NOT NULL,
    `follower_position_id` BIGINT NOT NULL,
    `gross_profit` DECIMAL(28,8) NOT NULL COMMENT '跟随者盈利',
    `share_rate` DECIMAL(8,6) NOT NULL COMMENT '分成比例',
    `share_amount` DECIMAL(28,8) NOT NULL COMMENT '分成金额',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_copy_order` (`copy_order_id`),
    KEY `idx_trader_create` (`trader_user_id`, `create_time` DESC),
    KEY `idx_follower_create` (`follower_user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='U本位跟单盈利分成流水';

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '跟单盈利分成比例', 'copy.trade.profit_share_rate', '0.10', 'Y', 'admin', NOW(), 'U本位跟单默认盈利分成比例；复制开仓时快照'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'copy.trade.profit_share_rate');

UPDATE `t_setting`
SET `setting_value` = JSON_ARRAY_APPEND(
    `setting_value`,
    '$',
    JSON_OBJECT('name','跟单','key','copy_trading','imgUrl','','linkUrl','/copy-trading','sort',10,'isOpen',true)
)
WHERE `id` = 'MIDDLE_MENU_SETTING'
  AND JSON_SEARCH(`setting_value`, 'one', 'copy_trading', NULL, '$[*].key') IS NULL;

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 5300 AND 5399;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5300 AND 5399;

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5300, '跟单管理', 0, 53, 'copyTrading', NULL, 1, 0, 'M', '0', '0', '', 'peoples', 'admin', NOW(), 'U本位跟单管理');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5310, '交易员申请', 5300, 1, 'copyTraderApplications', 'bussiness/copy/traderApplications/index', 1, 0, 'C', '0', '0', 'bussiness:copy:trader:application:list', 'user', 'admin', NOW(), ''),
(5320, '交易员管理', 5300, 2, 'copyTraders', 'bussiness/copy/traders/index', 1, 0, 'C', '0', '0', 'bussiness:copy:trader:list', 'people', 'admin', NOW(), '');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
(5311, '交易员申请 查询', 5310, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:copy:trader:application:query', '#', 'admin', NOW(), ''),
(5312, '交易员申请 通过', 5310, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:copy:trader:application:approve', '#', 'admin', NOW(), ''),
(5313, '交易员申请 拒绝', 5310, 3, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:copy:trader:application:reject', '#', 'admin', NOW(), ''),
(5321, '交易员 查询', 5320, 1, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:copy:trader:query', '#', 'admin', NOW(), ''),
(5322, '交易员 启停', 5320, 2, '', NULL, 1, 0, 'F', '0', '0', 'bussiness:copy:trader:status', '#', 'admin', NOW(), '');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 5300 AND 5399
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = `sys_menu`.`menu_id`);
