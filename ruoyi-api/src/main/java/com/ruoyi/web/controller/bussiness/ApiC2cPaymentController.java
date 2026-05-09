package com.ruoyi.web.controller.bussiness;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;
import com.ruoyi.bussiness.service.IC2cMerchantService;
import com.ruoyi.bussiness.service.IC2cMerchantPaymentService;
import com.ruoyi.web.controller.common.ApiBaseController;

/**
 * C2C收付款方式 - 用户端Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/c2c/payment")
public class ApiC2cPaymentController extends ApiBaseController {

    @Resource
    private IC2cMerchantPaymentService c2cMerchantPaymentService;

    @Resource
    private IC2cMerchantService c2cMerchantService;

    /**
     * 添加收付款方式
     */
    @PostMapping("/add")
    public AjaxResult add(@RequestBody TC2cMerchantPayment payment) {
        Long userId = getStpUserId();
        TC2cMerchant merchant = c2cMerchantService.getMerchantByUserId(userId);
        payment.setMerchantId(merchant != null ? merchant.getId() : 0L);
        payment.setUserId(userId);
        return toAjax(c2cMerchantPaymentService.addPayment(payment));
    }

    /**
     * 修改收付款方式
     */
    @PostMapping("/update")
    public AjaxResult update(@RequestBody TC2cMerchantPayment payment) {
        Long userId = getStpUserId();
        payment.setUserId(userId);
        return toAjax(c2cMerchantPaymentService.updatePayment(payment));
    }

    /**
     * 删除收付款方式
     */
    @PostMapping("/delete/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        Long userId = getStpUserId();
        return toAjax(c2cMerchantPaymentService.deletePayment(id, userId));
    }

    /**
     * 查询我的收付款方式列表
     */
    @PostMapping("/list")
    public TableDataInfo list() {
        Long userId = getStpUserId();
        startPage();
        List<TC2cMerchantPayment> list = c2cMerchantPaymentService.listByUser(userId);
        return getDataTable(list);
    }
}
