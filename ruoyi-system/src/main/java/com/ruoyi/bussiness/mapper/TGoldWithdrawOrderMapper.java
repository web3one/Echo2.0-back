package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TGoldWithdrawOrderMapper extends BaseMapper<TGoldWithdrawOrder> {

    @Select("SELECT * FROM t_gold_withdraw_order WHERE idempotent_key = #{key} LIMIT 1")
    TGoldWithdrawOrder selectByIdempotentKey(@Param("key") String key);
}
