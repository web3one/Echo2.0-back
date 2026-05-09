package com.ruoyi.web.controller.bussiness;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.bussiness.domain.TC2cConfig;
import com.ruoyi.bussiness.service.IC2cConfigService;

/**
 * C2C全局配置Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/bussiness/c2c/config")
public class C2cConfigController extends BaseController {

    @Autowired
    private IC2cConfigService c2cConfigService;

    /**
     * 查询C2C配置列表
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:config:list')")
    @GetMapping("/list")
    public TableDataInfo list() {
        startPage();
        List<TC2cConfig> list = c2cConfigService.selectConfigList();
        return getDataTable(list);
    }

    /**
     * 修改C2C配置
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:config:edit')")
    @Log(title = "C2C配置管理", businessType = BusinessType.UPDATE)
    @PutMapping("/update")
    public AjaxResult update(@RequestBody TC2cConfig config) {
        return toAjax(c2cConfigService.updateConfig(config));
    }
}
