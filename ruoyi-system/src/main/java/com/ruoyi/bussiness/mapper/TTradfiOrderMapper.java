package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TTradfiOrder;

import java.util.List;

/**
 * TradFi 交易订单 Mapper
 */
public interface TTradfiOrderMapper extends BaseMapper<TTradfiOrder> {

    TTradfiOrder selectTTradfiOrderById(Long id);

    List<TTradfiOrder> selectTTradfiOrderList(TTradfiOrder tTradfiOrder);

    int insertTTradfiOrder(TTradfiOrder tTradfiOrder);

    int updateTTradfiOrder(TTradfiOrder tTradfiOrder);

    int deleteTTradfiOrderById(Long id);

    int deleteTTradfiOrderByIds(Long[] ids);

    List<TTradfiOrder> selectOrderList(TTradfiOrder tTradfiOrder);
}
