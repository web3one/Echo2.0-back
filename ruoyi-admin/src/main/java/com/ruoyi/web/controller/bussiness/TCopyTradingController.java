package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TCopyTrader;
import com.ruoyi.bussiness.domain.TCopyTraderApply;
import com.ruoyi.bussiness.domain.dto.CopyTraderReviewDTO;
import com.ruoyi.bussiness.domain.dto.CopyTraderStatusDTO;
import com.ruoyi.bussiness.service.ICopyTradingService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/bussiness/copy/trader")
public class TCopyTradingController extends BaseController {
    @Resource
    private ICopyTradingService copyTradingService;

    @PreAuthorize("@ss.hasPermi('bussiness:copy:trader:application:list')")
    @GetMapping("/applications")
    public TableDataInfo applications(TCopyTraderApply query) {
        startPage();
        return getDataTable(copyTradingService.adminApplications(query));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:copy:trader:application:approve')")
    @Log(title = "跟单交易员申请通过", businessType = BusinessType.UPDATE)
    @PostMapping("/applications/{id}/approve")
    public AjaxResult approve(@PathVariable("id") Long id, @RequestBody(required = false) CopyTraderReviewDTO dto) {
        return success(copyTradingService.approveApplication(id, SecurityUtils.getUserId(), dto == null ? null : dto.getRemark()));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:copy:trader:application:reject')")
    @Log(title = "跟单交易员申请拒绝", businessType = BusinessType.UPDATE)
    @PostMapping("/applications/{id}/reject")
    public AjaxResult reject(@PathVariable("id") Long id, @RequestBody(required = false) CopyTraderReviewDTO dto) {
        copyTradingService.rejectApplication(id, SecurityUtils.getUserId(), dto == null ? null : dto.getRemark());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('bussiness:copy:trader:list')")
    @GetMapping("/list")
    public TableDataInfo traders(TCopyTrader query) {
        startPage();
        return getDataTable(copyTradingService.adminTraders(query));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:copy:trader:status')")
    @Log(title = "跟单交易员启停", businessType = BusinessType.UPDATE)
    @PostMapping("/{userId}/status")
    public AjaxResult updateStatus(@PathVariable("userId") Long userId, @RequestBody CopyTraderStatusDTO dto) {
        copyTradingService.updateTraderStatus(userId, dto == null ? null : dto.getStatus(), dto == null ? null : dto.getRemark());
        return success();
    }
}
