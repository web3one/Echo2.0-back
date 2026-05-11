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
 * 金矿子钱包→现货 USDT 提现单。
 *
 * 扣 5% 手续费：1% 进 t_pool_account.founder_share 创世 49 等分池
 *              + 4% 进 t_pool_account.platform_fee 平台收入
 * （PRD §12.2 第 5 条权益 + figma founderPerk5；费率拆解从 sys_config 读快照）
 *
 * @date 2026-05-15
 */
@Data
@TableName("t_gold_withdraw_order")
public class TGoldWithdrawOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_FAILED = "failed";

    public static final String ASSET_USDT = "USDT";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String assetType;
    private BigDecimal grossAmount;
    private BigDecimal feeRateSnap;
    private BigDecimal feeAmount;
    private BigDecimal founderShareRateSnap;
    private BigDecimal founderShareAmount;
    private BigDecimal platformFeeAmount;
    private BigDecimal netAmount;
    private String status;
    private Integer fundPasswordVerified;
    private String idempotentKey;
    private Long spotWalletRecordId;
    private Long goldWalletLogId;
    private String failedReason;
    private Date completedAt;
    private String clientIp;
    private String clientUserAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
