package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TXgtBalance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * XGT 余额 Mapper（user_id UNIQUE）
 */
public interface TXgtBalanceMapper extends BaseMapper<TXgtBalance> {

    @Select("SELECT * FROM t_xgt_balance WHERE user_id = #{userId} LIMIT 1")
    TXgtBalance selectByUserId(@Param("userId") Long userId);
}
