package com.ruoyi.chain.aggregation;

import com.ruoyi.bussiness.domain.TChainConfig;

import java.math.BigDecimal;

/**
 * 链上交易执行器接口。
 * 每条链一个实现（EVM 共用一个，TRX 单独一个）。
 */
public interface ChainExecutor {

    /** 该执行器支持的链（ETH/BSC/BASE/TRX） */
    boolean supports(String chain);

    /** 查 USDT 余额（已按 decimals 还原成人类可读金额） */
    BigDecimal queryUsdtBalance(String address, TChainConfig config);

    /** 查原生币余额（ETH/BNB/TRX，单位为该币种） */
    BigDecimal queryNativeBalance(String address, TChainConfig config);

    /** 发原生币转账。返回 tx_hash，失败返回 null */
    String sendNative(String privateKeyHex, String fromAddress, String toAddress,
                       BigDecimal amount, TChainConfig config);

    /** 发 USDT 转账。返回 tx_hash，失败返回 null */
    String sendUsdt(String privateKeyHex, String fromAddress, String toAddress,
                     BigDecimal amount, TChainConfig config);

    /**
     * 查询 tx 确认状态。
     * @return true=已确认成功，false=失败或还没确认
     */
    boolean isTxConfirmed(String txHash, TChainConfig config);
}
