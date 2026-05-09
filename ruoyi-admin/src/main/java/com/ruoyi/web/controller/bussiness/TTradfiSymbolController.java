package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TTradfiSymbol;
import com.ruoyi.bussiness.service.ITTradfiSymbolService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * TradFi 标的（股票/股指/外汇/贵金属/大宗商品）管理 Controller
 *
 * 配合 t_tradfi_symbol 表 + Gate.io TradFi WebSocket 数据源使用。
 * status / show_flag 字段直接控制 H5 是否展示。
 */
@RestController
@RequestMapping("/bussiness/tradfi")
public class TTradfiSymbolController extends BaseController {

    @Autowired
    private ITTradfiSymbolService tTradfiSymbolService;

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:list')")
    @GetMapping("/list")
    public TableDataInfo list(TTradfiSymbol query) {
        startPage();
        List<TTradfiSymbol> list = tTradfiSymbolService.selectTTradfiSymbolList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:export')")
    @Log(title = "TradFi 标的", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TTradfiSymbol query) {
        List<TTradfiSymbol> list = tTradfiSymbolService.selectTTradfiSymbolList(query);
        ExcelUtil<TTradfiSymbol> util = new ExcelUtil<>(TTradfiSymbol.class);
        util.exportExcel(response, list, "TradFi 标的");
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(tTradfiSymbolService.selectTTradfiSymbolById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:add')")
    @Log(title = "TradFi 标的", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TTradfiSymbol record) {
        TTradfiSymbol exists = tTradfiSymbolService.getOne(
                new LambdaQueryWrapper<TTradfiSymbol>().eq(TTradfiSymbol::getSymbol, record.getSymbol()));
        if (exists != null) {
            return AjaxResult.error("symbol 已存在: " + record.getSymbol());
        }
        record.setCreateBy(SecurityUtils.getUsername());
        return toAjax(tTradfiSymbolService.insertTTradfiSymbol(record));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:edit')")
    @Log(title = "TradFi 标的", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TTradfiSymbol record) {
        record.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(tTradfiSymbolService.updateTTradfiSymbol(record));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:remove')")
    @Log(title = "TradFi 标的", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(tTradfiSymbolService.deleteTTradfiSymbolByIds(ids));
    }
}
