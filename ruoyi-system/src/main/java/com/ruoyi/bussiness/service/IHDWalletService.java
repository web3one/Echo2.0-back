package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TUserAddress;
import org.web3j.crypto.Bip32ECKeyPair;

/**
 * HD 钱包派生服务。
 *
 * 用法：
 *   1. 用户首次访问充值页时调 {@link #getOrDeriveAddress(Long, String)}，幂等。
 *   2. Phase 4/5（归集 / 提现）需要私钥时调 {@link #derivePrivateKeyPair(Long, String)}（敏感方法，
 *      只能由后端服务内部调用，绝不暴露 HTTP）。
 *
 * 注意：私钥从不入库，每次根据 userId+chain 现场派生。
 */
public interface IHDWalletService {

    /**
     * 获取（或首次派生）用户在某链上的充值地址。幂等。
     *
     * @param userId 用户 ID
     * @param chain  链：ETH / BSC / BASE / TRX
     * @return TUserAddress 持久化记录
     */
    TUserAddress getOrDeriveAddress(Long userId, String chain);

    /**
     * 派生用户在某链上的密钥对（含私钥，仅内部使用）。
     *
     * @return Bip32ECKeyPair，含私钥
     */
    Bip32ECKeyPair derivePrivateKeyPair(Long userId, String chain);

    /**
     * 派生主钱包密钥对（用 derive_index=0，user_id 始终 ≥1 不冲突）。
     * 用于归集时给子地址派 gas、提现时从主/热钱包打款。
     */
    Bip32ECKeyPair deriveMainKeyPair(String chain);

    /**
     * 主钱包地址（每条链 1 个）。
     */
    String getMainAddress(String chain);
}
