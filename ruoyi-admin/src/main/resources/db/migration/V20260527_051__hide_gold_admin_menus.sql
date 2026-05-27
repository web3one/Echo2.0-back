-- Hide the gold mine admin menu tree without deleting historical menu records.
-- RuoYi uses visible='0' for shown and visible='1' for hidden.

UPDATE `sys_menu`
SET `visible` = '1',
    `update_by` = 'admin',
    `update_time` = NOW()
WHERE `menu_id` = 5200
   OR `parent_id` = 5200
   OR `parent_id` IN (
       SELECT `menu_id`
       FROM (
           SELECT `menu_id`
           FROM `sys_menu`
           WHERE `parent_id` = 5200
       ) AS gold_child_menu
   );
