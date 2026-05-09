-- ================================================================
-- 修复：实体继承 BaseEntity 但表 schema 漏列
-- BaseEntity 有 create_by / create_time / update_by / update_time / remark 5 个字段
-- 之前 V004 / V005 部分表少建了，导致 MyBatis Plus INSERT 时 SQL 失败
-- ================================================================

-- t_pool_log 加 update_by / update_time（虽然底池流水通常 append-only，
-- 但 BaseEntity 自动填充会塞这两个字段，schema 必须有列）
ALTER TABLE `t_pool_log`
    ADD COLUMN `update_by`   VARCHAR(64) NULL DEFAULT NULL AFTER `create_time`,
    ADD COLUMN `update_time` DATETIME    NULL DEFAULT NULL AFTER `update_by`;

-- t_aggregation_item 加 create_by / update_by / remark
ALTER TABLE `t_aggregation_item`
    ADD COLUMN `create_by` VARCHAR(64)  NULL DEFAULT NULL AFTER `error_msg`,
    ADD COLUMN `update_by` VARCHAR(64)  NULL DEFAULT NULL AFTER `create_by`,
    ADD COLUMN `remark`    VARCHAR(500) NULL DEFAULT NULL AFTER `update_time`;
