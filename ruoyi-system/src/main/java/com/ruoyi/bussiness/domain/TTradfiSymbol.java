package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * TradFi 标的（股票/股指/外汇/贵金属/大宗商品）
 *
 * 与 4 张币种表分开存放，避免污染加密币逻辑。
 * 数据源默认 Gate.io TradFi REST，2.5s 轮询；后续可挂 mt5/finnhub。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_tradfi_symbol")
public class TTradfiSymbol extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 内部统一符号 EURUSD/XAUUSD/SPX500/AAPL */
    private String symbol;

    /** H5 展示用 EUR/USD、XAU/USD */
    private String showSymbol;

    /** 数据源合约名 EURUSD / XAUUSD / SPX500 / AAPL.US */
    private String gateContract;

    /** FX 外汇 / METAL 贵金属 / INDEX 股指 / STOCK 股票 / COMMODITY 大宗 */
    private String category;

    /** 计价货币 默认 USD */
    private String baseCoin;

    private String logo;

    /** 数据源 gate/mt5/finnhub */
    private String market;

    /** 1=启用 2=禁用 */
    private Integer status;

    /** 1=H5 展示 2=隐藏 */
    private Integer showFlag;

    /** 价格小数位 */
    private Integer decimals;

    private Integer sort;

    /** 当前价（拼装到 getCoinList 响应用，从 Redis 读取，不入库） */
    @TableField(exist = false)
    private BigDecimal amount;

    /** 24h 开盘价（前端算涨跌用，不入库） */
    @TableField(exist = false)
    private BigDecimal open;
}
