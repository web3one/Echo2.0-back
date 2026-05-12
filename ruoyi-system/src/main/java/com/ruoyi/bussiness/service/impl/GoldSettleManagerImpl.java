package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IAgencyGlobalDividendSettleService;
import com.ruoyi.bussiness.service.IAgencyTeamRewardSettleService;
import com.ruoyi.bussiness.service.IDailyFeeSummaryService;
import com.ruoyi.bussiness.service.IFounderDividendSettleService;
import com.ruoyi.bussiness.service.IGoldSettleManager;
import com.ruoyi.bussiness.service.IPoolHealthMonitorService;
import com.ruoyi.bussiness.service.IStaticRewardSettleService;
import com.ruoyi.bussiness.service.ITSettleLogService;
import com.ruoyi.bussiness.service.IXgtLockReleaseSettleService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;

@Service
@Slf4j
public class GoldSettleManagerImpl implements IGoldSettleManager {

    @Resource
    private ITSettleLogService settleLogService;

    @Resource
    private IStaticRewardSettleService staticRewardSettleService;

    @Resource
    private IAgencyTeamRewardSettleService agencyTeamRewardSettleService;

    @Resource
    private IDailyFeeSummaryService dailyFeeSummaryService;

    @Resource
    private IAgencyGlobalDividendSettleService agencyGlobalDividendSettleService;

    @Resource
    private IFounderDividendSettleService founderDividendSettleService;

    @Resource
    private IXgtLockReleaseSettleService xgtLockReleaseSettleService;

    @Resource
    private IPoolHealthMonitorService poolHealthMonitorService;

    @Override
    public TSettleLog retry(String jobName, LocalDate bizDate, Long adminId) {
        // 1. 重置 failed → running，校验 success/running 拒绝
        TSettleLog row = settleLogService.resetForRetry(jobName, bizDate, adminId);

        // 2. 同步跑对应 service
        try {
            SettleResult result = dispatch(jobName, bizDate, row.getId());
            if (result == null) {
                result = SettleResult.empty();
            }
            settleLogService.markSuccess(row.getId(), result);
            log.info("[settle.retry] success: job={} bizDate={} admin={} success={} skipped={} amount={}",
                    jobName, bizDate, adminId,
                    result.getSuccessCount(), result.getSkippedCount(), result.getAmountSettledUsdt());
            row.setStatus(TSettleLog.STATUS_SUCCESS);
        } catch (Exception e) {
            log.error("[settle.retry] failed: job={} bizDate={} admin={}", jobName, bizDate, adminId, e);
            settleLogService.markFailed(row.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new ServiceException(MessageUtils.message("settle.retry.failed"));
        }
        return row;
    }

    private SettleResult dispatch(String jobName, LocalDate bizDate, Long settleLogId) {
        if (TSettleLog.JOB_DAILY_STATIC_REWARD.equals(jobName)) {
            return staticRewardSettleService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_AGENCY_TEAM_REWARD.equals(jobName)) {
            return agencyTeamRewardSettleService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_DAILY_FEE_SUMMARY.equals(jobName)) {
            return dailyFeeSummaryService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_AGENCY_GLOBAL_DIVIDEND.equals(jobName)) {
            return agencyGlobalDividendSettleService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_FOUNDER_DIVIDEND.equals(jobName)) {
            return founderDividendSettleService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_XGT_LOCK_RELEASE.equals(jobName)) {
            return xgtLockReleaseSettleService.settle(bizDate, settleLogId);
        }
        if (TSettleLog.JOB_POOL_HEALTH_MONITOR.equals(jobName)) {
            return poolHealthMonitorService.settle(bizDate, settleLogId);
        }
        // binary_daily_volume_reset 设计上 no-op（V029 已是新业务日新行）
        if (TSettleLog.JOB_BINARY_DAILY_VOLUME_RESET.equals(jobName)) {
            log.info("[settle.retry] dry-run no-op job: {}", jobName);
            return SettleResult.empty();
        }
        throw new ServiceException(MessageUtils.message("settle.job.unknown"));
    }
}
