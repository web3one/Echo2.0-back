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
 * 代理等级变更流水（H5 申请审核 / admin 直改 / 系统冻结 三路统一审计）
 *
 * source 字段区分来源：
 *   - user_apply：H5 申请审核通过，application_id 必填
 *   - admin_direct：客服后台直改（决策 3 通道 2），operator_admin_id 必填
 *   - system_freeze / system_unfreeze：系统冻结/解冻
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_agent_level_change_log")
public class TAgentLevelChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_USER_APPLY = "user_apply";
    public static final String SOURCE_ADMIN_DIRECT = "admin_direct";
    public static final String SOURCE_SYSTEM_FREEZE = "system_freeze";
    public static final String SOURCE_SYSTEM_UNFREEZE = "system_unfreeze";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fromLevel;

    private String toLevel;

    private String source;

    /** 关联 t_agent_application.id（source=user_apply 时必填） */
    private Long applicationId;

    /** 操作人 admin user id（system_* 时为 NULL） */
    private Long operatorAdminId;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
