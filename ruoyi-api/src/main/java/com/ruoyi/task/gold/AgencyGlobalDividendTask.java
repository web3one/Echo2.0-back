package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * V4/V5 全网手续费分红 cron（PRD §16，本地 00:15 触发）
 *
 * 当前为骨架空跑：依赖手续费聚合 4 件套（板块 9）尚未落地。
 * Phase 3 实装时接入 IAgencyGlobalDividendSettleService。
 */
@Component
@Slf4j
public class AgencyGlobalDividendTask extends AbstractSettleJob {

    @Override
    protected String jobName() {
        return TSettleLog.JOB_AGENCY_GLOBAL_DIVIDEND;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        log.info("[agency_global_dividend] dry-run: bizDate={} settleLogId={} (skeleton, requires fee aggregation)",
                bizDate, settleLogId);
        return SettleResult.empty();
    }

    @Scheduled(cron = "0 15 0 * * ?")
    public void schedule() {
        run();
    }
}
