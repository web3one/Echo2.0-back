package com.ruoyi.bussiness.wallet;

import org.tron.trident.core.key.KeyPair;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.utils.Numeric;

/**
 * TRX 地址生成（Tron 主网，base58check + 0x41 前缀）。
 *
 * 实现方式：Bip32 派生出的私钥（hex）→ trident KeyPair → toBase58CheckAddress
 */
public final class TrxAddressUtil {

    private TrxAddressUtil() {}

    /**
     * 从派生出的密钥对生成 TRX 地址（T... 开头）。
     */
    public static String addressFromKey(Bip32ECKeyPair keyPair) {
        // 私钥 hex 32 字节，无 0x 前缀
        String privKeyHex = Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
        KeyPair trxKeyPair = new KeyPair(privKeyHex);
        return trxKeyPair.toBase58CheckAddress();
    }

    /**
     * 派生出的密钥对的私钥 hex（无 0x 前缀，64 字符）。
     * 后续 Phase 4/5（归集、提现）转账时需要。
     */
    public static String privateKeyHex(Bip32ECKeyPair keyPair) {
        return Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
    }
}
