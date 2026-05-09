package com.ruoyi.web.controller.bussiness;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import com.ruoyi.bussiness.service.IC2cAdvertService;

/**
 * C2C广告管理Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/bussiness/c2c/advert")
public class C2cAdvertController extends BaseController {

    @Autowired
    private IC2cAdvertService c2cAdvertService;

    /**
     * 查询C2C广告列表
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:advert:list')")
    @GetMapping("/list")
    public TableDataInfo list(TC2cAdvert advert) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        if (!user.isAdmin()) {
            advert.setAdminParentIds(String.valueOf(user.getUserId()));
        }
        startPage();
        List<C2cAdvertVO> list = c2cAdvertService.selectAdminAdvertVOList(advert);
        return getDataTable(list);
    }

    /**
     * 获取C2C广告详细信息
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:advert:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(c2cAdvertService.getAdvertDetail(id));
    }

    /**
     * 管理员禁用广告
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:advert:edit')")
    @Log(title = "C2C广告管理", businessType = BusinessType.UPDATE)
    @PutMapping("/disable/{id}")
    public AjaxResult disable(@PathVariable Long id) {
        return toAjax(c2cAdvertService.adminDisableAdvert(id));
    }

    /**
     * 管理员启用广告
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:advert:edit')")
    @Log(title = "C2C广告管理", businessType = BusinessType.UPDATE)
    @PutMapping("/enable/{id}")
    public AjaxResult enable(@PathVariable Long id) {
        return toAjax(c2cAdvertService.adminEnableAdvert(id));
    }
}
