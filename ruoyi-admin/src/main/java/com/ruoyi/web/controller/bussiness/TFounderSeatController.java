package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TFounderPurchaseLog;
import com.ruoyi.bussiness.service.IFounderAdminService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创世合伙人 49 席管理 Controller（金矿 Phase 2 admin 通道）。
 *
 *  - GET  /bussiness/founder/seat/list           全 49 席
 *  - GET  /bussiness/founder/seat/{id}           详情
 *  - POST /bussiness/founder/seat/{id}/freeze    冻结
 *  - POST /bussiness/founder/seat/{id}/unfreeze  解冻
 *  - PUT  /bussiness/founder/seat/{id}/remark    改备注
 *  - GET  /bussiness/founder/log/list            购买流水分页
 *
 * @date 2026-05-11
 */
@RestController
@RequestMapping("/bussiness/founder")
public class TFounderSeatController extends BaseController {

    @Autowired
    private IFounderAdminService founderAdminService;

    /** 全 49 席（list 形式；前端按 seat_no 渲染 7×7 矩阵） */
    @PreAuthorize("@ss.hasPermi('bussiness:founder:seat:list')")
    @GetMapping("/seat/list")
    public AjaxResult listSeats() {
        return success(founderAdminService.listAllSeats());
    }

    @PreAuthorize("@ss.hasPermi('bussiness:founder:seat:query')")
    @GetMapping("/seat/{id}")
    public AjaxResult getSeat(@PathVariable("id") Long id) {
        return success(founderAdminService.getSeatById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:founder:seat:freeze')")
    @Log(title = "创世席位 - 冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/seat/{id}/freeze")
    public AjaxResult freezeSeat(@PathVariable("id") Long id,
                                 @RequestParam("reason") String reason) {
        Long admin = SecurityUtils.getUserId();
        return success(founderAdminService.freezeSeat(id, admin, reason));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:founder:seat:freeze')")
    @Log(title = "创世席位 - 解冻", businessType = BusinessType.UPDATE)
    @PostMapping("/seat/{id}/unfreeze")
    public AjaxResult unfreezeSeat(@PathVariable("id") Long id,
                                   @RequestParam("reason") String reason) {
        Long admin = SecurityUtils.getUserId();
        return success(founderAdminService.unfreezeSeat(id, admin, reason));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:founder:seat:edit')")
    @Log(title = "创世席位 - 改备注", businessType = BusinessType.UPDATE)
    @PutMapping("/seat/{id}/remark")
    public AjaxResult updateRemark(@PathVariable("id") Long id,
                                   @RequestParam(value = "remark", required = false) String remark) {
        return success(founderAdminService.updateRemark(id, remark));
    }

    /** 购买流水分页（含 userId / seatNo 过滤） */
    @PreAuthorize("@ss.hasPermi('bussiness:founder:log:list')")
    @GetMapping("/log/list")
    public AjaxResult listLogs(@RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "10") int pageSize,
                               TFounderPurchaseLog query) {
        IPage<TFounderPurchaseLog> page = founderAdminService.pageLogs(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }
}
