package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TTradfiOrder;

import java.util.List;

/**
 * TradFi 交易订单 Service
 */
public interface ITTradfiOrderService extends IService<TTradfiOrder> {

    TTradfiOrder selectTTradfiOrderById(Long id);

    List<TTradfiOrder> selectTTradfiOrderList(TTradfiOrder tTradfiOrder);

    int insertTTradfiOrder(TTradfiOrder tTradfiOrder);

    int updateTTradfiOrder(TTradfiOrder tTradfiOrder);

    int deleteTTradfiOrderByIds(Long[] ids);

    int deleteTTradfiOrderById(Long id);

    String submitTradfiOrder(TAppUser user, TTradfiOrder tTradfiOrder);

    int canCelOrder(TTradfiOrder tradfiOrder);

    List<TTradfiOrder> selectOrderList(TTradfiOrder tTradfiOrder);
}
