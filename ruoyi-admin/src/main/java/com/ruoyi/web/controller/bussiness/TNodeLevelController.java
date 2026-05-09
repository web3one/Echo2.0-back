package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TNodeLevel;
import com.ruoyi.bussiness.service.INodeLevelService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 矿机等级配置 Controller（金矿 Phase 1 admin）
 *
 * L1-L4 默认配置已在 V20260510_026 迁移以 enabled=0 插入；admin 上线前必须 review
 * 启用。修改后影响后续新购买的矿机；存量矿机价格/收益率/封顶/出局目标已在 t_node_instance
 * 中快照不受影响。
 */
@RestController
@RequestMapping("/bussiness/node/level")
public class TNodeLevelController extends BaseController {

    @Autowired
    private INodeLevelService nodeLevelService;

    /** 矿机等级列表（按 sort 升序） */
    @PreAuthorize("@ss.hasPermi('bussiness:node:level:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        List<TNodeLevel> all = nodeLevelService.listAll();
        return success(all);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:level:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(nodeLevelService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:level:add')")
    @Log(title = "矿机等级配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TNodeLevel record) {
        return toAjax(nodeLevelService.insert(record));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:level:edit')")
    @Log(title = "矿机等级配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TNodeLevel record) {
        return toAjax(nodeLevelService.update(record));
    }

    /** 启用/停用快捷切换 */
    @PreAuthorize("@ss.hasPermi('bussiness:node:level:edit')")
    @Log(title = "矿机等级 - 启停", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/enabled/{enabled}")
    public AjaxResult toggleEnabled(@PathVariable("id") Long id,
                                    @PathVariable("enabled") Integer enabled) {
        return toAjax(nodeLevelService.toggleEnabled(id, enabled));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:node:level:remove')")
    @Log(title = "矿机等级配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(nodeLevelService.deleteById(id));
    }
}
