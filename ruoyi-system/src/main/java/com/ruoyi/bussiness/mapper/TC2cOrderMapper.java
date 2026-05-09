package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cOrder;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TC2cOrderMapper extends BaseMapper<TC2cOrder> {

    List<TC2cOrder> selectOrderList(TC2cOrder order);

    List<C2cOrderVO> selectOrderVOList(TC2cOrder order);

    /** 查询超时未付款的订单 */
    List<TC2cOrder> selectTimeoutOrders();

    /** 查询用户当日取消次数 */
    int countDailyCancelByUser(@Param("userId") Long userId, @Param("role") String role);

    /** 查询用户当前进行中的订单数 */
    int countActiveOrdersByUser(@Param("userId") Long userId);
}
