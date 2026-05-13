-- ============================================================================
-- V20260518_047 直推奖业务修订：100% USDT，不再拆 ecosystem_credit / XGT
-- ============================================================================
-- 说明：
--   旧逻辑把 referral 与 team 共用 70/30 动态奖分账。业务确认后，直推奖
--   应 100% 入金矿 USDT 子钱包，不产生 XGT，也不产生 ecosystem_credit。
--
--   本迁移只修正历史 referral reward_log 中已有的 30% ecosystem_credit：
--   1. reward_log：usdt_credited += eco_credit_amount，eco_credit_amount = 0
--   2. gold_wallet：补回对应 USDT 入账与余额
--   3. ecosystem_credit_balance：扣回 referral 误入的 locked credit
--   4. gold_wallet_log：写一条幂等补账流水便于审计
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS tmp_referral_100pct_usdt;

CREATE TEMPORARY TABLE tmp_referral_100pct_usdt AS
SELECT
    user_id,
    SUM(eco_credit_amount) AS delta_usdt
FROM t_reward_log
WHERE reward_type = 'referral'
  AND status = 'settled'
  AND eco_credit_amount > 0
GROUP BY user_id;

INSERT INTO t_gold_wallet (
    user_id, usdt_balance, usdt_total_in, usdt_total_out, create_time, update_time
)
SELECT
    t.user_id, 0, 0, 0, NOW(), NOW()
FROM tmp_referral_100pct_usdt t
WHERE t.delta_usdt > 0
  AND NOT EXISTS (
      SELECT 1 FROM t_gold_wallet w WHERE w.user_id = t.user_id
  );

UPDATE t_gold_wallet w
JOIN tmp_referral_100pct_usdt t ON t.user_id = w.user_id
SET
    w.usdt_balance = w.usdt_balance + t.delta_usdt,
    w.usdt_total_in = w.usdt_total_in + t.delta_usdt,
    w.update_time = NOW()
WHERE t.delta_usdt > 0;

INSERT INTO t_gold_wallet_log (
    user_id,
    change_type,
    amount_usdt,
    balance_before,
    balance_after,
    biz_ref_type,
    biz_ref_id,
    idempotent_key,
    remark,
    create_time
)
SELECT
    t.user_id,
    'reward_referral',
    t.delta_usdt,
    w.usdt_balance - t.delta_usdt,
    w.usdt_balance,
    'reward_log',
    'direct_referral_100pct_migration',
    CONCAT('referral_100pct_usdt:', t.user_id),
    '直推奖规则修正：历史30%生态额度改为100% USDT',
    NOW()
FROM tmp_referral_100pct_usdt t
JOIN t_gold_wallet w ON w.user_id = t.user_id
WHERE t.delta_usdt > 0
  AND NOT EXISTS (
      SELECT 1
      FROM t_gold_wallet_log l
      WHERE l.idempotent_key = CONCAT('referral_100pct_usdt:', t.user_id)
  );

UPDATE t_ecosystem_credit_balance b
JOIN tmp_referral_100pct_usdt t ON t.user_id = b.user_id
LEFT JOIN (
    SELECT user_id, SUM(amount_credit) AS amount_in_progress
    FROM t_ecosystem_credit_unlock_log
    WHERE status = 'in_progress'
    GROUP BY user_id
) p ON p.user_id = b.user_id
SET
    b.balance_locked = GREATEST(
        COALESCE(p.amount_in_progress, 0),
        b.balance_locked - t.delta_usdt
    ),
    b.update_time = NOW()
WHERE t.delta_usdt > 0;

UPDATE t_reward_log
SET
    usdt_credited = usdt_credited + eco_credit_amount,
    eco_credit_amount = 0,
    update_time = NOW()
WHERE reward_type = 'referral'
  AND status = 'settled'
  AND eco_credit_amount > 0;

ALTER TABLE t_reward_log
    MODIFY COLUMN eco_credit_amount DECIMAL(28, 8) NOT NULL DEFAULT 0
    COMMENT '生态额度入账（团队代理奖 30% 用；直推奖为 0）';

ALTER TABLE t_ecosystem_credit_balance
    MODIFY COLUMN balance_locked DECIMAL(28, 8) NOT NULL DEFAULT 0
    COMMENT '未解锁的 credit 累计（团队代理奖 30% 部分进这里）';

DROP TEMPORARY TABLE IF EXISTS tmp_referral_100pct_usdt;
