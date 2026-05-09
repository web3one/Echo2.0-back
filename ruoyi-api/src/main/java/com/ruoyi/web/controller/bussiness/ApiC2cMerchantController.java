package com.ruoyi.web.controller.bussiness;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.dto.C2cMerchantApplyDTO;
import com.ruoyi.bussiness.service.IC2cMerchantService;
import com.ruoyi.web.controller.common.ApiBaseController;

/**
 * C2C商家 - 用户端Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/c2c/merchant")
public class ApiC2cMerchantController extends ApiBaseController {

    @Resource
    private IC2cMerchantService c2cMerchantService;

    /**
     * 申请成为C2C商家
     */
    @PostMapping("/apply")
    public AjaxResult apply(@RequestBody C2cMerchantApplyDTO dto) {
        String error = c2cMerchantService.applyMerchant(getStpUserId(), dto);
        return error == null ? success() : error(error);
    }

    /**
     * 获取当前用户的商家信息
     */
    @PostMapping("/myProfile")
    public AjaxResult myProfile() {
        TC2cMerchant merchant = c2cMerchantService.getMerchantByUserId(getStpUserId());
        return success(merchant);
    }

    /**
     * 获取指定用户的商家公开信息
     */
    @PostMapping("/info/{userId}")
    public AjaxResult info(@PathVariable Long userId) {
        TC2cMerchant merchant = c2cMerchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return error("该用户不是商家");
        }
        // 只返回公开信息，清除敏感字段
        merchant.setAdminParentIds(null);
        return success(merchant);
    }
}
