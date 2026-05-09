package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cOrderAppeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TC2cOrderAppealMapper extends BaseMapper<TC2cOrderAppeal> {

    List<TC2cOrderAppeal> selectAppealList(TC2cOrderAppeal appeal);

    TC2cOrderAppeal selectByOrderId(@Param("orderId") Long orderId);
}
