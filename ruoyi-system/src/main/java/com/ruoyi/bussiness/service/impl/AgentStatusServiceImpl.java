package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.TAgentLevelChangeLog;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.mapper.TAgentLevelChangeLogMapper;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.service.IAgentStatusService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 用户代理等级状态服务实现（金矿 Phase 1）
 *
 * 决策 3 双通道（H5 申请审核 + admin 客服直改）的代理等级修改统一入口。
 * 所有等级变更必经 changeAgentLevel —— 强制写 t_agent_level_change_log 流水审计。
 */
@Service
@Slf4j
public class AgentStatusServiceImpl implements IAgentStatusService {

    @Resource
    private TAgentStatusMapper agentStatusMapper;

    @Resource
    private TAgentLevelMapper agentLevelMapper;

    @Resource
    private TAgentLevelChangeLogMapper agentLevelChangeLogMapper;

    @Override
    @Transactional
    public TAgentStatus getOrInitByUserId(Long userId) {
        if (userId == null) {
            throw new ServiceException("userId 不能为空");
        }
        TAgentStatus status = agentStatusMapper.selectByUserId(userId);
        if (status != null) {
            return status;
        }
        // 懒建：用户首次访问代理体系时创建一行 V0 active
        status = new TAgentStatus();
        status.setUserId(userId);
        status.setAgentLevel(TAgentStatus.LEVEL_V0);
        status.setStatus(TAgentStatus.STATUS_ACTIVE);
        agentStatusMapper.insert(status);
        return status;
    }

    @Override
    @Transactional
    public TAgentStatus changeAgentLevel(Long userId,
                                         String newLevel,
                                         String source,
                                         Long applicationId,
                                         Long operatorAdminId,
                                         String reason) {
        // ---- 参数校验 ----
        if (userId == null) {
            throw new ServiceException("userId 不能为空");
        }
        if (StrUtil.isBlank(newLevel)) {
            throw new ServiceException("newLevel 不能为空");
        }
        if (StrUtil.isBlank(source)) {
            throw new ServiceException("source 不能为空");
        }
        if (StrUtil.isBlank(reason)) {
            // 决策 3：reason 必填便于审计
            throw new ServiceException("变更原因 reason 必填");
        }

        // 校验目标等级在 t_agent_level 中存在
        TAgentLevel targetLevelConfig = agentLevelMapper.selectByLevelCode(newLevel);
        if (targetLevelConfig == null) {
            throw new ServiceException("目标等级不存在: " + newLevel);
        }

        // 校验 source 与必填关联字段一致性
        if (TAgentLevelChangeLog.SOURCE_USER_APPLY.equals(source)) {
            if (applicationId == null) {
                throw new ServiceException("source=user_apply 时 applicationId 必填");
            }
        } else if (TAgentLevelChangeLog.SOURCE_ADMIN_DIRECT.equals(source)) {
            if (operatorAdminId == null) {
                throw new ServiceException("source=admin_direct 时 operatorAdminId 必填");
            }
        } else if (!TAgentLevelChangeLog.SOURCE_SYSTEM_FREEZE.equals(source)
                && !TAgentLevelChangeLog.SOURCE_SYSTEM_UNFREEZE.equals(source)) {
            throw new ServiceException("非法 source: " + source);
        }

        // ---- 读旧状态（自动懒建）----
        TAgentStatus current = getOrInitByUserId(userId);

        // ---- 幂等：等级一致直接返回 ----
        if (newLevel.equals(current.getAgentLevel())) {
            log.info("changeAgentLevel skip (level unchanged): userId={} level={}", userId, newLevel);
            return current;
        }

        // ---- 写流水 ----
        TAgentLevelChangeLog changeLog = new TAgentLevelChangeLog();
        changeLog.setUserId(userId);
        changeLog.setFromLevel(current.getAgentLevel());
        changeLog.setToLevel(newLevel);
        changeLog.setSource(source);
        changeLog.setApplicationId(applicationId);
        changeLog.setOperatorAdminId(operatorAdminId);
        changeLog.setReason(reason);
        agentLevelChangeLogMapper.insert(changeLog);

        // ---- 更新代理等级状态 ----
        current.setAgentLevel(newLevel);
        current.setPromotedAt(new Date());
        current.setLastChangeLogId(changeLog.getId());
        agentStatusMapper.updateById(current);

        log.info("changeAgentLevel done: userId={} {}->{} source={} operatorAdmin={} logId={}",
                userId, changeLog.getFromLevel(), newLevel, source, operatorAdminId, changeLog.getId());
        return current;
    }

    @Override
    @Transactional
    public TAgentStatus freeze(Long userId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("冻结原因必填");
        }
        TAgentStatus current = getOrInitByUserId(userId);

        // 幂等：已冻结直接返回
        if (TAgentStatus.STATUS_FROZEN.equals(current.getStatus())) {
            return current;
        }

        // 写流水（fromLevel=toLevel，仅记录"冻结"事件）
        TAgentLevelChangeLog changeLog = new TAgentLevelChangeLog();
        changeLog.setUserId(userId);
        changeLog.setFromLevel(current.getAgentLevel());
        changeLog.setToLevel(current.getAgentLevel());
        changeLog.setSource(TAgentLevelChangeLog.SOURCE_SYSTEM_FREEZE);
        changeLog.setOperatorAdminId(operatorAdminId);
        changeLog.setReason(reason);
        agentLevelChangeLogMapper.insert(changeLog);

        current.setStatus(TAgentStatus.STATUS_FROZEN);
        current.setFrozenAt(new Date());
        current.setFrozenReason(reason);
        current.setFrozenByAdminId(operatorAdminId);
        current.setLastChangeLogId(changeLog.getId());
        agentStatusMapper.updateById(current);

        log.info("freeze agent: userId={} level={} operatorAdmin={} reason={}",
                userId, current.getAgentLevel(), operatorAdminId, reason);
        return current;
    }

    @Override
    @Transactional
    public TAgentStatus unfreeze(Long userId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("解冻原因必填");
        }
        TAgentStatus current = getOrInitByUserId(userId);

        if (TAgentStatus.STATUS_ACTIVE.equals(current.getStatus())) {
            return current;
        }

        TAgentLevelChangeLog changeLog = new TAgentLevelChangeLog();
        changeLog.setUserId(userId);
        changeLog.setFromLevel(current.getAgentLevel());
        changeLog.setToLevel(current.getAgentLevel());
        changeLog.setSource(TAgentLevelChangeLog.SOURCE_SYSTEM_UNFREEZE);
        changeLog.setOperatorAdminId(operatorAdminId);
        changeLog.setReason(reason);
        agentLevelChangeLogMapper.insert(changeLog);

        current.setStatus(TAgentStatus.STATUS_ACTIVE);
        // 追问 A：不补发冻结期间应得未发的奖；这里保留 frozenAt/frozenReason 作为"曾经冻结"记录
        // 改：清空便于"当前是否冻结"判断更直观；历史在 t_agent_level_change_log 完整保留
        current.setFrozenAt(null);
        current.setFrozenReason(null);
        current.setFrozenByAdminId(null);
        current.setLastChangeLogId(changeLog.getId());
        agentStatusMapper.updateById(current);

        log.info("unfreeze agent: userId={} level={} operatorAdmin={} reason={}",
                userId, current.getAgentLevel(), operatorAdminId, reason);
        return current;
    }
}
