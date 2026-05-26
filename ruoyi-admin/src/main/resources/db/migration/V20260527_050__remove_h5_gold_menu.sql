-- Remove the H5 Gold Mine shortcut from the home middle menu.
UPDATE `t_setting`
SET `setting_value` = JSON_REMOVE(
    `setting_value`,
    REPLACE(
        JSON_UNQUOTE(JSON_SEARCH(`setting_value`, 'one', 'download_center', NULL, '$[*].key')),
        '.key',
        ''
    )
)
WHERE `id` = 'MIDDLE_MENU_SETTING'
  AND JSON_SEARCH(`setting_value`, 'one', 'download_center', NULL, '$[*].key') IS NOT NULL;

