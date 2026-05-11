package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * XGT 锁仓计划 Mapper
 *
 * INSERT 时撞 UNIQUE(source_type, source_ref_id) 会抛 DuplicateKeyException，
 * 由调用方 catch 后跳过（幂等命中）。
 */
public interface TXgtLockPlanMapper extends BaseMapper<TXgtLockPlan> {

    @Select("SELECT * FROM t_xgt_lock_plan WHERE source_type = #{sourceType} " +
            "AND source_ref_id = #{sourceRefId} LIMIT 1")
    TXgtLockPlan selectBySource(@Param("sourceType") String sourceType,
                                @Param("sourceRefId") String sourceRefId);

    /** 扫到期但未释放的 plan：xgt_lock_release_job 入口 */
    @Select("SELECT * FROM t_xgt_lock_plan WHERE status = 'locked' AND release_at <= #{now} " +
            "ORDER BY release_at ASC, id ASC LIMIT 1000")
    List<TXgtLockPlan> selectReleasable(@Param("now") Date now);

    /** 条件 UPDATE 占用：status locked→completed，影响 0 行表示已被并发改 */
    @Update("UPDATE t_xgt_lock_plan SET status = 'completed', released_at = #{now} " +
            "WHERE id = #{id} AND status = 'locked'")
    int markCompleted(@Param("id") Long id, @Param("now") Date now);
}
