package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cAppealMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TC2cAppealMessageMapper extends BaseMapper<TC2cAppealMessage> {

    List<TC2cAppealMessage> selectByAppealId(@Param("appealId") Long appealId);
}
