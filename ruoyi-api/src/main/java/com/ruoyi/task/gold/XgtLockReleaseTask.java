package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * XGT 30 天锁仓释放 cron（PRD §13 + §16，本地 00:25 触发）
 *
 * bizDate 取今天（按 release_at &lt;= NOW 判定到期，跨天累积一次释放）。
 * 当前为骨架空跑。Phase 3 实装时接入 IXgtLockReleaseSettleService：
 * - 扫 status=locked AND release_at &lt;= NOW
 * - 每条计划 status → completed，释放 amount 从 balance_locked 移到 balance_unlocked
 * - 写 t_xgt_log change_type=release，t_reward_log type=xgt_unlock
 * - 用户冻结跳过（追问 A 的例外：30 天锁仓是合同义务，PM 决策放行——但这里仍按
 *   "冻结期所有奖励停发"统一处理，解冻后会被下次 release 任务捞起）
 */
@Component
@Slf4j
public class XgtLockReleaseTask extends AbstractSettleJob {

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
        log.info("[xgt_lock_release] dry-run: bizDate={} settleLogId={} (skeleton)",
                bizDate, settleLogId);
        return SettleResult.empty();
    }

    @Scheduled(cron = "0 25 0 * * ?")
    public void schedule() {
        run();
    }
}
