package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 金矿子钱包 USDT 余额（PRD §12.2 / figma assetHub 设计）。
 *
 * 跟现货 USDT 余额（t_app_asset）独立；金矿奖励 cron 写入这里，用户从 mining-gold
 * 资产中心主动 [Withdraw USDT] 扣 5% 手续费后转入现货。
 *
 * XGT 余额仍走 t_xgt_balance，本表只管 USDT。
 *
 * @date 2026-05-15
 */
@Data
@TableName("t_gold_wallet")
public class TGoldWallet implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private BigDecimal usdtBalance;
    private BigDecimal usdtTotalIn;
    private BigDecimal usdtTotalOut;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
