package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TSettleLog;
import com.ruoyi.bussiness.domain.vo.SettleReconcileVO;
import com.ruoyi.bussiness.service.IGoldSettleManager;
import com.ruoyi.bussiness.service.ITSettleLogService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 金矿 6 个定时任务结算日志 Controller（admin 监控 + 重试）
 *
 * 监控：
 *   GET /bussiness/settle/list?jobName=&amp;bizDate=&amp;status=&amp;pageNum=&amp;pageSize=
 *   GET /bussiness/settle/{id}
 *
 * 重试（仅 status=failed 行可重试，已 success 拒绝；running 拒绝）：
 *   POST /bussiness/settle/retry/{jobName}/{bizDate}
 *
 * 错误码（i18n）：
 *   settle.not_found / .duplicate / .running / .retry.failed / .job.unknown
 */
@RestController
@RequestMapping("/bussiness/settle")
public class TSettleLogController extends BaseController {

    @Autowired
    private ITSettleLogService settleLogService;

    @Autowired
    private IGoldSettleManager goldSettleManager;

    @PreAuthorize("@ss.hasPermi('bussiness:settle:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "10") int pageSize,
                           @RequestParam(value = "jobName", required = false) String jobName,
                           @RequestParam(value = "bizDate", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
                           @RequestParam(value = "status", required = false) String status) {
        IPage<TSettleLog> page = settleLogService.page(pageNum, pageSize, jobName, bizDate, status);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:settle:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(settleLogService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:settle:retry')")
    @Log(title = "金矿结算 - 重试", businessType = BusinessType.UPDATE)
    @PostMapping("/retry/{jobName}/{bizDate}")
    public AjaxResult retry(@PathVariable("jobName") String jobName,
                            @PathVariable("bizDate")
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        Long adminId = SecurityUtils.getUserId();
        TSettleLog row = goldSettleManager.retry(jobName, bizDate, adminId);
        return success(row);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:settle:export')")
    @Log(title = "金矿结算 - 导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(value = "jobName", required = false) String jobName,
                       @RequestParam(value = "bizDate", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
                       @RequestParam(value = "status", required = false) String status) {
        List<TSettleLog> rows = settleLogService.listForExport(jobName, bizDate, status);
        ExcelUtil<TSettleLog> util = new ExcelUtil<>(TSettleLog.class);
        util.exportExcel(response, rows, "金矿结算日志");
    }

    @PreAuthorize("@ss.hasPermi('bussiness:settle:reconcile')")
    @GetMapping("/reconcile")
    public AjaxResult reconcile(@RequestParam("bizDate")
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        SettleReconcileVO vo = settleLogService.reconcile(bizDate);
        return success(vo);
    }
}
