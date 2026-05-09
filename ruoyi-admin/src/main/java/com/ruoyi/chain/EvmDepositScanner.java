package com.ruoyi.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.domain.TUserAddress;
import com.ruoyi.bussiness.mapper.TUserAddressMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EVM 链 USDT 充值扫描器（ETH / BSC / BASE 共用本类）。
 *
 * 工作流程：
 *   1. 拿到上次扫描到的区块号 X 和当前最新区块号 Y
 *   2. 计算安全扫描区间 [X+1, Y - confirmations]
 *   3. 分块（每次 500 个区块）调 eth_getLogs（按 USDT 合约 + Transfer topic + 平台充值地址过滤）
 *   4. 解码每条 Transfer 事件 → DetectedDeposit
 *   5. 主 RPC 失败自动切换备用 RPC
 */
@Slf4j
@Component
public class EvmDepositScanner {

    /** Transfer(address,address,uint256) 事件签名 keccak256 */
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    /** 每次最多扫描的块数（公共 RPC 普遍限制 ≤ 1000）*/
    private static final int CHUNK_SIZE = 500;

    /** 单次 eth_getLogs 最多放多少个 to 地址 topic，避免请求体过大 */
    private static final int ADDRESS_TOPIC_CHUNK_SIZE = 100;

    /** 单次扫描最多回溯块数（首次启动场景，避免一次扫数月历史）*/
    private static final int INITIAL_LOOKBACK_BLOCKS = 200;

    /** 每条链的 web3j 客户端（按 chain 缓存）*/
    private final Map<String, Web3j> clientCache = new ConcurrentHashMap<>();

    @Autowired
    private TUserAddressMapper userAddressMapper;

    /**
     * 扫描一个 EVM 链。返回检测到的所有 USDT 充值事件（不区分 to 地址，调用方负责过滤）。
     * 同时更新 config.lastScannedBlock。
     */
    public List<DetectedDeposit> scan(TChainConfig config) {
        Web3j web3 = getOrCreateClient(config);

        BigInteger latest;
        try {
            latest = web3.ethBlockNumber().send().getBlockNumber();
        } catch (Exception e) {
            log.warn("[{}] 获取最新区块失败，尝试切备用 RPC: {}", config.getChain(), e.getMessage());
            web3 = switchToBackup(config);
            if (web3 == null) {
                return new ArrayList<>();
            }
            try {
                latest = web3.ethBlockNumber().send().getBlockNumber();
            } catch (Exception e2) {
                log.error("[{}] 备用 RPC 也失败: {}", config.getChain(), e2.getMessage());
                return new ArrayList<>();
            }
        }

        long latestSafe = latest.longValue() - config.getMinConfirmations();
        long lastScanned = config.getLastScannedBlock() == null ? 0L : config.getLastScannedBlock();
        if (lastScanned == 0) {
            lastScanned = Math.max(0, latestSafe - INITIAL_LOOKBACK_BLOCKS);
            log.info("[{}] 首次扫描，从块 {} 开始", config.getChain(), lastScanned);
        }
        if (latestSafe <= lastScanned) {
            return new ArrayList<>();
        }

        long fromBlock = lastScanned + 1;
        long toBlock = Math.min(fromBlock + CHUNK_SIZE - 1, latestSafe);

        ScanRangeResult rangeResult = scanRange(web3, config, fromBlock, toBlock);
        if (!rangeResult.success && config.getBackupRpcUrl() != null && !config.getBackupRpcUrl().isEmpty()) {
            Web3j backup = switchToBackup(config);
            if (backup != null) {
                rangeResult = scanRange(backup, config, fromBlock, toBlock);
            }
        }

        if (!rangeResult.success) {
            log.warn("[{}] 扫描区间 [{}, {}] 失败，本轮不推进 last_scanned_block: {}",
                    config.getChain(), fromBlock, toBlock, rangeResult.errorMessage);
            return new ArrayList<>();
        }

        List<DetectedDeposit> results = rangeResult.deposits;
        config.setLastScannedBlock(toBlock);
        log.debug("[{}] 扫描区间 [{}, {}] 命中 {} 条 USDT Transfer",
                config.getChain(), fromBlock, toBlock, results.size());
        return results;
    }

    private ScanRangeResult scanRange(Web3j web3, TChainConfig config, long fromBlock, long toBlock) {
        if (config.getUsdtContract() == null || config.getUsdtContract().isEmpty()) {
            return ScanRangeResult.success(new ArrayList<>());
        }

        List<String> toTopics = loadPlatformAddressTopics(config.getChain());
        if (toTopics.isEmpty()) {
            log.debug("[{}] 当前没有平台充值地址，跳过区间 [{}, {}]", config.getChain(), fromBlock, toBlock);
            return ScanRangeResult.success(new ArrayList<>());
        }

        List<DetectedDeposit> deposits = new ArrayList<>();
        for (int i = 0; i < toTopics.size(); i += ADDRESS_TOPIC_CHUNK_SIZE) {
            int end = Math.min(i + ADDRESS_TOPIC_CHUNK_SIZE, toTopics.size());
            List<String> topicChunk = toTopics.subList(i, end);

            ScanRangeResult chunkResult = scanTopicChunk(web3, config, fromBlock, toBlock, topicChunk);
            if (!chunkResult.success) {
                return chunkResult;
            }
            deposits.addAll(chunkResult.deposits);
        }
        return ScanRangeResult.success(deposits);
    }

    private ScanRangeResult scanTopicChunk(Web3j web3, TChainConfig config, long fromBlock, long toBlock,
                                           List<String> toTopics) {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                config.getUsdtContract());
        filter.addSingleTopic(TRANSFER_TOPIC);
        filter.addNullTopic(); // indexed from address: 任意
        if (toTopics.size() == 1) {
            filter.addSingleTopic(toTopics.get(0));
        } else {
            filter.addOptionalTopics(toTopics.toArray(new String[0]));
        }

        EthLog ethLog;
        try {
            ethLog = web3.ethGetLogs(filter).send();
        } catch (Exception e) {
            return ScanRangeResult.failure("eth_getLogs exception: " + e.getMessage());
        }
        if (ethLog.hasError()) {
            return ScanRangeResult.failure("eth_getLogs error: " + ethLog.getError().getMessage());
        }

        List<EthLog.LogResult> logs = ethLog.getLogs();
        if (logs == null || logs.isEmpty()) {
            return ScanRangeResult.success(new ArrayList<>());
        }

        // 收集涉及到的区块号，统一拉取 timestamp（避免每条事件都查一次）
        Map<BigInteger, Date> blockTimes = new HashMap<>();
        List<DetectedDeposit> deposits = new ArrayList<>();
        int decimals = config.getUsdtDecimals();

        for (EthLog.LogResult rawLog : logs) {
            Log lg = (Log) rawLog;
            if (lg.getTopics() == null || lg.getTopics().size() < 3) {
                continue; // 不是标准 Transfer，跳过
            }

            String fromTopic = lg.getTopics().get(1);
            String toTopic = lg.getTopics().get(2);
            String fromAddr = "0x" + fromTopic.substring(fromTopic.length() - 40);
            String toAddr = "0x" + toTopic.substring(toTopic.length() - 40);

            BigDecimal amount;
            try {
                BigInteger valueRaw = Numeric.toBigInt(lg.getData());
                amount = new BigDecimal(valueRaw).movePointLeft(decimals);
            } catch (Exception e) {
                continue;
            }

            BigInteger blockNum = lg.getBlockNumber();
            Date blockTime = blockTimes.computeIfAbsent(blockNum, bn -> fetchBlockTime(web3, bn));

            deposits.add(DetectedDeposit.builder()
                    .chain(config.getChain())
                    .txHash(lg.getTransactionHash())
                    .fromAddress(toChecksum(fromAddr))
                    .toAddress(toChecksum(toAddr))
                    .amount(amount)
                    .symbol("USDT")
                    .blockNumber(blockNum.longValue())
                    .blockTime(blockTime)
                    .build());
        }
        return ScanRangeResult.success(deposits);
    }

    private List<String> loadPlatformAddressTopics(String chain) {
        List<TUserAddress> addresses = userAddressMapper.selectList(
                new LambdaQueryWrapper<TUserAddress>().eq(TUserAddress::getChain, chain));
        List<String> topics = new ArrayList<>();
        if (addresses == null || addresses.isEmpty()) {
            return topics;
        }
        for (TUserAddress address : addresses) {
            String topic = addressToTopic(address.getAddress());
            if (topic != null) {
                topics.add(topic);
            }
        }
        return topics;
    }

    private String addressToTopic(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        String clean = Numeric.cleanHexPrefix(address).toLowerCase();
        if (clean.length() != 40) {
            return null;
        }
        return "0x000000000000000000000000" + clean;
    }

    private Date fetchBlockTime(Web3j web3, BigInteger blockNumber) {
        try {
            EthBlock blk = web3.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (blk.getBlock() != null) {
                return new Date(blk.getBlock().getTimestamp().longValue() * 1000L);
            }
        } catch (Exception e) {
            log.debug("fetch block time failed: {}", e.getMessage());
        }
        return new Date();
    }

    private String toChecksum(String addr) {
        try {
            return org.web3j.crypto.Keys.toChecksumAddress(addr);
        } catch (Exception e) {
            return addr;
        }
    }

    private Web3j getOrCreateClient(TChainConfig config) {
        return clientCache.computeIfAbsent(config.getChain(), c ->
                Web3j.build(new HttpService(config.getRpcUrl())));
    }

    private Web3j switchToBackup(TChainConfig config) {
        if (config.getBackupRpcUrl() == null || config.getBackupRpcUrl().isEmpty()) {
            return null;
        }
        log.info("[{}] 切换到备用 RPC: {}", config.getChain(), config.getBackupRpcUrl());
        Web3j backup = Web3j.build(new HttpService(config.getBackupRpcUrl()));
        clientCache.put(config.getChain(), backup);
        return backup;
    }

    private static class ScanRangeResult {
        private final boolean success;
        private final List<DetectedDeposit> deposits;
        private final String errorMessage;

        private ScanRangeResult(boolean success, List<DetectedDeposit> deposits, String errorMessage) {
            this.success = success;
            this.deposits = deposits;
            this.errorMessage = errorMessage;
        }

        private static ScanRangeResult success(List<DetectedDeposit> deposits) {
            return new ScanRangeResult(true, deposits, null);
        }

        private static ScanRangeResult failure(String errorMessage) {
            return new ScanRangeResult(false, new ArrayList<>(), errorMessage);
        }
    }
}
