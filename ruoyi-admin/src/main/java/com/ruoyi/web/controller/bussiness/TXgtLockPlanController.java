package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.dto.XgtLockPlanCreateDTO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanAdminVO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanDetailVO;
import com.ruoyi.bussiness.service.IXgtLockAdminService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * XGT 锁仓管理 Controller（PRD §15.5 admin 通道）。
 *
 *  - GET  /bussiness/xgtLock/list             分页列表
 *  - GET  /bussiness/xgtLock/{id}             详情（含用户当前 XGT 余额）
 *  - POST /bussiness/xgtLock/create           手工创建
 *  - POST /bussiness/xgtLock/{id}/freeze      冻结
 *  - POST /bussiness/xgtLock/{id}/unfreeze    解冻（恢复 locked，cron 自动接力）
 *  - POST /bussiness/xgtLock/{id}/retry       手动补发释放
 *  - POST /bussiness/xgtLock/export           导出 Excel
 *
 * @date 2026-05-12
 */
@RestController
@RequestMapping("/bussiness/xgtLock")
public class TXgtLockPlanController extends BaseController {

    @Autowired
    private IXgtLockAdminService xgtLockAdminService;

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:list')")
    @GetMapping("/list")
    public TableDataInfo list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(value = "beginLockedAt", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginLockedAt,
            @RequestParam(value = "endLockedAt", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endLockedAt,
            @RequestParam(value = "beginReleaseAt", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginReleaseAt,
            @RequestParam(value = "endReleaseAt", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endReleaseAt) {
        IPage<XgtLockPlanAdminVO> page = xgtLockAdminService.pageList(
                pageNum, pageSize, userId, sourceType, status,
                beginLockedAt, endLockedAt, beginReleaseAt, endReleaseAt);
        TableDataInfo info = new TableDataInfo();
        info.setCode(200);
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        return info;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable("id") Long id) {
        XgtLockPlanDetailVO detail = xgtLockAdminService.getDetail(id);
        return success(detail);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:create')")
    @Log(title = "XGT 锁仓 - 创建", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult create(@RequestBody XgtLockPlanCreateDTO dto) {
        Long adminId = SecurityUtils.getUserId();
        TXgtLockPlan plan = xgtLockAdminService.create(dto, adminId);
        return success(plan);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:freeze')")
    @Log(title = "XGT 锁仓 - 冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/freeze")
    public AjaxResult freeze(@PathVariable("id") Long id,
                             @RequestParam("reason") String reason) {
        Long adminId = SecurityUtils.getUserId();
        return success(xgtLockAdminService.freeze(id, adminId, reason));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:freeze')")
    @Log(title = "XGT 锁仓 - 解冻", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/unfreeze")
    public AjaxResult unfreeze(@PathVariable("id") Long id,
                               @RequestParam("reason") String reason) {
        Long adminId = SecurityUtils.getUserId();
        return success(xgtLockAdminService.unfreeze(id, adminId, reason));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:retry')")
    @Log(title = "XGT 锁仓 - 手动补发", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/retry")
    public AjaxResult retry(@PathVariable("id") Long id) {
        Long adminId = SecurityUtils.getUserId();
        Map<String, Object> result = xgtLockAdminService.retry(id, adminId);
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:xgtLock:export')")
    @Log(title = "XGT 锁仓 - 导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) String sourceType,
                       @RequestParam(required = false) String status,
                       @RequestParam(value = "beginLockedAt", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginLockedAt,
                       @RequestParam(value = "endLockedAt", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endLockedAt,
                       @RequestParam(value = "beginReleaseAt", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginReleaseAt,
                       @RequestParam(value = "endReleaseAt", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endReleaseAt) {
        List<XgtLockPlanAdminVO> rows = xgtLockAdminService.listForExport(
                userId, sourceType, status,
                beginLockedAt, endLockedAt, beginReleaseAt, endReleaseAt);
        ExcelUtil<XgtLockPlanAdminVO> util = new ExcelUtil<>(XgtLockPlanAdminVO.class);
        util.exportExcel(response, rows, "XGT锁仓计划");
    }
}
