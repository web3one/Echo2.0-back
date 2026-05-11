package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;

import java.math.BigDecimal;

/**
 * 金矿子钱包 Service：所有金矿奖励 USDT 入账 / 提现出账的统一入口。
 *
 * 设计原则：
 * - addBalance / deductBalance 不开新事务，依赖调用方事务（如 reward cron 子事务、withdraw service 事务）
 * - 行级锁 + 余额/流水原子写
 * - idempotentKey UK 兜底（重发抛 DuplicateKeyException）
 *
 * @date 2026-05-15
 */
public interface IGoldWalletService {

    /** 获取或创建子钱包（user_id UK 兜底）。 */
    TGoldWallet getOrCreate(Long userId);

    /** 查（不创建）。 */
    TGoldWallet getByUserId(Long userId);

    /**
     * 入账：金矿奖励 USDT 写入子钱包，并落流水。
     *
     * 调用方必须已在事务内。
     *
     * @param userId          受益人
     * @param amount          入账金额（必须 > 0）
     * @param changeType      TGoldWalletLog.CHANGE_REWARD_*
     * @param bizRefType      TGoldWalletLog.BIZ_REF_REWARD_LOG 等
     * @param bizRefId        关联业务 ID（reward_log.id 等）
     * @param idempotentKey   幂等键（UK 冲突视为重复入账，抛 DuplicateKeyException）
     * @param remark          备注（可选）
     * @return 流水 ID
     */
    Long addBalance(Long userId,
                    BigDecimal amount,
                    String changeType,
                    String bizRefType,
                    String bizRefId,
                    String idempotentKey,
                    String remark);

    /**
     * 出账：从子钱包扣减 USDT（提现 / admin 调账），并落流水。
     *
     * 调用方必须已在事务内。先 FOR UPDATE 锁，再校验余额，再扣。
     *
     * @param userId          所有者
     * @param amount          出账金额（必须 > 0；内部以负数写流水）
     * @param changeType      TGoldWalletLog.CHANGE_WITHDRAW_OUT / ADMIN_ADJUST
     * @param bizRefType      TGoldWalletLog.BIZ_REF_*
     * @param bizRefId        关联业务 ID
     * @param idempotentKey   幂等键
     * @param operatorAdminId admin 调账时填，否则 null
     * @param remark          备注
     * @return 流水 ID（balance < amount 时抛 ServiceException）
     */
    Long deductBalance(Long userId,
                       BigDecimal amount,
                       String changeType,
                       String bizRefType,
                       String bizRefId,
                       String idempotentKey,
                       Long operatorAdminId,
                       String remark);

    /**
     * 退款回账（提现失败回滚）：amount 必须为出账时同金额。
     */
    Long refundBalance(Long userId,
                       BigDecimal amount,
                       String bizRefType,
                       String bizRefId,
                       String idempotentKey,
                       String remark);
}
