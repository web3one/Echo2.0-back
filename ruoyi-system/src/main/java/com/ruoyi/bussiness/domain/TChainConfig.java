package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 链配置 t_chain_config
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chain_config")
public class TChainConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** ETH / BSC / BASE / TRX */
    @TableId(value = "chain")
    private String chain;

    private String chainName;
    private String nativeSymbol;
    private Integer bip44CoinType;

    private String rpcUrl;
    private String backupRpcUrl;
    private String explorerUrl;

    private String usdtContract;
    private Integer usdtDecimals;

    private Integer minConfirmations;
    private Integer blockPollingIntervalMs;
    private Long lastScannedBlock;

    private BigDecimal gasTopUpAmount;
    private BigDecimal minAggregationAmount;

    private Integer depositEnabled;
    private Integer withdrawEnabled;
}
