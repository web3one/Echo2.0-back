-- Add idempotency protection for ecosystem_credit unlock requests.

ALTER TABLE `t_ecosystem_credit_unlock_log`
    ADD COLUMN `idempotent_key` VARCHAR(128) NULL COMMENT '幂等键 <user_id>:<request_uuid>' AFTER `related_reward_log_id`;

UPDATE `t_ecosystem_credit_unlock_log`
SET `idempotent_key` = CONCAT('legacy:', `id`)
WHERE `idempotent_key` IS NULL OR `idempotent_key` = '';

ALTER TABLE `t_ecosystem_credit_unlock_log`
    MODIFY COLUMN `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键 <user_id>:<request_uuid>',
    ADD UNIQUE KEY `uk_idempotent` (`idempotent_key`);
