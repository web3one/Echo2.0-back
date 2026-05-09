package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TC2cConfigMapper extends BaseMapper<TC2cConfig> {

    TC2cConfig selectByKey(@Param("configKey") String configKey);
}
