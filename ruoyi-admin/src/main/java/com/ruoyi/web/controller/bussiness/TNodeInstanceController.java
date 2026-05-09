package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.service.INodeInstanceService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 矿机实例 Controller（金矿 Phase 1 admin）
 *
 * 不允许物理删除——业务追溯需要。状态切换只能 freeze / unfreeze。
 */
@RestController
@RequestMapping("/bussiness/node/instance")
public class TNodeInstanceController extends BaseController {

    @Autowired
    private INodeInstanceService nodeInstanceService;

    /**
     * 分页查询矿机实例。
     * 过滤参数（可选）：userId / status / levelCode
     */
    @PreAuthorize("@ss.hasPermi('bussiness:node:instance:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "10") int pageSize,
                           TNodeInstance query) {
        IPage<TNodeInstance> page = nodeInstanceService.page(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:instance:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(nodeInstanceService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:instance:freeze')")
    @Log(title = "矿机实例 - 冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/freeze")
    public AjaxResult freeze(@PathVariable("id") Long id,
                             @RequestParam("reason") String reason) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(nodeInstanceService.freeze(id, operatorAdminId, reason));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:instance:freeze')")
    @Log(title = "矿机实例 - 解冻", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/unfreeze")
    public AjaxResult unfreeze(@PathVariable("id") Long id,
                               @RequestParam("reason") String reason) {
        Long operatorAdminId = SecurityUtils.getUserId();
        return success(nodeInstanceService.unfreeze(id, operatorAdminId, reason));
    }
}
