package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 矿机购买请求（POST /api/nodes/buy）。
 *
 * @date 2026-05-10
 */
@Data
public class NodeBuyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 等级编码 L1 / L2 / L3 / L4 */
    private String levelCode;

    /** 资金密码（明文，进 service 后比对 TAppUserDetail.userTardPwd 的 BCrypt 哈希） */
    private String fundPassword;

    /**
     * 客户端幂等键（UUID 或 user_id+timestamp）防重复扣款。
     * 同一 key 重复请求返回首次结果，不重复创建实例。
     */
    private String idempotentKey;
}
