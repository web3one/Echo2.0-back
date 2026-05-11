package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TFounderPurchaseLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 创世席位购买流水 Mapper（金矿 Phase 2）
 */
public interface TFounderPurchaseLogMapper extends BaseMapper<TFounderPurchaseLog> {

    /** 按幂等键查（防重复扣款） */
    @Select("SELECT * FROM t_founder_purchase_log WHERE idempotent_key = #{key} LIMIT 1")
    TFounderPurchaseLog selectByIdempotentKey(@Param("key") String key);
}
