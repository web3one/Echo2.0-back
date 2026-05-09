package com.ruoyi.chain.aggregation;

import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.common.trc.Trc20Contract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.contract.Contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * TRX 链交易执行器（基于 tron-trident 0.7.0）。
 *
 * 注意：
 *   · TRX 大额转账（PM 要求）应走多签，多签需要 2-3 个授权方链下协同，
 *     当前实现是单签（用主钱包私钥）。大额场景由运营走 TronLink/TronWeb 多签 UI。
 *   · 本实现只用于：日常归集小额场景（< 多签阈值）
 */
@Slf4j
@Component
public class TrxChainExecutor implements ChainExecutor {

    /** TRC20 transfer 默认 fee_limit（单位 sun） */
    private static final long FEE_LIMIT = 100_000_000L;

    @Override
    public boolean supports(String chain) {
        return "TRX".equalsIgnoreCase(chain);
    }

    private ApiWrapper buildWrapper(String privateKeyHex, TChainConfig config) {
        // 用 ofMainnet，trident 内部连 grpc.trongrid.io。如果 config 配的是其他 RPC，
        // 这里只是签名 + 广播，实际走 trident 默认节点。生产环境如有需要可改造为 ofShasta/自定义节点
        // 这里用一个无意义的 api key（trident 0.7 在某些场景需要，没有时也能跑只读）
        return ApiWrapper.ofMainnet(privateKeyHex, "00000000-0000-0000-0000-000000000000");
    }

    @Override
    public BigDecimal queryUsdtBalance(String address, TChainConfig config) {
        // 用主钱包密钥构造 wrapper（任意有效私钥都行，只为读取）
        ApiWrapper wrapper = null;
        try {
            // 占位私钥（trident 需要，但此调用为只读不会发交易）
            wrapper = buildWrapper("0000000000000000000000000000000000000000000000000000000000000001", config);
            Contract contract = wrapper.getContract(config.getUsdtContract());
            Trc20Contract token = new Trc20Contract(contract, address, wrapper);
            BigInteger raw = token.balanceOf(address);
            return new BigDecimal(raw).movePointLeft(config.getUsdtDecimals());
        } catch (Exception e) {
            log.warn("[TRX] queryUsdtBalance({}) 异常: {}", address, e.getMessage());
            return BigDecimal.ZERO;
        } finally {
            if (wrapper != null) wrapper.close();
        }
    }

    @Override
    public BigDecimal queryNativeBalance(String address, TChainConfig config) {
        ApiWrapper wrapper = null;
        try {
            wrapper = buildWrapper("0000000000000000000000000000000000000000000000000000000000000001", config);
            long sun = wrapper.getAccount(address).getBalance();
            return new BigDecimal(sun).divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_DOWN);
        } catch (Exception e) {
            log.warn("[TRX] queryNativeBalance({}) 异常: {}", address, e.getMessage());
            return BigDecimal.ZERO;
        } finally {
            if (wrapper != null) wrapper.close();
        }
    }

    @Override
    public String sendNative(String privateKeyHex, String fromAddress, String toAddress,
                              BigDecimal amount, TChainConfig config) {
        ApiWrapper wrapper = null;
        try {
            wrapper = buildWrapper(privateKeyHex, config);
            long sun = amount.multiply(BigDecimal.valueOf(1_000_000)).longValue();
            org.tron.trident.proto.Response.TransactionExtention txExt =
                    wrapper.transfer(fromAddress, toAddress, sun);
            org.tron.trident.proto.Chain.Transaction signed = wrapper.signTransaction(txExt);
            String hash = wrapper.broadcastTransaction(signed);
            log.info("[TRX] sendNative {}->{} {} TRX, hash={}", fromAddress, toAddress, amount, hash);
            return hash;
        } catch (Exception e) {
            log.error("[TRX] sendNative {}->{} 失败: {}", fromAddress, toAddress, e.getMessage());
            return null;
        } finally {
            if (wrapper != null) wrapper.close();
        }
    }

    @Override
    public String sendUsdt(String privateKeyHex, String fromAddress, String toAddress,
                            BigDecimal amount, TChainConfig config) {
        ApiWrapper wrapper = null;
        try {
            wrapper = buildWrapper(privateKeyHex, config);
            Contract contract = wrapper.getContract(config.getUsdtContract());
            Trc20Contract token = new Trc20Contract(contract, fromAddress, wrapper);
            long raw = amount.movePointRight(config.getUsdtDecimals()).longValue();
            String hash = token.transfer(toAddress, raw, "aggregation", FEE_LIMIT);
            log.info("[TRX] sendUsdt {}->{} {} USDT, hash={}", fromAddress, toAddress, amount, hash);
            return hash;
        } catch (Exception e) {
            log.error("[TRX] sendUsdt {}->{} 失败: {}", fromAddress, toAddress, e.getMessage());
            return null;
        } finally {
            if (wrapper != null) wrapper.close();
        }
    }

    @Override
    public boolean isTxConfirmed(String txHash, TChainConfig config) {
        ApiWrapper wrapper = null;
        try {
            wrapper = buildWrapper("0000000000000000000000000000000000000000000000000000000000000001", config);
            org.tron.trident.proto.Response.TransactionInfo info = wrapper.getTransactionInfoById(txHash);
            // result = SUCCESS 表示成功
            return info != null
                    && info.getResult() == org.tron.trident.proto.Response.TransactionInfo.code.SUCESS;
        } catch (Exception e) {
            log.debug("[TRX] isTxConfirmed({}) 异常: {}", txHash, e.getMessage());
            return false;
        } finally {
            if (wrapper != null) wrapper.close();
        }
    }
}
