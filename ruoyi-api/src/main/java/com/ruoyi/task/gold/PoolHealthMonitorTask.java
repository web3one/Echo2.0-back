package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IPoolHealthMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 资金池健康监控 cron（PRD §21.1，本地 00:30 触发，结算昨日 UTC 业务日）。
 *
 * 跑在所有 6 个金矿 cron 之后（static 00:05 / team 00:10 / global 00:15 /
 * founder 00:20 / xgt_release 00:25 / daily_fee_summary 00:00）。
 *
 * 算法：feeBase vs totalDividends 比对，ratio > 1.0 抛异常走 failed；
 * ratio > 0.8 warn；否则 OK。失败行可在 admin settle 监控页一键重试。
 *
 * @date 2026-05-12
 */
@Component
@Slf4j
public class PoolHealthMonitorTask extends AbstractSettleJob {

    @Resource
    private IPoolHealthMonitorService poolHealthMonitorService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_POOL_HEALTH_MONITOR;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return poolHealthMonitorService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 30 0 * * ?")
    public void schedule() {
        run();
    }
}
