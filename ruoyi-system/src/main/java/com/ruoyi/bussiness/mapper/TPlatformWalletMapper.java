package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TPlatformWallet;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TPlatformWalletMapper extends BaseMapper<TPlatformWallet> {

    default TPlatformWallet selectMain(String chain) {
        return selectOne(new LambdaQueryWrapper<TPlatformWallet>()
                .eq(TPlatformWallet::getChain, chain)
                .eq(TPlatformWallet::getWalletType, "MAIN")
                .eq(TPlatformWallet::getEnabled, 1));
    }

    default TPlatformWallet selectHot(String chain) {
        return selectOne(new LambdaQueryWrapper<TPlatformWallet>()
                .eq(TPlatformWallet::getChain, chain)
                .eq(TPlatformWallet::getWalletType, "HOT")
                .eq(TPlatformWallet::getEnabled, 1));
    }

    default List<TPlatformWallet> selectByChain(String chain) {
        return selectList(new LambdaQueryWrapper<TPlatformWallet>()
                .eq(TPlatformWallet::getChain, chain));
    }
}
