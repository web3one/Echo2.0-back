package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 双轨当日业绩清零 cron（PRD §16，本地 00:30 触发）
 *
 * bizDate 取今天（标识"今天清零，开启新的累计日"）。注意 t_binary_volume_daily 不是
 * DELETE 历史行，而是新业务日往里写新行（PRD V029 的设计）。所以"清零"在数据层面
 * 实际是"什么都不做"——新一天的购买会自动写新行。
 *
 * 当前为骨架空跑。如果未来要真"DELETE 历史 N 天前数据"或"打 admin 报表归档"再实装。
 */
@Component
@Slf4j
public class BinaryDailyVolumeResetTask extends AbstractSettleJob {

    @Override
    protected String jobName() {
        return TSettleLog.JOB_BINARY_DAILY_VOLUME_RESET;
    }

    @Override
    protected LocalDate computeBizDate() {
        return LocalDate.now();
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        log.info("[binary_daily_volume_reset] dry-run: bizDate={} settleLogId={} (no-op by design)",
                bizDate, settleLogId);
        return SettleResult.empty();
    }

    @Scheduled(cron = "0 30 0 * * ?")
    public void schedule() {
        run();
    }
}
