package com.ruoyi.task.gold;

import cn.hutool.core.util.StrUtil;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IDailyFeeSummaryService;
import com.ruoyi.system.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 每日手续费聚合 cron（B 路线第一组，本地 00:00 触发）。
 *
 * 聚合前一天（UTC 业务日）的：
 *   - spot 手续费（USDT 结算）
 *   - contract 手续费
 *   - 金矿提现费（独立统计）
 *
 * 写入 t_daily_fee_summary，为 B 路线第二组 V4/V5 全网分红 + 创世手续费分红 cron 提供数据源。
 *
 * 开关：sys_config key=gold.fee.daily_summary_enabled（false 跳过）
 *
 * @date 2026-05-15
 */
@Component
@Slf4j
public class DailyFeeSummaryTask extends AbstractSettleJob {

    private static final String CFG_ENABLED = "gold.fee.daily_summary_enabled";

    @Resource
    private IDailyFeeSummaryService dailyFeeSummaryService;

    @Resource
    private ISysConfigService sysConfigService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_DAILY_FEE_SUMMARY;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        if (!enabled()) {
            log.info("[daily_fee_summary] disabled by sys_config, skip bizDate={}", bizDate);
            return SettleResult.empty();
        }
        return dailyFeeSummaryService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void schedule() {
        run();
    }

    private boolean enabled() {
        String v = sysConfigService.selectConfigByKey(CFG_ENABLED);
        if (StrUtil.isBlank(v)) return true;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }
}
