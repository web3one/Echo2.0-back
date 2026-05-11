package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TAgentApplication;
import com.ruoyi.bussiness.domain.dto.AgencyReviewDTO;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;
import com.ruoyi.bussiness.service.IAgencyReviewService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代理申请审核 Controller（金矿 Phase 2 admin 通道）。
 *
 * H5 用户走 ApiAgencyController 提交申请，admin 客服走本 controller 审核。
 * 审核通过统一调 IAgentStatusService.changeAgentLevel(source='user_apply')，
 * 与"客服直改"双通道共享 t_agent_level_change_log 审计流水。
 *
 * @date 2026-05-11
 */
@RestController
@RequestMapping("/bussiness/agent/application")
public class TAgentApplicationController extends BaseController {

    @Autowired
    private IAgencyReviewService agencyReviewService;

    /**
     * 分页查询代理申请列表。
     * 过滤参数（可选）：userId / status (pending|approved|rejected|cancelled) / targetLevel
     */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:application:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "10") int pageSize,
                           TAgentApplication query) {
        IPage<AgentApplicationVO> page = agencyReviewService.pageList(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }

    /** 审核详情（含 snap 与当前真值双列 + eligibleNow 判定） */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:application:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(agencyReviewService.getDetail(id));
    }

    /** 审核通过：UPDATE status=approved + 调代理状态服务改等级（source=user_apply） */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:application:approve')")
    @Log(title = "代理申请 - 审核通过", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/approve")
    public AjaxResult approve(@PathVariable("id") Long id,
                              @RequestBody AgencyReviewDTO dto) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(agencyReviewService.approve(id, operatorAdminId, dto == null ? null : dto.getReviewRemark()));
    }

    /** 审核拒绝：仅 UPDATE 本表状态，不动 t_agent_status */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:application:reject')")
    @Log(title = "代理申请 - 审核拒绝", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reject")
    public AjaxResult reject(@PathVariable("id") Long id,
                             @RequestBody AgencyReviewDTO dto) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(agencyReviewService.reject(id, operatorAdminId, dto == null ? null : dto.getReviewRemark()));
    }
}
