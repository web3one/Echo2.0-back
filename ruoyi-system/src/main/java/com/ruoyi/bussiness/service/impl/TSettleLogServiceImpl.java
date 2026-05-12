package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleReconcileVO;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TSettleLogMapper;
import com.ruoyi.bussiness.service.ITSettleLogService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class TSettleLogServiceImpl implements ITSettleLogService {

    private static final int ERR_MSG_MAX = 2000;

    private static final int EXPORT_CAP = 1000;
    private static final BigDecimal WARN_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal CRITICAL_THRESHOLD = BigDecimal.ONE;

    /** 计入"USDT 分红消耗"的 cron 名 */
    private static final List<String> DIVIDEND_JOBS = Arrays.asList(
            TSettleLog.JOB_DAILY_STATIC_REWARD,
            TSettleLog.JOB_AGENCY_TEAM_REWARD,
            TSettleLog.JOB_AGENCY_GLOBAL_DIVIDEND,
            TSettleLog.JOB_FOUNDER_DIVIDEND
    );

    @Resource
    private TSettleLogMapper mapper;

    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;

    @Override
    public TSettleLog tryStart(String jobName, LocalDate bizDate) {
        TSettleLog existing = mapper.selectByJobAndDate(jobName, bizDate);
        if (existing != null && TSettleLog.STATUS_SUCCESS.equals(existing.getStatus())) {
            log.info("settle skip (already success): job={} bizDate={} id={}",
                    jobName, bizDate, existing.getId());
            return null;
        }
        mapper.upsertRunning(jobName, bizDate);
        return mapper.selectByJobAndDate(jobName, bizDate);
    }

    @Override
    public void markSuccess(Long id, SettleResult r) {
        if (id == null) {
            return;
        }
        TSettleLog row = new TSettleLog();
        row.setId(id);
        row.setStatus(TSettleLog.STATUS_SUCCESS);
        row.setFinishedAt(new Date());
        row.setTotalCount(r != null ? r.getTotalCount() : 0);
        row.setSuccessCount(r != null ? r.getSuccessCount() : 0);
        row.setFailedCount(r != null ? r.getFailedCount() : 0);
        row.setSkippedCount(r != null ? r.getSkippedCount() : 0);
        row.setAmountSettledUsdt(r != null && r.getAmountSettledUsdt() != null
                ? r.getAmountSettledUsdt() : BigDecimal.ZERO);
        mapper.updateById(row);
    }

    @Override
    public void markFailed(Long id, String errorMessage) {
        if (id == null) {
            return;
        }
        if (errorMessage == null) {
            errorMessage = "(no message)";
        } else if (errorMessage.length() > ERR_MSG_MAX) {
            errorMessage = errorMessage.substring(0, ERR_MSG_MAX);
        }
        TSettleLog row = new TSettleLog();
        row.setId(id);
        row.setStatus(TSettleLog.STATUS_FAILED);
        row.setFinishedAt(new Date());
        row.setErrorMessage(errorMessage);
        mapper.updateById(row);
    }

    @Override
    public TSettleLog resetForRetry(String jobName, LocalDate bizDate, Long operatorAdminId) {
        TSettleLog existing = mapper.selectByJobAndDate(jobName, bizDate);
        if (existing == null) {
            throw new ServiceException(MessageUtils.message("settle.not_found"));
        }
        if (TSettleLog.STATUS_SUCCESS.equals(existing.getStatus())) {
            throw new ServiceException(MessageUtils.message("settle.duplicate"));
        }
        if (TSettleLog.STATUS_RUNNING.equals(existing.getStatus())) {
            throw new ServiceException(MessageUtils.message("settle.running"));
        }
        // failed → 用 UpdateWrapper 显式 set null（updateById 默认忽略 null 字段）
        LambdaUpdateWrapper<TSettleLog> uw = new LambdaUpdateWrapper<>();
        uw.eq(TSettleLog::getId, existing.getId())
                .set(TSettleLog::getStatus, TSettleLog.STATUS_RUNNING)
                .set(TSettleLog::getStartedAt, new Date())
                .set(TSettleLog::getFinishedAt, null)
                .set(TSettleLog::getErrorMessage, null)
                .set(TSettleLog::getTriggeredByAdminId, operatorAdminId);
        mapper.update(null, uw);
        existing.setStatus(TSettleLog.STATUS_RUNNING);
        existing.setStartedAt(new Date());
        existing.setFinishedAt(null);
        existing.setErrorMessage(null);
        existing.setTriggeredByAdminId(operatorAdminId);
        return existing;
    }

    @Override
    public TSettleLog getById(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    @Override
    public IPage<TSettleLog> page(int pageNum, int pageSize, String jobName, LocalDate bizDate, String status) {
        LambdaQueryWrapper<TSettleLog> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(jobName)) {
            qw.eq(TSettleLog::getJobName, jobName);
        }
        if (bizDate != null) {
            qw.eq(TSettleLog::getBizDate, bizDate);
        }
        if (StrUtil.isNotBlank(status)) {
            qw.eq(TSettleLog::getStatus, status);
        }
        qw.orderByDesc(TSettleLog::getStartedAt);

        // 手动分页规避 MP 3.4.1 PaginationInnerInterceptor 偶发 "SELECT COUNT()" BUG。
        Page<TSettleLog> p = new Page<>(pageNum, pageSize);
        Integer total = mapper.selectCount(qw);
        long totalLong = total == null ? 0L : total.longValue();
        p.setTotal(totalLong);
        if (totalLong > 0) {
            qw.last("LIMIT " + ((long) (pageNum - 1) * pageSize) + ", " + pageSize);
            p.setRecords(mapper.selectList(qw));
        }
        return p;
    }

    @Override
    public List<TSettleLog> listForExport(String jobName, LocalDate bizDate, String status) {
        LambdaQueryWrapper<TSettleLog> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(jobName)) {
            qw.eq(TSettleLog::getJobName, jobName);
        }
        if (bizDate != null) {
            qw.eq(TSettleLog::getBizDate, bizDate);
        }
        if (StrUtil.isNotBlank(status)) {
            qw.eq(TSettleLog::getStatus, status);
        }
        qw.orderByDesc(TSettleLog::getStartedAt).last("LIMIT " + EXPORT_CAP);
        return mapper.selectList(qw);
    }

    @Override
    public SettleReconcileVO reconcile(LocalDate bizDate) {
        SettleReconcileVO vo = new SettleReconcileVO();
        vo.setBizDate(bizDate);

        // 1. 分红基数（spot + contract + 提现费分项）
        TDailyFeeSummary fee = dailyFeeSummaryMapper.selectByBizDate(bizDate);
        if (fee != null) {
            vo.setDividendBaseUsdt(nz(fee.getDividendBaseUsdt()));
            vo.setSpotFeeUsdt(nz(fee.getSpotFeeUsdt()));
            vo.setContractFeeUsdt(nz(fee.getContractFeeUsdt()));
            vo.setGoldWithdrawFeeUsdt(nz(fee.getGoldWithdrawFeeUsdt()));
            vo.setGoldWithdrawFounderShareUsdt(nz(fee.getGoldWithdrawFounderShareUsdt()));
            vo.setGoldWithdrawPlatformFeeUsdt(nz(fee.getGoldWithdrawPlatformFeeUsdt()));
        } else {
            vo.setDividendBaseUsdt(BigDecimal.ZERO);
            vo.setSpotFeeUsdt(BigDecimal.ZERO);
            vo.setContractFeeUsdt(BigDecimal.ZERO);
            vo.setGoldWithdrawFeeUsdt(BigDecimal.ZERO);
            vo.setGoldWithdrawFounderShareUsdt(BigDecimal.ZERO);
            vo.setGoldWithdrawPlatformFeeUsdt(BigDecimal.ZERO);
        }

        // 2. 当日所有 cron（含 pool_health_monitor / xgt_release / 等所有 8 个）
        List<TSettleLog> rows = mapper.selectList(
                new LambdaQueryWrapper<TSettleLog>()
                        .eq(TSettleLog::getBizDate, bizDate)
                        .orderByAsc(TSettleLog::getStartedAt));
        List<SettleReconcileVO.JobAgg> jobs = new ArrayList<>(rows.size());
        BigDecimal totalDividends = BigDecimal.ZERO;
        for (TSettleLog row : rows) {
            SettleReconcileVO.JobAgg agg = new SettleReconcileVO.JobAgg();
            agg.setJobName(row.getJobName());
            agg.setStatus(row.getStatus());
            agg.setTotalCount(row.getTotalCount());
            agg.setSuccessCount(row.getSuccessCount());
            agg.setFailedCount(row.getFailedCount());
            agg.setSkippedCount(row.getSkippedCount());
            agg.setAmountSettledUsdt(row.getAmountSettledUsdt());
            agg.setErrorMessage(row.getErrorMessage());
            jobs.add(agg);
            if (TSettleLog.STATUS_SUCCESS.equals(row.getStatus())
                    && DIVIDEND_JOBS.contains(row.getJobName())
                    && row.getAmountSettledUsdt() != null) {
                totalDividends = totalDividends.add(row.getAmountSettledUsdt());
            }
        }
        vo.setJobs(jobs);
        vo.setTotalDividendsSettledUsdt(totalDividends);

        // 3. 资金池健康度
        BigDecimal feeBase = vo.getDividendBaseUsdt();
        if (feeBase.compareTo(BigDecimal.ZERO) <= 0) {
            vo.setRatio(null);
            vo.setHealthLevel(totalDividends.compareTo(BigDecimal.ZERO) > 0 ? "CRITICAL" : "NO_DATA");
        } else {
            BigDecimal ratio = totalDividends.divide(feeBase, 6, RoundingMode.HALF_UP);
            vo.setRatio(ratio);
            if (ratio.compareTo(CRITICAL_THRESHOLD) > 0) {
                vo.setHealthLevel("CRITICAL");
            } else if (ratio.compareTo(WARN_THRESHOLD) > 0) {
                vo.setHealthLevel("WARN");
            } else {
                vo.setHealthLevel("OK");
            }
        }
        return vo;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
