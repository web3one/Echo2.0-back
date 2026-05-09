package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TBinaryTree;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 双轨树 Mapper（金矿邀请体系 Phase 0.4）
 */
public interface TBinaryTreeMapper extends BaseMapper<TBinaryTree> {

    /** 按 user_id 查节点（包含 parent / direction / left_count / right_count） */
    @Select("SELECT * FROM t_binary_tree WHERE user_id = #{userId} LIMIT 1")
    TBinaryTree selectByUserId(@Param("userId") Long userId);

    /** 查 parent 下指定方向的子节点（找空槽时判断用） */
    @Select("SELECT * FROM t_binary_tree WHERE parent_id = #{parentId} AND direction = #{direction} LIMIT 1")
    TBinaryTree findChild(@Param("parentId") Long parentId, @Param("direction") String direction);

    /** 左区计数 +1（在 placement 完成后向上回溯计数链时用） */
    @Update("UPDATE t_binary_tree SET left_count = left_count + 1 WHERE user_id = #{userId}")
    int incrementLeftCount(@Param("userId") Long userId);

    /** 右区计数 +1 */
    @Update("UPDATE t_binary_tree SET right_count = right_count + 1 WHERE user_id = #{userId}")
    int incrementRightCount(@Param("userId") Long userId);
}
