package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IFounderDividendSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 创世合伙人 49 席手续费分红 cron（PRD §16，本地 00:20 触发，结算昨日 UTC 业务日）
 *
 * 算法走 IFounderDividendSettleService（PRD §12 + §22；池 = (spot+contract)×10% + founder_share）。
 *
 * @date 2026-05-15
 */
@Component
@Slf4j
public class FounderDividendTask extends AbstractSettleJob {

    @Resource
    private IFounderDividendSettleService founderDividendSettleService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_FOUNDER_DIVIDEND;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return founderDividendSettleService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 20 0 * * ?")
    public void schedule() {
        run();
    }
}
