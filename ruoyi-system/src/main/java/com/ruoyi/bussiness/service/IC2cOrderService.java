package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cOrder;
import com.ruoyi.bussiness.domain.dto.C2cOrderCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cOrderCreateResult;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import java.util.List;

public interface IC2cOrderService extends IService<TC2cOrder> {
    C2cOrderCreateResult createOrder(Long userId, C2cOrderCreateDTO dto);
    String markPaid(Long orderId, Long userId);
    String confirmRelease(Long orderId, Long userId, String fundPassword);
    String cancelOrder(Long orderId, Long userId);
    void handleTimeout(TC2cOrder order);
    C2cOrderVO getOrderDetail(Long orderId, Long userId);
    List<TC2cOrder> listMyOrders(Long userId, List<Integer> statusList);
    List<TC2cOrder> selectOrderList(TC2cOrder order);
    List<C2cOrderVO> selectOrderVOList(TC2cOrder order);
    List<TC2cOrder> selectTimeoutOrders();
}
