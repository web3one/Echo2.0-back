package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TBinaryVolumeDaily;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 双轨当日业绩 Mapper
 */
public interface TBinaryVolumeDailyMapper extends BaseMapper<TBinaryVolumeDaily> {

    /**
     * 在 (user_id, biz_date) 上累加左区业绩（行不存在则新建）。
     * 用 ON DUPLICATE KEY UPDATE 实现并发安全的 upsert。
     */
    @Update("INSERT INTO t_binary_volume_daily (user_id, biz_date, left_volume, right_volume, create_time, update_time) "
            + "VALUES (#{userId}, #{bizDate}, #{amount}, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE left_volume = left_volume + #{amount}, update_time = NOW()")
    int upsertLeftVolume(@Param("userId") Long userId,
                         @Param("bizDate") Date bizDate,
                         @Param("amount") BigDecimal amount);

    @Update("INSERT INTO t_binary_volume_daily (user_id, biz_date, left_volume, right_volume, create_time, update_time) "
            + "VALUES (#{userId}, #{bizDate}, 0, #{amount}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE right_volume = right_volume + #{amount}, update_time = NOW()")
    int upsertRightVolume(@Param("userId") Long userId,
                          @Param("bizDate") Date bizDate,
                          @Param("amount") BigDecimal amount);
}
