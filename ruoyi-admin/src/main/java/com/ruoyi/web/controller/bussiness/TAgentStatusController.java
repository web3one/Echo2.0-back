package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TAgentLevelChangeLog;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.dto.AdminAgentLevelChangeDTO;
import com.ruoyi.bussiness.service.IAgentStatusService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户代理等级状态管理 Controller（金矿 Phase 1）
 *
 * 决策 3 通道 2：客服后台直改代理等级。所有操作落 t_agent_level_change_log
 * 流水审计；冻结/解冻同样走此 controller，不绕过流水。
 */
@RestController
@RequestMapping("/bussiness/agent/status")
public class TAgentStatusController extends BaseController {

    @Autowired
    private IAgentStatusService agentStatusService;

    /**
     * 查询用户当前代理等级状态（不存在则懒建 V0 active 返回）。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:status:query')")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable("userId") Long userId) {
        return success(agentStatusService.getOrInitByUserId(userId));
    }

    /**
     * 客服直改用户代理等级（决策 3 通道 2）。
     *
     * 请求体：{ targetLevel: "V3", reason: "用户外部材料确认达标 ticket #1234" }
     * 强制写 t_agent_level_change_log（source=admin_direct）。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:status:edit')")
    @Log(title = "代理等级 - 客服直改", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/level")
    public AjaxResult changeLevel(@PathVariable("userId") Long userId,
                                  @RequestBody AdminAgentLevelChangeDTO dto) {
        Long operatorAdminId = SecurityUtils.getUserId();
        TAgentStatus updated = agentStatusService.changeAgentLevel(
                userId,
                dto.getTargetLevel(),
                TAgentLevelChangeLog.SOURCE_ADMIN_DIRECT,
                null,
                operatorAdminId,
                dto.getReason());
        return success(updated);
    }

    /**
     * 冻结用户代理（追问 A：所有 6 类奖励停发，但下线业绩照算）。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:status:freeze')")
    @Log(title = "代理状态 - 冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{userId}/freeze")
    public AjaxResult freeze(@PathVariable("userId") Long userId,
                             @RequestParam("reason") String reason) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(agentStatusService.freeze(userId, operatorAdminId, reason));
    }

    /**
     * 解冻用户代理（不补发冻结期间应得未发的奖）。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:agent:status:freeze')")
    @Log(title = "代理状态 - 解冻", businessType = BusinessType.UPDATE)
    @PostMapping("/{userId}/unfreeze")
    public AjaxResult unfreeze(@PathVariable("userId") Long userId,
                               @RequestParam("reason") String reason) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(agentStatusService.unfreeze(userId, operatorAdminId, reason));
    }
}
