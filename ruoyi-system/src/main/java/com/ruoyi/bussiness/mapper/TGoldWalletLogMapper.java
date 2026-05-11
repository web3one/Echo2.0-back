package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TGoldWalletLogMapper extends BaseMapper<TGoldWalletLog> {

    @Select("SELECT * FROM t_gold_wallet_log WHERE idempotent_key = #{key} LIMIT 1")
    TGoldWalletLog selectByIdempotentKey(@Param("key") String key);
}
