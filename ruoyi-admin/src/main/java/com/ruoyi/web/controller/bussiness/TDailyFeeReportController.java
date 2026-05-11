package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.service.IGoldWithdrawAdminService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日手续费报表 admin Controller（B 路线第一组）。
 *
 *  - GET /bussiness/gold/fee/recent?days=30   最近 N 天报表
 *  - GET /bussiness/gold/fee/{bizDate}        单日详情（yyyy-MM-dd）
 *
 * @date 2026-05-15
 */
@RestController
@RequestMapping("/bussiness/gold/fee")
public class TDailyFeeReportController extends BaseController {

    @Autowired
    private IGoldWithdrawAdminService goldWithdrawAdminService;

    @PreAuthorize("@ss.hasPermi('bussiness:gold:fee:list')")
    @GetMapping("/recent")
    public AjaxResult recent(@RequestParam(defaultValue = "30") Integer days) {
        List<TDailyFeeSummary> rows = goldWithdrawAdminService.recentFeeSummaries(days);
        AjaxResult res = AjaxResult.success();
        res.put("rows", rows);
        res.put("total", rows == null ? 0 : rows.size());
        return res;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:fee:query')")
    @GetMapping("/{bizDate}")
    public AjaxResult getOne(@PathVariable("bizDate")
                             @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate bizDate) {
        return success(goldWithdrawAdminService.getFeeSummaryByDate(bizDate));
    }
}
