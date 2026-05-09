package com.ruoyi.web.controller.bussiness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.bussiness.domain.TC2cOrder;
import com.ruoyi.bussiness.domain.dto.C2cOrderCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cOrderCreateResult;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import com.ruoyi.bussiness.service.IC2cOrderService;
import com.ruoyi.web.controller.common.ApiBaseController;

/**
 * C2C订单 - 用户端Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/c2c/order")
public class ApiC2cOrderController extends ApiBaseController {

    @Resource
    private IC2cOrderService c2cOrderService;

    /**
     * 创建C2C订单
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody C2cOrderCreateDTO dto) {
        C2cOrderCreateResult result = c2cOrderService.createOrder(getStpUserId(), dto);
        return result.getError() == null ? success(result) : error(result.getError());
    }

    /**
     * 标记已付款
     */
    @PostMapping("/markPaid")
    public AjaxResult markPaid(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        String error = c2cOrderService.markPaid(orderId, getStpUserId());
        return error == null ? success() : error(error);
    }

    /**
     * 确认放行
     */
    @PostMapping("/confirmRelease")
    public AjaxResult confirmRelease(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        String fundPassword = params.get("fundPassword").toString();
        String error = c2cOrderService.confirmRelease(orderId, getStpUserId(), fundPassword);
        return error == null ? success() : error(error);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel")
    public AjaxResult cancel(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        String error = c2cOrderService.cancelOrder(orderId, getStpUserId());
        return error == null ? success() : error(error);
    }

    /**
     * 查询我的订单列表（可按状态筛选）
     */
    @PostMapping("/myList")
    public TableDataInfo myList(@RequestBody(required = false) Map<String, Object> params) {
        List<Integer> statusList = parseStatusList(params);
        startPage();
        List<TC2cOrder> list = c2cOrderService.listMyOrders(getStpUserId(), statusList);
        return getDataTable(list);
    }

    /**
     * 获取订单详情
     */
    @PostMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        C2cOrderVO vo = c2cOrderService.getOrderDetail(id, getStpUserId());
        if (vo == null) {
            return error("订单不存在");
        }
        return success(vo);
    }

    private List<Integer> parseStatusList(Map<String, Object> params) {
        List<Integer> statusList = new ArrayList<>();
        if (params == null) {
            return statusList;
        }
        Object raw = params.get("statusList");
        if (raw == null) {
            raw = params.get("status");
        }
        if (raw == null || raw.toString().trim().isEmpty()) {
            return statusList;
        }
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null && !item.toString().trim().isEmpty()) {
                    statusList.add(Integer.valueOf(item.toString()));
                }
            }
            return statusList;
        }
        for (String item : raw.toString().split(",")) {
            if (item != null && !item.trim().isEmpty()) {
                statusList.add(Integer.valueOf(item.trim()));
            }
        }
        return statusList;
    }
}
