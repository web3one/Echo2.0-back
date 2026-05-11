package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TSettleLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 结算日志 Mapper（金矿 6 个定时任务幂等核心）
 */
public interface TSettleLogMapper extends BaseMapper<TSettleLog> {

    /** 按 job + biz_date 查唯一行（UK 保证最多一行） */
    @Select("SELECT * FROM t_settle_log WHERE job_name = #{jobName} AND biz_date = #{bizDate} LIMIT 1")
    TSettleLog selectByJobAndDate(@Param("jobName") String jobName,
                                  @Param("bizDate") LocalDate bizDate);

    /**
     * upsert 为 running 状态。
     *
     * 首次执行：INSERT 一行 status=running started_at=NOW。
     * 已有行（failed 或 running 残留）：ON DUPLICATE KEY UPDATE 重置 status=running，
     * 清 finished_at / error_message。
     *
     * 注意：调用方必须先查 status=success → 命中则跳过此 upsert。
     */
    @Insert("INSERT INTO t_settle_log (job_name, biz_date, status, started_at) " +
            "VALUES (#{jobName}, #{bizDate}, 'running', NOW()) " +
            "ON DUPLICATE KEY UPDATE status = 'running', started_at = NOW(), " +
            "finished_at = NULL, error_message = NULL")
    int upsertRunning(@Param("jobName") String jobName,
                      @Param("bizDate") LocalDate bizDate);
}
