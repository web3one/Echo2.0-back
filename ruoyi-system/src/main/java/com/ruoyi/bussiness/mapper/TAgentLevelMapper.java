package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TAgentLevel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 代理等级配置 Mapper（金矿 Phase 1）
 */
public interface TAgentLevelMapper extends BaseMapper<TAgentLevel> {

    /** 按 level_code 查配置 */
    @Select("SELECT * FROM t_agent_level WHERE level_code = #{levelCode} LIMIT 1")
    TAgentLevel selectByLevelCode(@Param("levelCode") String levelCode);
}
