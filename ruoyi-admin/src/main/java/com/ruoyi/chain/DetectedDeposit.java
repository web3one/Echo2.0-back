package com.ruoyi.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 链监听检测到的一笔充值（USDT Transfer 事件 → 平台用户地址）。
 * 由 EvmDepositScanner / TrxDepositScanner 产出，DepositAutoIngestService 消费。
 */
@Data
@Builder
@AllArgsConstructor
public class DetectedDeposit {

    /** ETH / BSC / BASE / TRX */
    private final String chain;

    /** 链上交易 hash */
    private final String txHash;

    /** Transfer 事件的 from 地址 */
    private final String fromAddress;

    /** Transfer 事件的 to 地址（对应平台用户的派生地址） */
    private final String toAddress;

    /** 已经按链上 decimals 还原的金额（人类可读） */
    private final BigDecimal amount;

    /** 币种（目前只有 USDT） */
    private final String symbol;

    /** 区块高度 */
    private final Long blockNumber;

    /** 区块时间 */
    private final Date blockTime;
}
