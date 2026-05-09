package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TUserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户多链充值地址 Mapper
 */
@Mapper
public interface TUserAddressMapper extends BaseMapper<TUserAddress> {

    default TUserAddress selectByUserAndChain(Long userId, String chain) {
        return selectOne(new LambdaQueryWrapper<TUserAddress>()
                .eq(TUserAddress::getUserId, userId)
                .eq(TUserAddress::getChain, chain));
    }

    default TUserAddress selectByChainAndAddress(String chain, String address) {
        if (!"TRX".equalsIgnoreCase(chain)) {
            return selectOne(new LambdaQueryWrapper<TUserAddress>()
                    .eq(TUserAddress::getChain, chain)
                    .apply("LOWER(address) = LOWER({0})", address));
        }
        return selectOne(new LambdaQueryWrapper<TUserAddress>()
                .eq(TUserAddress::getChain, chain)
                .eq(TUserAddress::getAddress, address));
    }
}
