package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;

import java.time.LocalDate;

/**
 * 结算日志服务（金矿 6 个定时任务幂等中枢）
 */
public interface ITSettleLogService {

    /**
     * 启动一次结算尝试。
     *
     * 先查同 job + bizDate 的行：
     * - status=success → 返回 null（已结算过，调用方应跳过）
     * - 不存在 / failed / running → upsert 为 running 并返回最新行
     *
     * @return null 表示已成功无需再跑；非 null 表示需要执行 doSettle，含主键 ID 用于 markSuccess/markFailed
     */
    TSettleLog tryStart(String jobName, LocalDate bizDate);

    /** doSettle 成功后写入计数和金额，置为 success。 */
    void markSuccess(Long settleLogId, SettleResult result);

    /** doSettle 抛异常后写入错误信息（截断 2000 字符），置为 failed；下次到点会自动重跑。 */
    void markFailed(Long settleLogId, String errorMessage);

    /**
     * admin 手动重试：把 failed 行重置为 running 后立刻调度 task 执行。
     *
     * - 不存在 → settle.not_found
     * - status=success → settle.duplicate（不允许重发）
     * - status=running → settle.running（避免并发冲突）
     * - status=failed → 重置为 running，写入 triggered_by_admin_id，返回新行
     */
    TSettleLog resetForRetry(String jobName, LocalDate bizDate, Long operatorAdminId);

    TSettleLog getById(Long id);

    IPage<TSettleLog> page(int pageNum, int pageSize, String jobName, LocalDate bizDate, String status);
}
