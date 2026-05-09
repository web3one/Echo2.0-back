-- ================================================================
-- Phase 1.6: 给现有充值/提现表加 chain / tx_hash 字段
-- 现有 t_app_recharge.tx_id 是"第三方支付订单号"，与链 hash 含义不同，保留
-- 新增 chain + tx_hash + from_address + block_number 用于自动到账
-- ================================================================

-- ----------- t_app_recharge 加链上信息 -----------
ALTER TABLE `t_app_recharge`
    ADD COLUMN `chain`         VARCHAR(20)  NULL DEFAULT NULL COMMENT '链：ETH / BSC / BASE / TRX'        AFTER `coin`,
    ADD COLUMN `tx_hash`       VARCHAR(128) NULL DEFAULT NULL COMMENT '链上交易 hash（自动到账场景必填）' AFTER `chain`,
    ADD COLUMN `from_address`  VARCHAR(64)  NULL DEFAULT NULL COMMENT '链上转出方地址'                      AFTER `tx_hash`,
    ADD COLUMN `block_number`  BIGINT       NULL DEFAULT NULL COMMENT '链上区块高度（用于确认数判断）'       AFTER `from_address`,
    ADD COLUMN `confirm_count` INT          NULL DEFAULT 0    COMMENT '当前确认数'                          AFTER `block_number`;

-- 同链同 hash 唯一（幂等防重复入账，链监听 worker 必须）
ALTER TABLE `t_app_recharge`
    ADD UNIQUE KEY `uk_chain_tx_hash` (`chain`, `tx_hash`);

-- 链监听 worker 反查时按 (chain, to_address) 索引
ALTER TABLE `t_app_recharge`
    ADD KEY `idx_chain_to_address` (`chain`, `to_address`);


-- ----------- t_withdraw 加链上信息 -----------
ALTER TABLE `t_withdraw`
    ADD COLUMN `chain`         VARCHAR(20)  NULL DEFAULT NULL COMMENT '链：ETH / BSC / BASE / TRX'         AFTER `coin`,
    ADD COLUMN `tx_hash`       VARCHAR(128) NULL DEFAULT NULL COMMENT '链上转账 hash（审核通过后回填）'      AFTER `chain`,
    ADD COLUMN `block_number`  BIGINT       NULL DEFAULT NULL COMMENT '链上区块高度'                          AFTER `tx_hash`,
    ADD COLUMN `confirm_count` INT          NULL DEFAULT 0    COMMENT '当前确认数'                            AFTER `block_number`;

-- 防止链上重发产生重复 hash
ALTER TABLE `t_withdraw`
    ADD UNIQUE KEY `uk_chain_tx_hash` (`chain`, `tx_hash`);
