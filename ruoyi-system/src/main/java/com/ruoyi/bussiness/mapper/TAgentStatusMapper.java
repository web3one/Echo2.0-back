package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TAgentStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户代理等级状态 Mapper（金矿 Phase 1）
 */
public interface TAgentStatusMapper extends BaseMapper<TAgentStatus> {

    /** 按 user_id 查代理状态（用户最多一行） */
    @Select("SELECT * FROM t_agent_status WHERE user_id = #{userId} LIMIT 1")
    TAgentStatus selectByUserId(@Param("userId") Long userId);

    /**
     * 查指定 agent_level + status='active' 的用户列表（B 第二组全网分红用）。
     * 用 ${levelCode} 拼接是因为入参由代码控制（V4/V5 字面量），不存在注入风险。
     */
    @Select("SELECT * FROM t_agent_status WHERE agent_level = #{levelCode} AND status = 'active'")
    List<TAgentStatus> selectActiveByLevel(@Param("levelCode") String levelCode);
}
