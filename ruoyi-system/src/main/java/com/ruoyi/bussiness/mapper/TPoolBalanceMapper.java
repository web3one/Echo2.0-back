package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TPoolBalance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TPoolBalanceMapper extends BaseMapper<TPoolBalance> {

    default TPoolBalance selectBySymbolAndChain(String symbol, String chain) {
        return selectOne(new LambdaQueryWrapper<TPoolBalance>()
                .eq(TPoolBalance::getSymbol, symbol)
                .eq(TPoolBalance::getChain, chain));
    }
}
