package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TSettleLog;

import java.time.LocalDate;

/**
 * 金矿 6 个定时任务的统一 admin 重试入口。
 *
 * 设计：业务逻辑放 service，cron task 类只是触发器。admin retry 直接调本 manager，
 * 不依赖 cron——支持任意历史 bizDate 重跑（cron 自动只会跑"昨天"，不会主动捞历史失败）。
 *
 * 失败重跑机制：
 * 1. resetForRetry：把 t_settle_log 的 failed 行重置回 running，写 triggered_by_admin_id
 * 2. 同步跑对应 service.settle(bizDate, settleLogId)
 * 3. 成功 markSuccess / 失败 markFailed
 *
 * 同步执行（不入异步队列），admin 接口阻塞直至完成，便于客服立即看到结果。
 * 矿机数量大时可能阻塞分钟级——Phase 1 数据量小可接受。
 */
public interface IGoldSettleManager {

    /**
     * @throws com.ruoyi.common.exception.ServiceException
     *         settle.not_found / .duplicate / .running / .retry.failed / .job.unknown
     */
    TSettleLog retry(String jobName, LocalDate bizDate, Long adminId);
}
