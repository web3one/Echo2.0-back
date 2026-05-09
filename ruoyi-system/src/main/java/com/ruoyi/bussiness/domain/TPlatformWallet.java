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
 * 平台钱包 t_platform_wallet（主/热/冷）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_platform_wallet")
public class TPlatformWallet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String chain;
    private String address;

    /** MAIN(归集主钱包) / HOT(提现热钱包) / COLD(冷备份) */
    private String walletType;
    private String derivePath;

    private BigDecimal usdtBalance;
    private BigDecimal nativeBalance;
    private Date lastSyncTime;

    private Integer enabled;
}
