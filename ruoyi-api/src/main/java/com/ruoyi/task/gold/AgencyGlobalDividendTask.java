package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IAgencyGlobalDividendSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * V4/V5 全网手续费分红 cron（PRD §16，本地 00:15 触发，结算昨日 UTC 业务日）
 *
 * 算法走 IAgencyGlobalDividendSettleService（PRD §8.1 + §11 + §22）。
 *
 * @date 2026-05-15
 */
@Component
@Slf4j
public class AgencyGlobalDividendTask extends AbstractSettleJob {

    @Resource
    private IAgencyGlobalDividendSettleService agencyGlobalDividendSettleService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_AGENCY_GLOBAL_DIVIDEND;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return agencyGlobalDividendSettleService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 15 0 * * ?")
    public void schedule() {
        run();
    }
}
