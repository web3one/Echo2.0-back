package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TAgentStatus;

/**
 * 用户代理等级状态服务（金矿 Phase 1）
 *
 * 实现决策 3 双通道（H5 申请审核 + admin 客服直改）的"修改用户代理等级"统一入口。
 */
public interface IAgentStatusService {

    /**
     * 查询用户当前代理等级状态。如果没有则懒建一行 V0 active。
     */
    TAgentStatus getOrInitByUserId(Long userId);

    /**
     * 修改用户代理等级（双通道统一入口）。
     *
     * 同事务内：
     *   1. 校验目标等级在 t_agent_level 中存在
     *   2. 读旧等级；若与目标等级相同直接返回（幂等）
     *   3. 写 t_agent_level_change_log 一行
     *   4. UPDATE t_agent_status：agent_level + promoted_at + last_change_log_id
     *
     * @param userId          被修改用户ID
     * @param newLevel        目标等级 V0-V5
     * @param source          来源 user_apply / admin_direct / system_freeze / system_unfreeze
     * @param applicationId   关联申请ID（source=user_apply 时必填）
     * @param operatorAdminId 操作人 admin user id（system_* 时为 NULL）
     * @param reason          变更原因（必填便于审计）
     * @return 变更后的 TAgentStatus
     */
    TAgentStatus changeAgentLevel(Long userId,
                                  String newLevel,
                                  String source,
                                  Long applicationId,
                                  Long operatorAdminId,
                                  String reason);

    /**
     * 冻结用户代理（追问 A：所有 6 类奖励停发，但下线业绩照算）。
     */
    TAgentStatus freeze(Long userId, Long operatorAdminId, String reason);

    /**
     * 解冻（不补发冻结期间应得未发的奖）。
     */
    TAgentStatus unfreeze(Long userId, Long operatorAdminId, String reason);
}
