package com.ruoyi.bussiness.service;

import java.math.BigDecimal;

public interface IC2cEscrowService {
    /** Freeze user's asset (available -> occupied) */
    void freezeAsset(Long userId, String symbol, BigDecimal amount, String serialId, String adminParentIds);
    /** Release escrowed asset from seller to buyer */
    void releaseAsset(Long sellerUserId, Long buyerUserId, String symbol, BigDecimal amount, String serialId, String sellerAdminParentIds, String buyerAdminParentIds);
    /** Unfreeze asset back to available (on cancel) */
    void unfreezeAsset(Long userId, String symbol, BigDecimal amount, String serialId, String adminParentIds);
}
