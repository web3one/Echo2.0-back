package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TUserAddress;
import com.ruoyi.bussiness.mapper.TUserAddressMapper;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.bussiness.wallet.EvmAddressUtil;
import com.ruoyi.bussiness.wallet.HDKeyDerivation;
import com.ruoyi.bussiness.wallet.TrxAddressUtil;
import com.ruoyi.bussiness.wallet.WalletProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Bip32ECKeyPair;

import java.util.Date;

@Slf4j
@Service
public class HDWalletServiceImpl implements IHDWalletService {

    @Autowired
    private WalletProperties walletProperties;

    @Autowired
    private TUserAddressMapper userAddressMapper;

    @Override
    public TUserAddress getOrDeriveAddress(Long userId, String chain) {
        if (userId == null || chain == null) {
            throw new IllegalArgumentException("userId / chain must not be null");
        }
        String chainUpper = chain.toUpperCase();

        // 1. 已存在直接返回
        TUserAddress existing = userAddressMapper.selectByUserAndChain(userId, chainUpper);
        if (existing != null) {
            return existing;
        }

        // 2. 派生新地址
        if (!walletProperties.isMnemonicConfigured()) {
            throw new IllegalStateException("wallet.mnemonic 未配置，无法派生地址");
        }

        Bip32ECKeyPair keyPair = derivePrivateKeyPair(userId, chainUpper);
        int coinType = coinTypeOf(chainUpper);
        String address;
        if (coinType == HDKeyDerivation.COIN_TYPE_TRX) {
            address = TrxAddressUtil.addressFromKey(keyPair);
        } else {
            address = EvmAddressUtil.addressFromKey(keyPair);
        }

        TUserAddress entity = new TUserAddress();
        entity.setUserId(userId);
        entity.setChain(chainUpper);
        entity.setAddress(address);
        entity.setDerivePath(HDKeyDerivation.pathString(coinType, userId));
        entity.setDeriveIndex(userId);
        entity.setCreateTime(new Date());

        try {
            userAddressMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发场景：另一个请求刚写入，再查一次直接返回
            log.warn("并发派生 user={} chain={}，重新查询返回", userId, chainUpper);
            return userAddressMapper.selectByUserAndChain(userId, chainUpper);
        }

        log.info("HD 派生新地址 user={} chain={} address={} path={}",
                userId, chainUpper, address, entity.getDerivePath());
        return entity;
    }

    @Override
    public Bip32ECKeyPair derivePrivateKeyPair(Long userId, String chain) {
        if (!walletProperties.isMnemonicConfigured()) {
            throw new IllegalStateException("wallet.mnemonic 未配置");
        }
        int coinType = coinTypeOf(chain.toUpperCase());
        return HDKeyDerivation.derive(
                walletProperties.getMnemonic(),
                walletProperties.getPassphrase(),
                coinType,
                userId);
    }

    @Override
    public Bip32ECKeyPair deriveMainKeyPair(String chain) {
        // 主钱包用 index=0；用户 user_id 从 1 起，不冲突
        return derivePrivateKeyPair(0L, chain);
    }

    @Override
    public String getMainAddress(String chain) {
        Bip32ECKeyPair keyPair = deriveMainKeyPair(chain);
        int coinType = coinTypeOf(chain.toUpperCase());
        if (coinType == HDKeyDerivation.COIN_TYPE_TRX) {
            return TrxAddressUtil.addressFromKey(keyPair);
        }
        return EvmAddressUtil.addressFromKey(keyPair);
    }

    /**
     * EVM 三链统一用 coin_type=60，TRX 用 195。
     */
    private int coinTypeOf(String chain) {
        switch (chain) {
            case "ETH":
            case "BSC":
            case "BASE":
                return HDKeyDerivation.COIN_TYPE_EVM;
            case "TRX":
                return HDKeyDerivation.COIN_TYPE_TRX;
            default:
                throw new IllegalArgumentException("不支持的链: " + chain);
        }
    }
}
