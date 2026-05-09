package com.ruoyi.web.controller.bussiness;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.service.IC2cMerchantService;

/**
 * C2C商家管理Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/bussiness/c2c/merchant")
public class C2cMerchantController extends BaseController {

    @Autowired
    private IC2cMerchantService c2cMerchantService;

    /**
     * 查询C2C商家列表
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:list')")
    @GetMapping("/list")
    public TableDataInfo list(TC2cMerchant merchant) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        if (!user.isAdmin()) {
            merchant.setAdminParentIds(String.valueOf(user.getUserId()));
        }
        startPage();
        List<TC2cMerchant> list = c2cMerchantService.selectMerchantList(merchant);
        return getDataTable(list);
    }

    /**
     * 获取C2C商家详细信息
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(c2cMerchantService.getById(id));
    }

    /**
     * 审批通过商家申请
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:edit')")
    @Log(title = "C2C商家管理", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{id}")
    public AjaxResult approve(@PathVariable Long id) {
        return toAjax(c2cMerchantService.approveMerchant(id));
    }

    /**
     * 拒绝商家申请
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:edit')")
    @Log(title = "C2C商家管理", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{id}")
    public AjaxResult reject(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String reason = params.get("reason");
        return toAjax(c2cMerchantService.rejectMerchant(id, reason));
    }

    /**
     * 禁用商家
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:edit')")
    @Log(title = "C2C商家管理", businessType = BusinessType.UPDATE)
    @PutMapping("/disable/{id}")
    public AjaxResult disable(@PathVariable Long id) {
        return toAjax(c2cMerchantService.disableMerchant(id));
    }

    /**
     * 启用商家
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:merchant:edit')")
    @Log(title = "C2C商家管理", businessType = BusinessType.UPDATE)
    @PutMapping("/enable/{id}")
    public AjaxResult enable(@PathVariable Long id) {
        return toAjax(c2cMerchantService.enableMerchant(id));
    }
}
