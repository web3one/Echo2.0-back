package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.ITSettleLogService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 6 个金矿定时任务共用基类（PRD §16）
 *
 * 模板流程：
 * 1. tryStart(jobName, bizDate)：t_settle_log 已 success 直接 return（幂等跳过）
 * 2. doSettle(bizDate, settleLogId)：子类实现真实结算逻辑
 * 3. 成功 → markSuccess 写入计数和金额
 * 4. 失败 → markFailed 写入错误堆栈，不抛异常向外（避免 Spring Scheduler 状态混乱）
 *
 * 失败的 t_settle_log 行下次到点 cron 触发时会被 tryStart upsert 回 running 重跑（用户决策：自动重试）。
 *
 * @date 2026-05-09
 */
@Slf4j
public abstract class AbstractSettleJob {

    @Resource
    protected ITSettleLogService settleLogService;

    /** 子类返回固定的 job 名称，与 t_settle_log.job_name UK 绑定。 */
    protected abstract String jobName();

    /**
     * 默认 bizDate = UTC 昨日（与 BinaryTreeServiceImpl 写 t_binary_volume_daily.biz_date 的口径一致）。
     * 适用于"结算昨日产生的奖励"类任务：static / team / global / founder。
     * binary_reset / xgt_release 等子类按需 override。
     *
     * 注意：cron 触发时间是服务器本地时区，bizDate 业务含义是 UTC 业务日，二者
     * 不需要同步。统一以 UTC 业务日做 t_settle_log.biz_date 幂等 key。
     */
    protected LocalDate computeBizDate() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1);
    }

    /** 子类实现真实结算逻辑；空跑骨架返回 SettleResult.empty()。 */
    protected abstract SettleResult doSettle(LocalDate bizDate, Long settleLogId);

    /** Spring @Scheduled 子类调本方法执行。public 便于 admin retry 直接调用。 */
    public final void run() {
        final String jn = jobName();
        final LocalDate bd = computeBizDate();
        log.info("[settle][{}] start bizDate={}", jn, bd);

        TSettleLog row = settleLogService.tryStart(jn, bd);
        if (row == null) {
            log.info("[settle][{}] skipped (already success) bizDate={}", jn, bd);
            return;
        }

        try {
            SettleResult result = doSettle(bd, row.getId());
            if (result == null) {
                result = SettleResult.empty();
            }
            settleLogService.markSuccess(row.getId(), result);
            log.info("[settle][{}] success bizDate={} total={} success={} failed={} skipped={} amount={}",
                    jn, bd, result.getTotalCount(), result.getSuccessCount(),
                    result.getFailedCount(), result.getSkippedCount(),
                    result.getAmountSettledUsdt());
        } catch (Exception e) {
            log.error("[settle][{}] failed bizDate={}", jn, bd, e);
            settleLogService.markFailed(row.getId(), stacktrace(e));
        }
    }

    private static String stacktrace(Throwable e) {
        if (e == null) {
            return "(no exception)";
        }
        StringWriter sw = new StringWriter();
        sw.append(e.getClass().getSimpleName()).append(": ")
                .append(String.valueOf(e.getMessage())).append("\n");
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
