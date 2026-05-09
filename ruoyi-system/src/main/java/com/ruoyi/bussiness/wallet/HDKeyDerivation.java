package com.ruoyi.bussiness.wallet;

import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.MnemonicUtils;

/**
 * BIP44 HD 密钥派生工具。
 *
 * 派生路径：
 *   EVM 链（ETH/BSC/Base）：m/44'/60'/0'/0/{userId}
 *   TRX 链：               m/44'/195'/0'/0/{userId}
 *
 * 注：纯静态工具，无 Spring 依赖，可独立单测。
 */
public final class HDKeyDerivation {

    /** EVM 系列链的 BIP44 coin_type */
    public static final int COIN_TYPE_EVM = 60;

    /** TRX 链的 BIP44 coin_type */
    public static final int COIN_TYPE_TRX = 195;

    private HDKeyDerivation() {}

    /**
     * 用助记词 + 派生路径派生密钥对。
     *
     * @param mnemonic   BIP39 助记词
     * @param passphrase BIP39 passphrase（可空）
     * @param coinType   BIP44 coin_type（60=EVM, 195=TRX）
     * @param userId     用户 ID（作为派生 index）
     * @return 派生出的 Bip32ECKeyPair（含公私钥）
     */
    public static Bip32ECKeyPair derive(String mnemonic, String passphrase, int coinType, long userId) {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            throw new IllegalArgumentException("mnemonic must not be empty");
        }
        if (userId < 0 || userId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("userId out of int range: " + userId);
        }

        byte[] seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase);
        Bip32ECKeyPair masterKey = Bip32ECKeyPair.generateKeyPair(seed);

        int[] path = new int[] {
                44 | Bip32ECKeyPair.HARDENED_BIT,
                coinType | Bip32ECKeyPair.HARDENED_BIT,
                0 | Bip32ECKeyPair.HARDENED_BIT,
                0,
                (int) userId
        };
        return Bip32ECKeyPair.deriveKeyPair(masterKey, path);
    }

    /**
     * 返回派生路径字符串，便于审计/排查。
     */
    public static String pathString(int coinType, long userId) {
        return String.format("m/44'/%d'/0'/0/%d", coinType, userId);
    }
}
