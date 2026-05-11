package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TEcosystemCreditBalance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ecosystem_credit 余额 Mapper（user_id UNIQUE）
 */
public interface TEcosystemCreditBalanceMapper extends BaseMapper<TEcosystemCreditBalance> {

    @Select("SELECT * FROM t_ecosystem_credit_balance WHERE user_id = #{userId} LIMIT 1")
    TEcosystemCreditBalance selectByUserId(@Param("userId") Long userId);
}
