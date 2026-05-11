package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TAgentApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 代理申请审核 Mapper（金矿 Phase 2）
 */
public interface TAgentApplicationMapper extends BaseMapper<TAgentApplication> {

    /** 用户申请历史（最新在前） */
    @Select("SELECT * FROM t_agent_application WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<TAgentApplication> selectByUserId(@Param("userId") Long userId);

    /** 用户是否有 pending 中的申请（同时只允许一条 pending） */
    @Select("SELECT COUNT(*) FROM t_agent_application WHERE user_id = #{userId} AND status = 'pending'")
    int countPendingByUserId(@Param("userId") Long userId);

    /**
     * 统计某用户的直推 V1+ 数（用户决策：min_direct_referral_count 按 PRD 含义为"直推 V1+"）。
     *
     * 直推关系：t_user_relation depth=1（parent_id=inviterId 的所有 user_id）。
     * V1+：t_agent_status.agent_level != 'V0' 且 status='active'。
     * 冻结代理（status='frozen'）不计入业绩资格 → 不计直推 V1+ 数。
     */
    @Select("SELECT COUNT(*) FROM t_user_relation ur "
            + "INNER JOIN t_agent_status ags ON ags.user_id = ur.user_id "
            + "WHERE ur.parent_id = #{inviterId} AND ur.depth = 1 "
            + "  AND ags.agent_level <> 'V0' AND ags.status = 'active'")
    int countDirectReferralAgentV1Plus(@Param("inviterId") Long inviterId);
}
