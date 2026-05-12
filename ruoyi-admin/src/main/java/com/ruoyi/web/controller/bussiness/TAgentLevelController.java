package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.dto.AgentLevelUpdateDTO;
import com.ruoyi.bussiness.service.IAgentLevelAdminService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代理等级配置 Controller（PRD §15.3）。
 *
 *  - GET /bussiness/agentLevel/list   全 V0-V5 配置
 *  - GET /bussiness/agentLevel/{id}   详情
 *  - PUT /bussiness/agentLevel/{id}   更新参数（level_code 不可改）
 *
 * 禁止新增/删除：V0-V5 是业务硬约束。
 *
 * @date 2026-05-12
 */
@RestController
@RequestMapping("/bussiness/agentLevel")
public class TAgentLevelController extends BaseController {

    @Autowired
    private IAgentLevelAdminService agentLevelAdminService;

    @PreAuthorize("@ss.hasPermi('bussiness:gold:agentLevel:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return success(agentLevelAdminService.listAll());
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:agentLevel:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable("id") Long id) {
        return success(agentLevelAdminService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:agentLevel:edit')")
    @Log(title = "代理等级配置 - 更新", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable("id") Long id,
                             @RequestBody AgentLevelUpdateDTO dto) {
        Long adminId = SecurityUtils.getUserId();
        TAgentLevel level = agentLevelAdminService.update(id, dto, adminId);
        return success(level);
    }
}
