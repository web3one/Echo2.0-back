package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TGoldWallet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 金矿子钱包 Mapper（user_id UNIQUE）。
 */
public interface TGoldWalletMapper extends BaseMapper<TGoldWallet> {

    @Select("SELECT * FROM t_gold_wallet WHERE user_id = #{userId} LIMIT 1")
    TGoldWallet selectByUserId(@Param("userId") Long userId);

    /** 加 FOR UPDATE 行级锁，并发入账/提现互斥。 */
    @Select("SELECT * FROM t_gold_wallet WHERE user_id = #{userId} LIMIT 1 FOR UPDATE")
    TGoldWallet selectByUserIdForUpdate(@Param("userId") Long userId);
}
