package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IAgencyTeamRewardSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 团队代理奖 cron（PRD §16，本地 00:10 触发，结算昨日 UTC 业务日）
 *
 * 算法走 IAgencyTeamRewardSettleService（PRD §9 + §22）。
 *
 * @date 2026-05-09
 */
@Component
@Slf4j
public class AgencyTeamRewardTask extends AbstractSettleJob {

    @Resource
    private IAgencyTeamRewardSettleService agencyTeamRewardSettleService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_AGENCY_TEAM_REWARD;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return agencyTeamRewardSettleService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void schedule() {
        run();
    }
}
