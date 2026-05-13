-- Backfill reward timestamps that were inserted with NULL values before
-- reward services explicitly populated create_time/update_time.
UPDATE `t_reward_log`
SET
    `create_time` = COALESCE(`create_time`, TIMESTAMP(`biz_date`, '00:00:00')),
    `update_time` = COALESCE(`update_time`, TIMESTAMP(`biz_date`, '00:00:00'))
WHERE (`create_time` IS NULL OR `update_time` IS NULL)
  AND `biz_date` IS NOT NULL;

UPDATE `t_agent_application`
SET
    `create_time` = COALESCE(`create_time`, `review_at`, NOW()),
    `update_time` = COALESCE(`update_time`, `review_at`, `create_time`, NOW())
WHERE `create_time` IS NULL OR `update_time` IS NULL;

UPDATE `t_agent_application`
SET `update_time` = `review_at`
WHERE `review_at` IS NOT NULL
  AND (`update_time` IS NULL OR `update_time` < `review_at`);

UPDATE `t_daily_fee_summary`
SET
    `create_time` = COALESCE(`create_time`, TIMESTAMP(`biz_date`, '00:00:00')),
    `update_time` = COALESCE(`update_time`, TIMESTAMP(`biz_date`, '00:00:00'))
WHERE (`create_time` IS NULL OR `update_time` IS NULL)
  AND `biz_date` IS NOT NULL;

UPDATE `t_gold_withdraw_order`
SET
    `create_time` = COALESCE(`create_time`, `completed_at`, NOW()),
    `update_time` = COALESCE(`update_time`, `completed_at`, `create_time`, NOW())
WHERE `create_time` IS NULL OR `update_time` IS NULL;
