package com.ruoyi.chain.aggregation;

import com.ruoyi.bussiness.domain.TChainConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EVM 链交易执行器（ETH/BSC/BASE 共用本类，按 TChainConfig 切换 RPC）。
 */
@Slf4j
@Component
public class EvmChainExecutor implements ChainExecutor {

    /** ETH 转账 gas limit */
    private static final BigInteger GAS_LIMIT_NATIVE = BigInteger.valueOf(21_000L);
    /** ERC20 transfer gas limit（保守留量）*/
    private static final BigInteger GAS_LIMIT_ERC20  = BigInteger.valueOf(120_000L);

    private final Map<String, Web3j> clientCache = new ConcurrentHashMap<>();

    @Override
    public boolean supports(String chain) {
        if (chain == null) return false;
        String c = chain.toUpperCase();
        return "ETH".equals(c) || "BSC".equals(c) || "BASE".equals(c);
    }

    private Web3j getClient(TChainConfig config) {
        return clientCache.computeIfAbsent(config.getChain(), c ->
                Web3j.build(new HttpService(config.getRpcUrl())));
    }

    @Override
    public BigDecimal queryUsdtBalance(String address, TChainConfig config) {
        Web3j web3 = getClient(config);
        Function balanceOf = new Function("balanceOf",
                Collections.singletonList(new Address(address)),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        String data = FunctionEncoder.encode(balanceOf);
        Transaction call = Transaction.createEthCallTransaction(address, config.getUsdtContract(), data);
        try {
            EthCall resp = web3.ethCall(call, DefaultBlockParameterName.LATEST).send();
            if (resp.hasError()) {
                log.warn("[{}] balanceOf({}) error: {}", config.getChain(), address, resp.getError().getMessage());
                return BigDecimal.ZERO;
            }
            List<Type> out = FunctionReturnDecoder.decode(resp.getValue(), balanceOf.getOutputParameters());
            if (out.isEmpty()) return BigDecimal.ZERO;
            BigInteger raw = (BigInteger) out.get(0).getValue();
            return new BigDecimal(raw).movePointLeft(config.getUsdtDecimals());
        } catch (Exception e) {
            log.warn("[{}] queryUsdtBalance({}) exception: {}", config.getChain(), address, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal queryNativeBalance(String address, TChainConfig config) {
        Web3j web3 = getClient(config);
        try {
            EthGetBalance bal = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            return Convert.fromWei(new BigDecimal(bal.getBalance()), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.warn("[{}] queryNativeBalance({}) exception: {}", config.getChain(), address, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String sendNative(String privateKeyHex, String fromAddress, String toAddress,
                              BigDecimal amount, TChainConfig config) {
        Web3j web3 = getClient(config);
        try {
            BigInteger nonce = web3.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send().getTransactionCount();
            BigInteger gasPrice = web3.ethGasPrice().send().getGasPrice();
            BigInteger value = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();
            RawTransaction tx = RawTransaction.createEtherTransaction(nonce, gasPrice, GAS_LIMIT_NATIVE, toAddress, value);
            Credentials creds = Credentials.create(privateKeyHex);
            byte[] signed = TransactionEncoder.signMessage(tx, creds);
            EthSendTransaction send = web3.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            if (send.hasError()) {
                log.error("[{}] sendNative {}->{} 失败: {}",
                        config.getChain(), fromAddress, toAddress, send.getError().getMessage());
                return null;
            }
            return send.getTransactionHash();
        } catch (Exception e) {
            log.error("[{}] sendNative {}->{} 异常: {}",
                    config.getChain(), fromAddress, toAddress, e.getMessage());
            return null;
        }
    }

    @Override
    public String sendUsdt(String privateKeyHex, String fromAddress, String toAddress,
                            BigDecimal amount, TChainConfig config) {
        Web3j web3 = getClient(config);
        try {
            BigInteger nonce = web3.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send().getTransactionCount();
            BigInteger gasPrice = web3.ethGasPrice().send().getGasPrice();
            BigInteger raw = amount.movePointRight(config.getUsdtDecimals()).toBigInteger();

            Function transfer = new Function("transfer",
                    Arrays.asList(new Address(toAddress), new Uint256(raw)),
                    Collections.emptyList());
            String data = FunctionEncoder.encode(transfer);
            RawTransaction tx = RawTransaction.createTransaction(nonce, gasPrice, GAS_LIMIT_ERC20,
                    config.getUsdtContract(), data);
            Credentials creds = Credentials.create(privateKeyHex);
            byte[] signed = TransactionEncoder.signMessage(tx, creds);
            EthSendTransaction send = web3.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            if (send.hasError()) {
                log.error("[{}] sendUsdt {}->{} {}: {}",
                        config.getChain(), fromAddress, toAddress, amount, send.getError().getMessage());
                return null;
            }
            return send.getTransactionHash();
        } catch (Exception e) {
            log.error("[{}] sendUsdt {}->{} 异常: {}",
                    config.getChain(), fromAddress, toAddress, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isTxConfirmed(String txHash, TChainConfig config) {
        Web3j web3 = getClient(config);
        try {
            TransactionReceipt receipt = web3.ethGetTransactionReceipt(txHash).send().getResult();
            if (receipt == null) return false;
            // status=0x1 表示成功
            String status = receipt.getStatus();
            if (status == null) return false;
            int s = Integer.parseInt(status.replace("0x", ""), 16);
            return s == 1;
        } catch (Exception e) {
            log.debug("isTxConfirmed({}) exception: {}", txHash, e.getMessage());
            return false;
        }
    }
}
