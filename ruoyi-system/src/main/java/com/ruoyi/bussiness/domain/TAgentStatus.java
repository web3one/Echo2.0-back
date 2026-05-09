package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户当前代理等级状态（金矿 Phase 1）
 *
 * status='frozen' 时所有 6 类奖励停发（追问 A），但下线业绩照常累计到双轨树。
 * 用户首次注册由 TAppUserServiceImpl 写入默认 V0 active 行。
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_agent_status")
public class TAgentStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String LEVEL_V0 = "V0";
    public static final String LEVEL_V1 = "V1";
    public static final String LEVEL_V2 = "V2";
    public static final String LEVEL_V3 = "V3";
    public static final String LEVEL_V4 = "V4";
    public static final String LEVEL_V5 = "V5";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FROZEN = "frozen";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 当前代理等级 V0-V5（默认 V0） */
    private String agentLevel;

    /** 状态 active / frozen */
    private String status;

    private Date frozenAt;

    private String frozenReason;

    private Long frozenByAdminId;

    /** 当前等级生效时间 */
    private Date promotedAt;

    /** 最近一次等级变更记录ID（关联 t_agent_level_change_log） */
    private Long lastChangeLogId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
