package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TAgentApplication;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;

/**
 * 代理申请审核服务（admin 通道，金矿 Phase 2）
 *
 * 审核通过统一调 IAgentStatusService.changeAgentLevel(source='user_apply')；
 * 拒绝仅 UPDATE t_agent_application.status=rejected，不动 t_agent_status。
 */
public interface IAgencyReviewService {

    /**
     * 分页列表（按 create_time DESC）。
     * 过滤：status / userId / targetLevel
     */
    IPage<AgentApplicationVO> pageList(int pageNum, int pageSize, TAgentApplication query);

    /** 审核详情（含 snap 与当前真值双列 + eligibleNow 判断） */
    AgentApplicationVO getDetail(Long id);

    /** 审核通过：UPDATE status='approved' + 调 agentStatusService 改等级 */
    TAgentApplication approve(Long id, Long reviewAdminId, String reviewRemark);

    /** 审核拒绝：仅 UPDATE status='rejected' */
    TAgentApplication reject(Long id, Long reviewAdminId, String reviewRemark);
}
