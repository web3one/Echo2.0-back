-- H5 用户 UID 顺序号生成器。
-- 新注册 / 后台新增用户从 198543732 开始顺序领取 user_id，避免继续暴露 3 位自增 ID。

CREATE TABLE IF NOT EXISTS `t_app_user_id_sequence` (
    `id` TINYINT NOT NULL COMMENT '固定为 1',
    `next_user_id` BIGINT NOT NULL COMMENT '下一个可领取的用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App用户ID顺序号';

INSERT IGNORE INTO `t_app_user_id_sequence` (`id`, `next_user_id`, `create_time`, `update_time`)
VALUES (1, 198543732, NOW(), NOW());
