package com.ruoyi.bussiness.wallet;

import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Keys;

/**
 * EVM 链地址生成（ETH / BSC / Base 同一算法，地址完全相同）。
 */
public final class EvmAddressUtil {

    private EvmAddressUtil() {}

    /**
     * 从派生出的密钥对生成 EVM 地址（带 EIP-55 大小写校验和）。
     *
     * @param keyPair Bip32ECKeyPair
     * @return 0x 开头、含校验和的 42 字符地址
     */
    public static String addressFromKey(Bip32ECKeyPair keyPair) {
        String addressNoChecksum = "0x" + Keys.getAddress(keyPair);
        return Keys.toChecksumAddress(addressNoChecksum);
    }
}
