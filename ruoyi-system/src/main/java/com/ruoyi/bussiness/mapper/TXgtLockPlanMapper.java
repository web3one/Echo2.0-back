package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
