package com.ruoyi.bussiness.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * admin 后台"客服直改用户代理等级"请求体（决策 3 通道 2）。
 *
 * targetLevel: V0-V5 任一
 * reason:      变更原因（必填便于审计 t_agent_level_change_log）
 */
@Data
public class AdminAgentLevelChangeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String targetLevel;

    private String reason;
}
