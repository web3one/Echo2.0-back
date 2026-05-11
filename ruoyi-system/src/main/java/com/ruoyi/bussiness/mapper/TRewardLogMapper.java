package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TRewardLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 金矿奖励流水 Mapper
 *
 * INSERT 时撞 idempotent_key UK 会抛 DuplicateKeyException，由调用方在
 * REQUIRES_NEW 子事务内 catch 后跳过（幂等命中），不影响整体结算。
 */
public interface TRewardLogMapper extends BaseMapper<TRewardLog> {

    @Select("SELECT * FROM t_reward_log WHERE idempotent_key = #{key} LIMIT 1")
    TRewardLog selectByIdempotentKey(@Param("key") String idempotentKey);
}
