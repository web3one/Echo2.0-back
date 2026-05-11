package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TFounderSeat;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 创世席位 Mapper（金矿 Phase 2）
 */
public interface TFounderSeatMapper extends BaseMapper<TFounderSeat> {

    /**
     * 行级锁取第一个 available 席位（按 seat_no ASC）。
     * 必须在 @Transactional 内调用，否则 FOR UPDATE 无效。
     */
    @Select("SELECT * FROM t_founder_seat WHERE status = 'available' "
            + "ORDER BY seat_no ASC LIMIT 1 FOR UPDATE")
    TFounderSeat selectFirstAvailableForUpdate();

    /** 统计某状态席位数（available 数用作 H5 remaining 显示） */
    @Select("SELECT COUNT(*) FROM t_founder_seat WHERE status = #{status}")
    int countByStatus(@Param("status") String status);

    /** 查我的席位（一人一席 UK） */
    @Select("SELECT * FROM t_founder_seat WHERE owner_user_id = #{userId} LIMIT 1")
    TFounderSeat selectByOwnerUserId(@Param("userId") Long userId);

    /** 查所有 owned 状态席位（创世分红 cron 用，冻结 / available 都不参与分红） */
    @Select("SELECT * FROM t_founder_seat WHERE status = 'owned' AND owner_user_id IS NOT NULL "
            + "ORDER BY seat_no ASC")
    java.util.List<TFounderSeat> selectAllOwned();
}
