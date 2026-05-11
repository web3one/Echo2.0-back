package com.ruoyi.task.gold;

import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.service.IStaticRewardSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 每日静态分红 cron（PRD §16，本地时区每天 00:05 触发，结算昨日业务日）
 *
 * - 扫所有 active 矿机
 * - 每台 gross = price × daily_yield_rate
 * - 健康出局截断 + 50/50 USDT/XGT 分账 + XGT 锁仓 30 天
 * - 用户冻结跳过；矿机达 300% 标记 expired
 */
@Component
@Slf4j
public class DailyStaticRewardTask extends AbstractSettleJob {

    @Resource
    private IStaticRewardSettleService staticRewardSettleService;

    @Override
    protected String jobName() {
        return TSettleLog.JOB_DAILY_STATIC_REWARD;
    }

    @Override
    protected SettleResult doSettle(LocalDate bizDate, Long settleLogId) {
        return staticRewardSettleService.settle(bizDate, settleLogId);
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void schedule() {
        run();
    }
}
