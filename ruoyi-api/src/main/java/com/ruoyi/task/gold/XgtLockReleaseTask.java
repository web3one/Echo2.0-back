package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IXgtLockReleaseSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * XGT 30 天锁仓释放 cron（PRD §13 + §16，本地 00:25 触发）
 *
 * bizDate 取今天（按 release_at &lt;= NOW 判定到期）。
 *
 * @date 2026-05-11 v3.10 C-1
 */
@Component
@Slf4j
public class XgtLockReleaseTask extends AbstractSettleJob {

    @Resource
    private IXgtLockReleaseSettleService xgtLockReleaseSettleService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_XGT_LOCK_RELEASE;
    }

    @Override
    protected LocalDate computeBizDate() {
        return LocalDate.now();
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return xgtLockReleaseSettleService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 25 0 * * ?")
    public void schedule() {
        run();
    }
}
