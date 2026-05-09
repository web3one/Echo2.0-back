package com.ruoyi.web.controller.bussiness;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.bussiness.domain.TC2cOrder;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import com.ruoyi.bussiness.service.IC2cOrderService;

/**
 * C2C订单管理Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/bussiness/c2c/order")
public class C2cOrderController extends BaseController {

    @Autowired
    private IC2cOrderService c2cOrderService;

    /**
     * 查询C2C订单列表（使用VO获取更丰富的数据）
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(TC2cOrder order) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        if (!user.isAdmin()) {
            order.setAdminParentIds(String.valueOf(user.getUserId()));
        }
        startPage();
        List<C2cOrderVO> list = c2cOrderService.selectOrderVOList(order);
        return getDataTable(list);
    }

    /**
     * 获取C2C订单详细信息
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:order:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(c2cOrderService.getOrderDetail(id, null));
    }
}
