package com.ruoyi.chain;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.bussiness.domain.TChainConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.trident.utils.Base58Check;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TRX 链 USDT 充值扫描器（基于 TronGrid HTTP API 的合约事件接口）。
 *
 * 工作流程：
 *   1. 查询近 N 分钟内 USDT 合约的 Transfer 事件
 *   2. 解码 from/to/value（TRX 地址转 base58）
 *   3. 过滤掉已经扫描过的（block_number ≤ lastScannedBlock）
 *   4. 返回 DetectedDeposit 列表
 *
 * 注：DepositAutoIngestService 已经有 (chain, tx_hash) 幂等防护，
 *     即使重复返回也不会重复入账。
 */
@Slf4j
@Component
public class TrxDepositScanner {

    /** 每次回查的时间窗口（毫秒），冗余以应对偶发延迟 */
    private static final long LOOKBACK_MS = 5 * 60 * 1000L;

    /** 单次请求最多取 200 条事件 */
    private static final int LIMIT = 200;

    public List<DetectedDeposit> scan(TChainConfig config) {
        long now = System.currentTimeMillis();
        long minTimestamp = now - LOOKBACK_MS;

        String baseUrl = config.getRpcUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new ArrayList<>();
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + "/v1/contracts/" + config.getUsdtContract()
                + "/events?event_name=Transfer"
                + "&min_block_timestamp=" + minTimestamp
                + "&order_by=block_timestamp,asc"
                + "&limit=" + LIMIT
                + "&only_confirmed=true";

        String resp;
        try {
            resp = HttpUtil.get(url, 10_000);
        } catch (Exception e) {
            log.warn("[TRX] 请求 TronGrid 失败: {}", e.getMessage());
            return tryBackup(config, minTimestamp);
        }

        return parseEvents(config, resp);
    }

    private List<DetectedDeposit> tryBackup(TChainConfig config, long minTimestamp) {
        if (config.getBackupRpcUrl() == null || config.getBackupRpcUrl().isEmpty()) {
            return new ArrayList<>();
        }
        String backupBase = config.getBackupRpcUrl();
        if (backupBase.endsWith("/")) {
            backupBase = backupBase.substring(0, backupBase.length() - 1);
        }
        String url = backupBase + "/v1/contracts/" + config.getUsdtContract()
                + "/events?event_name=Transfer&min_block_timestamp=" + minTimestamp
                + "&order_by=block_timestamp,asc&limit=" + LIMIT + "&only_confirmed=true";
        try {
            return parseEvents(config, HttpUtil.get(url, 10_000));
        } catch (Exception e) {
            log.error("[TRX] 备用 TronGrid 也失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<DetectedDeposit> parseEvents(TChainConfig config, String json) {
        List<DetectedDeposit> deposits = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return deposits;
        }

        JSONObject root;
        try {
            root = JSONObject.parseObject(json);
        } catch (Exception e) {
            log.warn("[TRX] 解析 JSON 失败");
            return deposits;
        }

        JSONArray data = root.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return deposits;
        }

        long lastScanned = config.getLastScannedBlock() == null ? 0L : config.getLastScannedBlock();
        long maxBlock = lastScanned;
        int decimals = config.getUsdtDecimals();

        for (int i = 0; i < data.size(); i++) {
            JSONObject ev = data.getJSONObject(i);
            try {
                long blockNumber = ev.getLongValue("block_number");
                if (blockNumber <= lastScanned) {
                    continue;
                }
                long blockTimestamp = ev.getLongValue("block_timestamp");

                JSONObject result = ev.getJSONObject("result");
                if (result == null) continue;

                String fromHex = result.getString("from");
                String toHex = result.getString("to");
                String valueStr = result.getString("value");
                if (fromHex == null || toHex == null || valueStr == null) continue;

                BigInteger valueRaw = new BigInteger(valueStr);
                BigDecimal amount = new BigDecimal(valueRaw).movePointLeft(decimals);

                deposits.add(DetectedDeposit.builder()
                        .chain("TRX")
                        .txHash(ev.getString("transaction_id"))
                        .fromAddress(normalizeTrxAddress(fromHex))
                        .toAddress(normalizeTrxAddress(toHex))
                        .amount(amount)
                        .symbol("USDT")
                        .blockNumber(blockNumber)
                        .blockTime(new Date(blockTimestamp))
                        .build());

                if (blockNumber > maxBlock) {
                    maxBlock = blockNumber;
                }
            } catch (Exception e) {
                log.debug("[TRX] 解析单条事件失败: {}", e.getMessage());
            }
        }

        config.setLastScannedBlock(maxBlock);
        log.debug("[TRX] 命中 {} 条 USDT Transfer，最高块 {}", deposits.size(), maxBlock);
        return deposits;
    }

    /**
     * TRX 地址归一化：HEX（含或不含 0x41 前缀）→ T... base58
     */
    private static String normalizeTrxAddress(String addr) {
        if (addr == null || addr.isEmpty()) return null;
        if (addr.startsWith("T") && addr.length() == 34) {
            return addr;
        }
        try {
            String hex = addr.startsWith("0x") ? addr.substring(2) : addr;
            byte[] bytes;
            if (hex.length() == 42 && (hex.startsWith("41") || hex.startsWith("4"))) {
                bytes = Numeric.hexStringToByteArray(hex);
            } else if (hex.length() == 40) {
                bytes = Numeric.hexStringToByteArray("41" + hex);
            } else {
                return addr;
            }
            return Base58Check.bytesToBase58(bytes);
        } catch (Exception e) {
            return addr;
        }
    }
}
