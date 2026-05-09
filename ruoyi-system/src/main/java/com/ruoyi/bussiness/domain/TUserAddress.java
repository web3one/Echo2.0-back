package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户多链充值地址（HD 派生）：t_user_address
 *
 * 每用户每条链一行，user_id+chain 唯一。
 * EVM 三条链（ETH/BSC/BASE）对同一 user 派生地址完全相同。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_address")
public class TUserAddress extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Excel(name = "用户ID")
    private Long userId;

    /** 链标识：ETH / BSC / BASE / TRX */
    @Excel(name = "链")
    private String chain;

    /** 派生出的充值地址 */
    @Excel(name = "地址")
    private String address;

    /** HD 派生路径，如 m/44'/60'/0'/0/123 */
    private String derivePath;

    /** 派生索引（一般等于 userId） */
    private Long deriveIndex;
}
