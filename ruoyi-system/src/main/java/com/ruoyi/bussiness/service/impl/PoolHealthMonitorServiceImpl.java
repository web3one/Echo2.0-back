package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TSettleLogMapper;
import com.ruoyi.bussiness.service.IPoolHealthMonitorService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 资金池健康监控实现（PRD §21.1）。
 *
 * 比对当日：
 *   feeBase = spotFee + contractFee（来自 t_daily_fee_summary）
 *   totalDividends = SUM(amount_settled_usdt) WHERE job_name IN (
 *       daily_static_reward_job, agency_team_reward_job,
 *       agency_global_dividend_job, founder_dividend_job
 *   ) AND biz_date = ? AND status = 'success'
 *
 * ratio = totalDividends / feeBase
 *   > WARN_THRESHOLD (0.8) → log.warn，amountSettledUsdt = feeBase - totalDividends（剩余）
 *   > CRITICAL_THRESHOLD (1.0) → 抛 ServiceException，AbstractSettleJob 写 failed
 *
 * 注意：xgt_lock_release_job 不计入分红消耗（XGT 是站内积分，不消耗 USDT 池）。
 *       daily_fee_summary_job 是基数聚合本身，不算消耗。
 *
 * @date 2026-05-12
 */
@Service
@Slf4j
public class PoolHealthMonitorServiceImpl implements IPoolHealthMonitorService {

    private static final BigDecimal WARN_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal CRITICAL_THRESHOLD = BigDecimal.ONE;

    /** 计入"USDT 分红消耗"的 cron 名（XGT 释放 / fee 聚合 / 健康监控自己 不算） */
    private static final List<String> DIVIDEND_JOBS = Arrays.asList(
            TSettleLog.JOB_DAILY_STATIC_REWARD,
            TSettleLog.JOB_AGENCY_TEAM_REWARD,
            TSettleLog.JOB_AGENCY_GLOBAL_DIVIDEND,
            TSettleLog.JOB_FOUNDER_DIVIDEND
    );

    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;

    @Resource
    private TSettleLogMapper settleLogMapper;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();

        TDailyFeeSummary fee = dailyFeeSummaryMapper.selectByBizDate(bizDate);
        BigDecimal feeBase = fee == null ? BigDecimal.ZERO : nz(fee.getDividendBaseUsdt());

        BigDecimal totalDividends = BigDecimal.ZERO;
        for (String job : DIVIDEND_JOBS) {
            TSettleLog row = settleLogMapper.selectOne(
                    new LambdaQueryWrapper<TSettleLog>()
                            .eq(TSettleLog::getJobName, job)
                            .eq(TSettleLog::getBizDate, bizDate)
                            .eq(TSettleLog::getStatus, TSettleLog.STATUS_SUCCESS)
                            .last("LIMIT 1"));
            if (row != null && row.getAmountSettledUsdt() != null) {
                totalDividends = totalDividends.add(row.getAmountSettledUsdt());
            }
        }
        r.setTotalCount(DIVIDEND_JOBS.size());
        r.setSuccessCount(DIVIDEND_JOBS.size());

        BigDecimal remainder = feeBase.subtract(totalDividends);
        r.setAmountSettledUsdt(remainder);

        if (feeBase.compareTo(BigDecimal.ZERO) <= 0) {
            // 基数为 0：可能 daily_fee_summary 尚未跑完 / 当日无交易；如有 dividends 仍属异常
            if (totalDividends.compareTo(BigDecimal.ZERO) > 0) {
                String msg = String.format(
                        "[pool_health] CRITICAL bizDate=%s feeBase=0 但 dividends=%s，资金池亏空，触发告警",
                        bizDate, totalDividends.toPlainString());
                log.error(msg);
                alert(bizDate, "CRITICAL", BigDecimal.ZERO, feeBase, totalDividends);
                throw new ServiceException(msg);
            }
            log.info("[pool_health] OK bizDate={} feeBase=0 dividends=0 (no activity)", bizDate);
            return r;
        }

        BigDecimal ratio = totalDividends.divide(feeBase, 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(CRITICAL_THRESHOLD) > 0) {
            String msg = String.format(
                    "[pool_health] CRITICAL bizDate=%s ratio=%s (dividends=%s / feeBase=%s) 资金池亏空 %s USDT",
                    bizDate, ratio.toPlainString(),
                    totalDividends.toPlainString(), feeBase.toPlainString(),
                    remainder.negate().toPlainString());
            log.error(msg);
            alert(bizDate, "CRITICAL", ratio, feeBase, totalDividends);
            throw new ServiceException(msg);
        }
        if (ratio.compareTo(WARN_THRESHOLD) > 0) {
            log.warn("[pool_health] WARN bizDate={} ratio={} (dividends={} / feeBase={}) 剩余 {} USDT，接近阈值",
                    bizDate, ratio.toPlainString(),
                    totalDividends.toPlainString(), feeBase.toPlainString(),
                    remainder.toPlainString());
            alert(bizDate, "WARN", ratio, feeBase, totalDividends);
        } else {
            log.info("[pool_health] OK bizDate={} ratio={} dividends={} feeBase={} 剩余 {} USDT",
                    bizDate, ratio.toPlainString(),
                    totalDividends.toPlainString(), feeBase.toPlainString(),
                    remainder.toPlainString());
        }
        return r;
    }

    /**
     * 告警 hook：当前阶段只打 log（admin 在 settle 监控页能看到 errorMessage）。
     * 后续可接入：钉钉机器人 / Slack / 邮件 / 短信。
     */
    private void alert(LocalDate bizDate, String level, BigDecimal ratio,
                       BigDecimal feeBase, BigDecimal dividends) {
        // 预留接入点。例：
        //   dingTalkRobotService.send(...)
        //   slackService.postMessage(...)
        log.warn("[pool_health][ALERT][{}] bizDate={} ratio={} feeBase={} dividends={}",
                level, bizDate, ratio, feeBase, dividends);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
