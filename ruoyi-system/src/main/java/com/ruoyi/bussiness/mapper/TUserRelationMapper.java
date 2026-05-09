package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TUserRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 用户推荐关系闭包表 Mapper（金矿邀请体系 Phase 0.3）
 */
public interface TUserRelationMapper extends BaseMapper<TUserRelation> {

    /**
     * 注册场景：把 newUser 挂到 sponsor 下
     * 1. 复制 sponsor 的所有祖先关系（含自身 depth=0），把每行 user_id 改为 newUserId、depth+1，
     *    一次写入即可让 newUser 拥有完整祖先链。
     * 通常配合 insertSelf 一起调用。
     */
    @Insert("INSERT INTO t_user_relation (user_id, parent_id, depth, create_time) " +
            "SELECT #{newUserId}, parent_id, depth + 1, NOW() " +
            "FROM t_user_relation WHERE user_id = #{sponsorId}")
    int copyAncestorsFromSponsor(@Param("newUserId") Long newUserId, @Param("sponsorId") Long sponsorId);

    /**
     * 注册场景：写入新用户自身关系（depth=0）
     */
    @Insert("INSERT INTO t_user_relation (user_id, parent_id, depth, create_time) " +
            "VALUES (#{userId}, #{userId}, 0, NOW())")
    int insertSelf(@Param("userId") Long userId);
}
