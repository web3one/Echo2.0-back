package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 创世合伙人 49 席手续费分红 cron（PRD §16，本地 00:20 触发）
 *
 * 当前为骨架空跑：依赖手续费聚合 + 49 席购买流程 尚未落地。
 * Phase 3 实装时接入 IFounderDividendSettleService。
 */
@Component
@Slf4j
public class FounderDividendTask extends AbstractSettleJob {

    @Override
    protected String jobName() {
        return TSettleLog.JOB_FOUNDER_DIVIDEND;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        log.info("[founder_dividend] dry-run: bizDate={} settleLogId={} (skeleton, requires fee aggregation + 49 seats)",
                bizDate, settleLogId);
        return SettleResult.empty();
    }

    @Scheduled(cron = "0 20 0 * * ?")
    public void schedule() {
        run();
    }
}
