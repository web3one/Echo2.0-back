-- ============================================================================
-- V20260509_024 用户推荐关系闭包表（金矿邀请体系 Phase 0.3）
-- ============================================================================
-- 业务变更：
--   原 t_app_user.app_parent_ids 用逗号分隔字段写死最多 3 级祖先链路（见
--   TAppUserServiceImpl.insertTAppUser 第 130-141 行旧逻辑），无法满足金矿
--   双轨团队 / 团队代理奖 / V1-V5 升级业绩计算所需的"无限级树"查询。
--
--   本迁移建立一张 closure table（user_id × parent_id × depth），把"任一用户
--   到任一祖先"的关系存为一行记录，查询整棵下级 / 任意深度上级都是 O(N) 索引。
--
-- 后续：
--   Phase 0.3 后端会修改 TAppUserServiceImpl.insertTAppUser，
--   新注册用户写入 t_user_relation：自身（depth=0）+ 复制邀请人的所有祖先（depth+1）。
--   保留 t_app_user.app_parent_ids 字段不动，老逻辑仍可读，不破坏兼容性。
--
-- 老数据迁移：
--   注册时 app_parent_ids 顺序：[直接父级, 祖父, 曾祖父]（最多 3 个）
--   分别还原到 depth=1 / 2 / 3。深度 4+ 在老数据里不存在，无需还原。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_user_relation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '后代用户ID（也存自身，depth=0）',
    `parent_id` BIGINT NOT NULL COMMENT '祖先用户ID（自身或任意级祖先）',
    `depth` INT NOT NULL COMMENT '深度：0=自身，1=直接父级，2=祖父级，... 无限级',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关系建立时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_parent` (`user_id`, `parent_id`),
    KEY `idx_parent_depth` (`parent_id`, `depth`) COMMENT '查询某用户的所有下级（按深度过滤）',
    KEY `idx_user_depth` (`user_id`, `depth`) COMMENT '查询某用户的所有上级（按深度过滤）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='用户推荐关系闭包表（金矿邀请体系）';

-- 老数据回填 Step 1：每个用户写入自身关系（depth=0）
INSERT IGNORE INTO `t_user_relation` (`user_id`, `parent_id`, `depth`, `create_time`)
SELECT `user_id`, `user_id`, 0, COALESCE(`create_time`, NOW()) FROM `t_app_user`;

-- 老数据回填 Step 2：直接父级（depth=1，对应 app_parent_ids 第 1 段）
INSERT IGNORE INTO `t_user_relation` (`user_id`, `parent_id`, `depth`, `create_time`)
SELECT
    u.`user_id`,
    CAST(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 1) AS UNSIGNED) AS parent_id,
    1 AS depth,
    COALESCE(u.`create_time`, NOW())
FROM `t_app_user` u
WHERE u.`app_parent_ids` IS NOT NULL
    AND u.`app_parent_ids` != ''
    AND CAST(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 1) AS UNSIGNED) > 0;

-- 老数据回填 Step 3：祖父级（depth=2，对应 app_parent_ids 第 2 段）
INSERT IGNORE INTO `t_user_relation` (`user_id`, `parent_id`, `depth`, `create_time`)
SELECT
    u.`user_id`,
    CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 2), ',', -1) AS UNSIGNED) AS parent_id,
    2 AS depth,
    COALESCE(u.`create_time`, NOW())
FROM `t_app_user` u
WHERE u.`app_parent_ids` IS NOT NULL
    AND CHAR_LENGTH(u.`app_parent_ids`) - CHAR_LENGTH(REPLACE(u.`app_parent_ids`, ',', '')) >= 1
    AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 2), ',', -1) AS UNSIGNED) > 0;

-- 老数据回填 Step 4：曾祖父级（depth=3，对应 app_parent_ids 第 3 段）
INSERT IGNORE INTO `t_user_relation` (`user_id`, `parent_id`, `depth`, `create_time`)
SELECT
    u.`user_id`,
    CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 3), ',', -1) AS UNSIGNED) AS parent_id,
    3 AS depth,
    COALESCE(u.`create_time`, NOW())
FROM `t_app_user` u
WHERE u.`app_parent_ids` IS NOT NULL
    AND CHAR_LENGTH(u.`app_parent_ids`) - CHAR_LENGTH(REPLACE(u.`app_parent_ids`, ',', '')) >= 2
    AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.`app_parent_ids`, ',', 3), ',', -1) AS UNSIGNED) > 0;
