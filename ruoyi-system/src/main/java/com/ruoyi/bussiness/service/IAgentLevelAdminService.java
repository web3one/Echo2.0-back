package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.dto.AgentLevelUpdateDTO;

import java.util.List;

/**
 * 代理等级配置 admin 服务（PRD §15.3）。
 *
 * 仅允许"读 + 改"，不允许新增/删除：V0-V5 是业务硬约束，固定 6 条。
 *
 * @date 2026-05-12
 */
public interface IAgentLevelAdminService {

    List<TAgentLevel> listAll();

    TAgentLevel getById(Long id);

    /**
     * 更新等级参数（match_rate / global_dividend_rate / 升级条件等）。
     * level_code / id / 创建时间 不允许修改。
     */
    TAgentLevel update(Long id, AgentLevelUpdateDTO dto, Long adminId);
}
