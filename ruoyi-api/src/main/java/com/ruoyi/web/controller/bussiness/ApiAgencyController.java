package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.dto.AgencyApplyDTO;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;
import com.ruoyi.bussiness.domain.vo.MyAgentVO;
import com.ruoyi.bussiness.service.IAgencyApplyService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 代理升级申请用户端 Controller（金矿 Phase 2 H5 通道 1）。
 *
 * GET  /api/agency/me                          我的代理状态 + 升级条件矩阵
 * POST /api/agency/apply                       提交升级申请
 * GET  /api/agency/applications/my             我的申请历史
 * POST /api/agency/applications/{id}/cancel    撤销 pending 申请
 *
 * @date 2026-05-11
 */
@RestController
@RequestMapping("/api/agency")
@Slf4j
public class ApiAgencyController extends ApiBaseController {

    @Resource
    private IAgencyApplyService agencyApplyService;

    @GetMapping("/me")
    public AjaxResult me() {
        MyAgentVO vo = agencyApplyService.getMyAgent(getStpUserId());
        return success(vo);
    }

    @PostMapping("/apply")
    public AjaxResult apply(@RequestBody AgencyApplyDTO dto) {
        try {
            agencyApplyService.submitApplication(getStpUserId(), dto);
            return success();
        } catch (ServiceException e) {
            log.warn("agency apply rejected: userId={} target={} err={}",
                    getStpUserId(), dto == null ? null : dto.getTargetLevel(), e.getMessage());
            return error(e.getMessage());
        }
    }

    @GetMapping("/applications/my")
    public AjaxResult myApplications() {
        List<AgentApplicationVO> list = agencyApplyService.listMyApplications(getStpUserId());
        return success(list);
    }

    @PostMapping("/applications/{id}/cancel")
    public AjaxResult cancel(@PathVariable("id") Long applicationId) {
        try {
            agencyApplyService.cancelMyApplication(getStpUserId(), applicationId);
            return success();
        } catch (ServiceException e) {
            log.warn("agency apply cancel rejected: userId={} appId={} err={}",
                    getStpUserId(), applicationId, e.getMessage());
            return error(e.getMessage());
        }
    }
}
