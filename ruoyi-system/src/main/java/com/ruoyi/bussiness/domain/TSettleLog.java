package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 6 个金矿定时任务的结算日志（幂等钥匙 + admin 监控）
 *
 * UNIQUE(job_name, biz_date) 保证：同一任务同一业务日只能有一条记录。
 * 任务启动前先查 status=success 已存在即跳过；否则 upsert 为 running，
 * 任务结束 UPDATE 为 success 或 failed。多实例并发启动也只会一条 INSERT 成功。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_settle_log")
public class TSettleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    public static final String JOB_DAILY_STATIC_REWARD = "daily_static_reward_job";
    public static final String JOB_AGENCY_TEAM_REWARD = "agency_team_reward_job";
    public static final String JOB_AGENCY_GLOBAL_DIVIDEND = "agency_global_dividend_job";
    public static final String JOB_FOUNDER_DIVIDEND = "founder_dividend_job";
    public static final String JOB_XGT_LOCK_RELEASE = "xgt_lock_release_job";
    public static final String JOB_BINARY_DAILY_VOLUME_RESET = "binary_daily_volume_reset_job";
    /** B 路线第一组：每日手续费聚合（spot+contract+gold_withdraw 三类） */
    public static final String JOB_DAILY_FEE_SUMMARY = "daily_fee_summary_job";
    /** P1 收尾：每日资金池健康监控（PRD §21.1），cron UTC 00:30 跑在所有结算之后 */
    public static final String JOB_POOL_HEALTH_MONITOR = "pool_health_monitor_job";

    @Excel(name = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Excel(name = "任务名")
    private String jobName;

    @Excel(name = "业务日期", dateFormat = "yyyy-MM-dd")
    private LocalDate bizDate;

    @Excel(name = "状态")
    private String status;

    @Excel(name = "开始时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @Excel(name = "结束时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    @Excel(name = "应处理")
    private Integer totalCount;

    @Excel(name = "成功")
    private Integer successCount;

    @Excel(name = "失败")
    private Integer failedCount;

    @Excel(name = "跳过")
    private Integer skippedCount;

    @Excel(name = "金额USDT")
    private BigDecimal amountSettledUsdt;

    @Excel(name = "错误信息")
    private String errorMessage;

    @Excel(name = "重试人")
    private Long triggeredByAdminId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
