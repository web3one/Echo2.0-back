package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TAgentStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户代理等级状态 Mapper（金矿 Phase 1）
 */
public interface TAgentStatusMapper extends BaseMapper<TAgentStatus> {

    /** 按 user_id 查代理状态（用户最多一行） */
    @Select("SELECT * FROM t_agent_status WHERE user_id = #{userId} LIMIT 1")
    TAgentStatus selectByUserId(@Param("userId") Long userId);
}
