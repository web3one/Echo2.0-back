package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IEcosystemCreditUnlockCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * ecosystem_credit 解锁推进 cron（PRD §10，辅助 cron，本地小时级触发）。
 *
 * 不在 6 个核心 cron 之列，不写 t_settle_log（本身就是推进而非每日结算）。
 *
 * @date 2026-05-11 v3.10 C-2
 */
@Component
@Slf4j
public class EcosystemCreditUnlockCheckTask {

    @Resource
    private IEcosystemCreditUnlockCheckService checkService;

    /** 每小时 :07 触发（避开整点拥挤） */
    @Scheduled(cron = "0 7 * * * ?")
    public void schedule() {
        try {
            SettleResult r = checkService.check();
            log.info("[eco_credit_unlock] cron tick done total={} success={} skipped={} failed={}",
                    r.getTotalCount(), r.getSuccessCount(), r.getSkippedCount(), r.getFailedCount());
        } catch (Exception e) {
            log.error("[eco_credit_unlock] cron tick failed", e);
        }
    }
}
