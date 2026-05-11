package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TAgentApplication;
import com.ruoyi.bussiness.domain.dto.AgencyApplyDTO;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;
import com.ruoyi.bussiness.domain.vo.MyAgentVO;

import java.util.List;

/**
 * 代理升级申请服务（H5 通道 1，金矿 Phase 2）
 *
 * 配套 admin 审核走 IAgencyReviewService。本接口仅服务 H5 用户：
 *   GET /api/agency/me                          查我的代理状态 + 升级条件矩阵
 *   POST /api/agency/apply                      提交升级申请
 *   GET /api/agency/applications/my             查我的申请历史
 *   POST /api/agency/applications/{id}/cancel   撤销 pending 中的申请
 */
public interface IAgencyApplyService {

    /** 查询用户代理状态 + 升级条件矩阵。无任何 t_agent_status 行时返回 V0 默认值（不写库）。 */
    MyAgentVO getMyAgent(Long userId);

    /** 提交申请；6 步校验失败抛 ServiceException + i18n key。返回新增的申请实体。 */
    TAgentApplication submitApplication(Long userId, AgencyApplyDTO dto);

    /** 申请历史（按 create_time DESC）。 */
    List<AgentApplicationVO> listMyApplications(Long userId);

    /** 撤销 pending 申请；仅本人 + 仅 pending 可撤销。 */
    TAgentApplication cancelMyApplication(Long userId, Long applicationId);
}
