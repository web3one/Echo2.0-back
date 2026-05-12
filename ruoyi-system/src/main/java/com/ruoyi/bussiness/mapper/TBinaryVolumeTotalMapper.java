package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TBinaryVolumeTotal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 双轨累计业绩 Mapper
 */
public interface TBinaryVolumeTotalMapper extends BaseMapper<TBinaryVolumeTotal> {

    @Insert("INSERT INTO t_binary_volume_total (user_id, left_volume_total, right_volume_total, direct_referral_count, update_time) "
            + "VALUES (#{userId}, #{amount}, 0, 0, NOW()) "
            + "ON DUPLICATE KEY UPDATE left_volume_total = left_volume_total + #{amount}, update_time = NOW()")
    int upsertLeftTotal(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Insert("INSERT INTO t_binary_volume_total (user_id, left_volume_total, right_volume_total, direct_referral_count, update_time) "
            + "VALUES (#{userId}, 0, #{amount}, 0, NOW()) "
            + "ON DUPLICATE KEY UPDATE right_volume_total = right_volume_total + #{amount}, update_time = NOW()")
    int upsertRightTotal(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Select("SELECT id, user_id, left_volume_total, right_volume_total, direct_referral_count, update_time "
            + "FROM t_binary_volume_total WHERE user_id = #{userId} LIMIT 1")
    TBinaryVolumeTotal selectByUserId(@Param("userId") Long userId);
}
