package com.ruoyi.bussiness.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * GET /api/ecosystem-credit/my 返回体
 *
 * 包含当前余额 + 在途解锁请求 + 历史完成请求。
 */
@Data
public class MyEcosystemCreditVO {

    /** 累计未解锁 credit（直推/团队 30% 累计） */
    private BigDecimal balanceLocked;

    /** 已解锁未划出（一般 0） */
    private BigDecimal balanceUnlocked;

    /** 在途解锁请求占用的 credit（不能再次申请） */
    private BigDecimal balanceInProgress;

    /** 可用余额（可申请解锁）= balanceLocked - balanceInProgress */
    private BigDecimal balanceAvailable;

    /** 在途 + 历史（最近 50 条） */
    private List<UnlockEntry> entries;

    @Data
    public static class UnlockEntry {
        private Long id;
        private String unlockType;
        private BigDecimal amountCredit;
        /** A 路径 */
        private BigDecimal tradeVolumeRequired;
        private BigDecimal tradeVolumeCompleted;
        /** B 路径 */
        private Long xgtLockPlanId;
        private Date xgtLockReleaseAt;
        private String xgtLockStatus;

        private String status;
        private Date startedAt;
        private Date completedAt;
        private BigDecimal usdtCredited;
    }
}
