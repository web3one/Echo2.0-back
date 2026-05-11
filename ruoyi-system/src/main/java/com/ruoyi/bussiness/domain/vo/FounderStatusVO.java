package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 创世合伙人状态 VO（GET /api/founder/status）。
 *
 * mining-gold mines/shop 子视图渲染 Founding Partners 卡：
 *  - remaining = availableCount
 *  - price = priceUsdt（取自第一个 available 席位）
 *  - mySeat 非 null 时禁用购买按钮 + 显示已持有
 *  - seats 矩阵每行包含 seatNo + status + ownerUserId（admin 才填，H5 用 isMe 即可）
 *
 * @date 2026-05-11
 */
@Data
public class FounderStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 49 席位总数（恒 49，便于前端 grid） */
    private Integer totalSeats;
    /** 当前 available 数 */
    private Integer availableCount;
    /** 当前 owned 数 */
    private Integer ownedCount;
    /** 当前 frozen 数 */
    private Integer frozenCount;
    /** 单席价格（取 available 中第一行；49 席预创建都是 200000） */
    private BigDecimal priceUsdt;

    /** 当前用户持有的席位（无则 null） */
    private MySeat mySeat;

    /** 49 行席位简要状态（按 seat_no ASC） */
    private List<SeatBrief> seats;

    @Data
    public static class MySeat implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Integer seatNo;
        private String status;
        private BigDecimal priceUsdt;
        private Date paidAt;
    }

    @Data
    public static class SeatBrief implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer seatNo;
        private String status;
        /** 是否为当前登录用户的席位 */
        private Boolean isMe;
    }
}
