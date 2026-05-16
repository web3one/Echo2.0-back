package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TTradfiOrder;
import com.ruoyi.bussiness.service.ITAppUserDetailService;
import com.ruoyi.bussiness.service.ITTradfiOrderService;
import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.web.controller.common.ApiBaseController;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * H5 TradFi 交易订单 Controller
 */
@RestController
@RequestMapping("/api/tradfi/order")
public class TTradfiOrderController extends ApiBaseController {

    @Resource
    private ITTradfiOrderService tTradfiOrderService;
    @Resource
    private ITAppUserDetailService tAppUserDetailService;

    @PostMapping("/orderList")
    public TableDataInfo orderList(TTradfiOrder tTradfiOrder) {
        tTradfiOrder.setUserId(getAppUser().getUserId());
        startPage();
        List<TTradfiOrder> list = tTradfiOrderService.selectOrderList(tTradfiOrder);
        addTimeParams(list);
        return getDataTable(list);
    }

    @PostMapping("/cancelOrder")
    public AjaxResult cancelOrder(Long id) {
        TTradfiOrder tradfiOrder = tTradfiOrderService.getOne(new LambdaQueryWrapper<TTradfiOrder>()
                .eq(TTradfiOrder::getUserId, getStpUserId())
                .eq(TTradfiOrder::getId, id)
                .eq(TTradfiOrder::getStatus, 0));
        if (tradfiOrder != null) {
            tTradfiOrderService.canCelOrder(tradfiOrder);
        } else {
            return AjaxResult.error("status is error");
        }
        return AjaxResult.success();
    }

    @RepeatSubmit(interval = 5000, message = "repeat.submit.too_frequent")
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody TTradfiOrder tTradfiOrder) {
        TAppUser user = getAppUser();
        if (user != null) {
            TAppUserDetail userDetail = tAppUserDetailService.getOne(
                    new LambdaQueryWrapper<TAppUserDetail>().eq(TAppUserDetail::getUserId, user.getUserId()));
            if (userDetail != null && Objects.equals(userDetail.getTradeFlag(), 1)) {
                if (userDetail.getTradeMessage() == null || userDetail.getTradeMessage().equals("")) {
                    return AjaxResult.error(MessageUtils.message("user.push.message"));
                }
                return AjaxResult.error(userDetail.getTradeMessage());
            }
        } else {
            return AjaxResult.error(MessageUtils.message("user.notfound"));
        }
        String result = tTradfiOrderService.submitTradfiOrder(user, tTradfiOrder);
        return "success".equals(result) ? AjaxResult.success() : AjaxResult.error(result);
    }

    private void addTimeParams(List<TTradfiOrder> list) {
        if (CollectionUtils.isEmpty(list)) return;
        list.forEach(t -> {
            Map<String, Object> params = new HashMap<>();
            params.put("dealTime", Objects.nonNull(t.getDealTime()) ? t.getDealTime().getTime() : 0L);
            params.put("delegateTime", Objects.nonNull(t.getDelegateTime()) ? t.getDelegateTime().getTime() : 0L);
            t.setParams(params);
        });
    }
}
