package com.ruoyi.bussiness.wallet;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * HD 钱包配置：从 application-secret.yml 加载主助记词。
 *
 * 配置位置（优先级从高到低）：
 *   1. 环境变量 WALLET_MNEMONIC（生产环境推荐）
 *   2. application-secret.yml 中的 wallet.mnemonic（dev 推荐）
 *   3. application.yml 默认占位值（PLACEHOLDER，调用派生时会抛错）
 *
 * 助记词规范：12 或 24 个 BIP39 英文单词，空格分隔。
 * 注意：助记词一旦泄露，所有派生地址的私钥全部暴露。务必离线生成、加密存储。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "wallet")
public class WalletProperties {

    public static final String PLACEHOLDER = "PLEASE_SET_WALLET_MNEMONIC_VIA_ENV_OR_SECRET_YML";

    /**
     * 主助记词（BIP39，12/24 词）。
     */
    private String mnemonic = PLACEHOLDER;

    /**
     * BIP39 passphrase（可选，加密助记词的二次密码，留空表示不用）。
     */
    private String passphrase = "";

    /**
     * 是否已配置可用的助记词。
     */
    public boolean isMnemonicConfigured() {
        return mnemonic != null
                && !mnemonic.trim().isEmpty()
                && !PLACEHOLDER.equals(mnemonic.trim());
    }

    @PostConstruct
    public void validateOnStart() {
        if (!isMnemonicConfigured()) {
            log.warn("=========================================================");
            log.warn(" wallet.mnemonic 未配置，HD 钱包派生功能将不可用");
            log.warn(" 请在 application-secret.yml 设置 wallet.mnemonic");
            log.warn(" 或设置环境变量 WALLET_MNEMONIC");
            log.warn("=========================================================");
            return;
        }
        int wordCount = mnemonic.trim().split("\\s+").length;
        if (wordCount != 12 && wordCount != 24) {
            log.warn("wallet.mnemonic 词数异常: {}（标准为 12 或 24）", wordCount);
        } else {
            log.info("HD 钱包助记词已加载（{} 词）", wordCount);
        }
    }
}
