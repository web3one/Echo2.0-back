package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TSettleLogMapper;
import com.ruoyi.bussiness.service.ITSettleLogService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Service
@Slf4j
public class TSettleLogServiceImpl implements ITSettleLogService {

    private static final int ERR_MSG_MAX = 2000;

    @Resource
    private TSettleLogMapper mapper;

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
        return mapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }
}
