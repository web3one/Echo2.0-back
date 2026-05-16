package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * TradFi 交易订单对象 t_tradfi_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_tradfi_order")
public class TTradfiOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 0 买入 1 卖出 */
    private Integer type;

    /** 0 限价 1 市价 */
    private Integer delegateType;

    /** 0 等待成交 1 完全成交 3 已撤销 */
    private Integer status;

    private String orderNo;

    /** TradFi 标的 symbol，小写存储 */
    private String symbol;

    /** 结算币种，当前固定 usdt */
    private String coin;

    private BigDecimal fee;

    private BigDecimal delegateTotal;

    private BigDecimal delegatePrice;

    private BigDecimal dealNum;

    private BigDecimal dealPrice;

    private BigDecimal delegateValue;

    private BigDecimal dealValue;

    private Date delegateTime;

    private Date dealTime;

    private Long userId;

    private String searchValue;

    private String adminParentIds;
}
