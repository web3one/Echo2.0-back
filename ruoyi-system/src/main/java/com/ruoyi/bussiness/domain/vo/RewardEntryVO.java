package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TRewardLog;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 奖励流水条目（mining-gold 子站 interface RewardEntry 对齐）。
 *
 * 字段命名严格匹配 mining-gold App.tsx line 693：
 *   id / type / createdAt / gross / usdtCredited / ecoCreditLocked /
 *   healthCounted / relatedMinerId / status / xgtNominalUsd / amountXgt
 *
 * 字段映射约定：
 *   healthCounted = countedInHealthExit==1 ? grossAmountUsdt : 0（前端展示用）
 *   xgtNominalUsd = static 类型的 XGT 名义美元值（同 usdtCredited，PRD §3.1 50/50）
 *   amountXgt     = xgtCredited（XGT 实际数量，PRD §13）
 *
 * @date 2026-05-09
 */
@Data
public class RewardEntryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private Date createdAt;
    private BigDecimal gross;
    private BigDecimal usdtCredited;
    private BigDecimal ecoCreditLocked;
    private BigDecimal healthCounted;
    private Long relatedMinerId;
    private String status;
    private BigDecimal xgtNominalUsd;
    private BigDecimal amountXgt;

    public static RewardEntryVO from(TRewardLog log) {
        RewardEntryVO vo = new RewardEntryVO();
        vo.setId(log.getId());
        vo.setType(log.getRewardType());
        vo.setCreatedAt(log.getCreateTime());
        vo.setGross(nz(log.getGrossAmountUsdt()));
        vo.setUsdtCredited(nz(log.getUsdtCredited()));
        vo.setEcoCreditLocked(nz(log.getEcoCreditAmount()));

        boolean counted = log.getCountedInHealthExit() != null && log.getCountedInHealthExit() == 1;
        vo.setHealthCounted(counted ? nz(log.getGrossAmountUsdt()) : BigDecimal.ZERO);

        vo.setRelatedMinerId(log.getRelatedNodeInstanceId());
        vo.setStatus(log.getStatus());

        if (TRewardLog.TYPE_STATIC.equals(log.getRewardType())) {
            vo.setXgtNominalUsd(nz(log.getUsdtCredited()));
        }
        vo.setAmountXgt(log.getXgtCredited());
        return vo;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
