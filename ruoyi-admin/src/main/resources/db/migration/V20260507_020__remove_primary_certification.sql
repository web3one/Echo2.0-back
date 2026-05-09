-- ============================================================================
-- V20260507_020 下线"初级认证"，仅保留"实名认证"（H5 侧边栏）
-- ============================================================================
-- 业务变更：
--   1. 不再要求用户先做"初级认证"（primary）才能做"实名认证"（advanced）
--   2. H5 侧边栏不再展示 key='primary' 的初级认证入口
--   3. 后端 /api/user/uploadKYC 已限制 flag 必须为 2（实名）
-- 数据变更（仅 t_setting，不动 t_app_user_detail.audit_status_primary 历史字段）：
--   APP_SIDEBAR_SETTING JSON 中移除 key='primary' 的整条记录
-- ============================================================================

UPDATE `t_setting`
SET `setting_value` = JSON_REMOVE(
        `setting_value`,
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(`setting_value`, 'one', 'primary', NULL, '$[*].key')),
                '.key', '')
    )
WHERE `id` = 'APP_SIDEBAR_SETTING'
  AND JSON_SEARCH(`setting_value`, 'one', 'primary', NULL, '$[*].key') IS NOT NULL;

-- 同步清理字典里的"实名认证（初级）"选项，避免后台再勾选回来
DELETE FROM `sys_dict_data`
WHERE `dict_type` = 'app_sidebar_setting'
  AND `dict_value` = 'app.verified.primary';
