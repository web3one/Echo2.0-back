package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface TDailyFeeSummaryMapper extends BaseMapper<TDailyFeeSummary> {

    @Select("SELECT * FROM t_daily_fee_summary WHERE biz_date = #{bizDate} LIMIT 1")
    TDailyFeeSummary selectByBizDate(@Param("bizDate") LocalDate bizDate);
}
