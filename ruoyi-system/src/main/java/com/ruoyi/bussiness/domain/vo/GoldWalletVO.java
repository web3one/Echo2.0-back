package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TGoldWallet;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金矿子钱包余额 + 当前提现费率（GET /api/gold/wallet 响应）。
 *
 * 用于 mining-gold 资产中心：显示可提现 USDT + 提现费率 + 创世分成提示。
 */
@Data
public class GoldWalletVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal usdtBalance;
    private BigDecimal usdtTotalIn;
    private BigDecimal usdtTotalOut;
    /** 当前提现费率（如 0.05） */
    private BigDecimal feeRate;
    /** 创世分成费率（如 0.01；剩余进平台） */
    private BigDecimal founderShareRate;
    /** 平台分成费率（feeRate - founderShareRate） */
    private BigDecimal platformFeeRate;

    public static GoldWalletVO from(TGoldWallet w, BigDecimal feeRate, BigDecimal founderShareRate) {
        GoldWalletVO vo = new GoldWalletVO();
        vo.setUsdtBalance(w != null && w.getUsdtBalance() != null
                ? w.getUsdtBalance() : BigDecimal.ZERO);
        vo.setUsdtTotalIn(w != null && w.getUsdtTotalIn() != null
                ? w.getUsdtTotalIn() : BigDecimal.ZERO);
        vo.setUsdtTotalOut(w != null && w.getUsdtTotalOut() != null
                ? w.getUsdtTotalOut() : BigDecimal.ZERO);
        vo.setFeeRate(feeRate);
        vo.setFounderShareRate(founderShareRate);
        BigDecimal platform = feeRate.subtract(founderShareRate)
                .setScale(6, RoundingMode.HALF_UP);
        vo.setPlatformFeeRate(platform);
        return vo;
    }
}
