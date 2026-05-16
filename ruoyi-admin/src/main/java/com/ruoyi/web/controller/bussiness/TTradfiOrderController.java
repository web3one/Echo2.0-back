package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TTradfiOrder;
import com.ruoyi.bussiness.service.ITTradfiOrderService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * TradFi 交易订单后台 Controller
 */
@RestController
@RequestMapping("/bussiness/tradfi/order")
public class TTradfiOrderController extends BaseController {

    @Resource
    private ITTradfiOrderService tTradfiOrderService;

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(TTradfiOrder tTradfiOrder) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        if (!user.isAdmin()) {
            if (StringUtils.isNotBlank(user.getUserType()) && !user.getUserType().equals("0")) {
                tTradfiOrder.setAdminParentIds(String.valueOf(user.getUserId()));
            }
        }
        startPage();
        List<TTradfiOrder> list = tTradfiOrderService.selectTTradfiOrderList(tTradfiOrder);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:export')")
    @Log(title = "TradFi交易订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TTradfiOrder tTradfiOrder) {
        List<TTradfiOrder> list = tTradfiOrderService.selectTTradfiOrderList(tTradfiOrder);
        ExcelUtil<TTradfiOrder> util = new ExcelUtil<>(TTradfiOrder.class);
        util.exportExcel(response, list, "TradFi交易订单数据");
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(tTradfiOrderService.selectTTradfiOrderById(id));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:add')")
    @Log(title = "TradFi交易订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TTradfiOrder tTradfiOrder) {
        return toAjax(tTradfiOrderService.insertTTradfiOrder(tTradfiOrder));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:edit')")
    @Log(title = "TradFi交易订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TTradfiOrder tTradfiOrder) {
        return toAjax(tTradfiOrderService.updateTTradfiOrder(tTradfiOrder));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:tradfi:order:remove')")
    @Log(title = "TradFi交易订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(tTradfiOrderService.deleteTTradfiOrderByIds(ids));
    }
}
