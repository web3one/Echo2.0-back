package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TNodeLevel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 矿机等级配置 Mapper
 */
public interface TNodeLevelMapper extends BaseMapper<TNodeLevel> {

    @Select("SELECT * FROM t_node_level WHERE level_code = #{levelCode} LIMIT 1")
    TNodeLevel selectByLevelCode(@Param("levelCode") String levelCode);
}
