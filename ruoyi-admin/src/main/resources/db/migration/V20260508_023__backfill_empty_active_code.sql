-- ============================================================================
-- V20260508_023 老用户邀请码回填（金矿邀请体系 Phase 0 前置）
-- ============================================================================
-- 业务变更：
--   注册流程已强制邀请码必填（V020 之后版本），但历史上有少数老用户 active_code 为空，
--   导致他们无法被新用户邀请（新用户必须填一个有效邀请码才能注册），等于"不能发展下线"。
-- 处理方式：
--   1. 用 MD5(echo2_invite_v1_<user_id>) 取前 6 位 16 进制字符（大写）作为 active_code
--   2. MD5 输出空间 16M，对几千~几万老用户来说碰撞概率可忽略
--   3. 字符集 [0-9A-F] 是注册自动生成 active_code 字符集 [0-9A-Z] 的子集，格式兼容
--   4. 同时把 MD5 结果与现有 active_code 比对：万一与已有用户冲突，附加 user_id 后两位
--      生成 8 位字符（active_code 字段是 varchar(256)，长度宽松）
-- ============================================================================

-- Step 1：批量回填空值
UPDATE `t_app_user`
SET `active_code` = UPPER(SUBSTRING(MD5(CONCAT('echo2_invite_v1_', `user_id`)), 1, 6)),
    `update_time` = NOW()
WHERE `active_code` IS NULL OR `active_code` = '';

-- Step 2：清理可能与已有 active_code 冲突的记录（保留 user_id 最小的，其余拼接尾号）
-- 注：这步是防御性补救，正常情况下不会触发
UPDATE `t_app_user` u1
JOIN (
    SELECT `active_code`
    FROM `t_app_user`
    WHERE `active_code` IS NOT NULL AND `active_code` != ''
    GROUP BY `active_code`
    HAVING COUNT(*) > 1
) dup ON u1.`active_code` = dup.`active_code`
SET u1.`active_code` = CONCAT(u1.`active_code`, LPAD(u1.`user_id` MOD 100, 2, '0')),
    u1.`update_time` = NOW()
WHERE u1.`user_id` NOT IN (
    SELECT `min_uid` FROM (
        SELECT MIN(`user_id`) AS `min_uid`
        FROM `t_app_user`
        WHERE `active_code` IS NOT NULL AND `active_code` != ''
        GROUP BY `active_code`
        HAVING COUNT(*) > 1
    ) t
);
