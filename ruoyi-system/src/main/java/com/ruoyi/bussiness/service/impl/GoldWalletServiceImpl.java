package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.mapper.TGoldWalletLogMapper;
import com.ruoyi.bussiness.mapper.TGoldWalletMapper;
import com.ruoyi.bussiness.service.IGoldWalletService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 金矿子钱包 Service 实现。
 *
 * 不开新事务，依赖外层事务（reward cron 子事务 / withdraw service 事务）。
 *
 * @date 2026-05-15
 */
@Service
@Slf4j
public class GoldWalletServiceImpl implements IGoldWalletService {

    @Resource
    private TGoldWalletMapper goldWalletMapper;
    @Resource
    private TGoldWalletLogMapper goldWalletLogMapper;

    @Override
    public TGoldWallet getOrCreate(Long userId) {
        TGoldWallet w = goldWalletMapper.selectByUserId(userId);
        if (w != null) return w;
        w = new TGoldWallet();
        w.setUserId(userId);
        w.setUsdtBalance(BigDecimal.ZERO);
        w.setUsdtTotalIn(BigDecimal.ZERO);
        w.setUsdtTotalOut(BigDecimal.ZERO);
        try {
            goldWalletMapper.insert(w);
        } catch (DuplicateKeyException e) {
            w = goldWalletMapper.selectByUserId(userId);
        }
        return w;
    }

    @Override
    public TGoldWallet getByUserId(Long userId) {
        return goldWalletMapper.selectByUserId(userId);
    }

    @Override
    public Long addBalance(Long userId,
                           BigDecimal amount,
                           String changeType,
                           String bizRefType,
                           String bizRefId,
                           String idempotentKey,
                           String remark) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("gold.wallet.invalid_amount"));
        }
        // 1. 确保 row 存在（UK 兜底）
        getOrCreate(userId);

        // 2. FOR UPDATE 锁
        TGoldWallet w = goldWalletMapper.selectByUserIdForUpdate(userId);
        BigDecimal before = nz(w.getUsdtBalance());
        BigDecimal after = before.add(amount);

        // 3. UPDATE 余额 + 累计入账
        TGoldWallet upd = new TGoldWallet();
        upd.setId(w.getId());
        upd.setUsdtBalance(after);
        upd.setUsdtTotalIn(nz(w.getUsdtTotalIn()).add(amount));
        goldWalletMapper.updateById(upd);

        // 4. INSERT 流水（idempotentKey UK 冲突 → DuplicateKeyException 视为重复入账）
        TGoldWalletLog log = new TGoldWalletLog();
        log.setUserId(userId);
        log.setChangeType(changeType);
        log.setAmountUsdt(amount);
        log.setBalanceBefore(before);
        log.setBalanceAfter(after);
        log.setBizRefType(bizRefType);
        log.setBizRefId(bizRefId);
        log.setIdempotentKey(idempotentKey);
        log.setRemark(remark);
        goldWalletLogMapper.insert(log);
        return log.getId();
    }

    @Override
    public Long deductBalance(Long userId,
                              BigDecimal amount,
                              String changeType,
                              String bizRefType,
                              String bizRefId,
                              String idempotentKey,
                              Long operatorAdminId,
                              String remark) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("gold.wallet.invalid_amount"));
        }
        // 1. FOR UPDATE 锁
        TGoldWallet w = goldWalletMapper.selectByUserIdForUpdate(userId);
        if (w == null) {
            throw new ServiceException(MessageUtils.message("gold.wallet.insufficient"));
        }
        BigDecimal before = nz(w.getUsdtBalance());
        if (before.compareTo(amount) < 0) {
            throw new ServiceException(MessageUtils.message("gold.wallet.insufficient"));
        }
        BigDecimal after = before.subtract(amount);

        // 2. UPDATE 余额 + 累计出账
        TGoldWallet upd = new TGoldWallet();
        upd.setId(w.getId());
        upd.setUsdtBalance(after);
        upd.setUsdtTotalOut(nz(w.getUsdtTotalOut()).add(amount));
        goldWalletMapper.updateById(upd);

        // 3. INSERT 流水（出账 amount 用负数）
        TGoldWalletLog log = new TGoldWalletLog();
        log.setUserId(userId);
        log.setChangeType(changeType);
        log.setAmountUsdt(amount.negate());
        log.setBalanceBefore(before);
        log.setBalanceAfter(after);
        log.setBizRefType(bizRefType);
        log.setBizRefId(bizRefId);
        log.setIdempotentKey(idempotentKey);
        log.setOperatorAdminId(operatorAdminId);
        log.setRemark(remark);
        goldWalletLogMapper.insert(log);
        return log.getId();
    }

    @Override
    public Long refundBalance(Long userId,
                              BigDecimal amount,
                              String bizRefType,
                              String bizRefId,
                              String idempotentKey,
                              String remark) {
        return addBalance(userId, amount,
                TGoldWalletLog.CHANGE_REFUND,
                bizRefType, bizRefId, idempotentKey, remark);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
