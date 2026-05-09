package com.ruoyi.chain.aggregation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAggregationItem;
import com.ruoyi.bussiness.domain.TAggregationTask;
import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.domain.TPlatformWallet;
import com.ruoyi.bussiness.domain.TUserAddress;
import com.ruoyi.bussiness.mapper.TAggregationItemMapper;
import com.ruoyi.bussiness.mapper.TAggregationTaskMapper;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import com.ruoyi.bussiness.mapper.TPlatformWalletMapper;
import com.ruoyi.bussiness.mapper.TUserAddressMapper;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.bussiness.wallet.EvmAddressUtil;
import com.ruoyi.bussiness.wallet.HDKeyDerivation;
import com.ruoyi.bussiness.wallet.TrxAddressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 归集服务（Phase 4 核心）。
 *
 * 流程（单条链）：
 *   1. 查所有 t_user_address 在该链的 USDT 余额，筛选 >= min_aggregation_amount 的子地址
 *   2. 建 t_aggregation_task（phase=GAS_TOPUP, status=RUNNING）
 *   3. 派 gas：主钱包给每个子地址转 gas_top_up_amount 原生币（已有充足 gas 的子地址跳过）
 *   4. 等 gas 确认（轮询，单链超时 10 分钟）
 *   5. 归集 USDT：用子地址私钥（HDWallet 派生）把 USDT 转回主钱包
 *   6. 等 USDT 确认
 *   7. 更新 task 统计
 *
 * 多签：TRX 单笔 >= MULTISIG_THRESHOLD 的转账被跳过（status=FAILED），
 * 由运营走链下多签流程处理。EVM 多签暂不接入（未配置 Gnosis Safe）。
 */
@Slf4j
@Service
@EnableScheduling
@EnableAsync
@DependsOn("flyway")
@ConditionalOnProperty(name = "aggregation.enabled", havingValue = "true", matchIfMissing = true)
public class AggregationService {

    /** TRX 多签阈值（单笔 ≥ 此值需要走多签，自动归集跳过） */
    private static final BigDecimal TRX_MULTISIG_THRESHOLD = new BigDecimal("10000");

    /** 单笔 tx 等待确认最长时间（毫秒），超时视为失败 */
    private static final long TX_WAIT_TIMEOUT_MS = 10 * 60 * 1000L;
    /** 轮询确认间隔 */
    private static final long TX_POLL_INTERVAL_MS = 15_000L;

    @Autowired private TChainConfigMapper chainConfigMapper;
    @Autowired private TUserAddressMapper userAddressMapper;
    @Autowired private TPlatformWalletMapper platformWalletMapper;
    @Autowired private TAggregationTaskMapper taskMapper;
    @Autowired private TAggregationItemMapper itemMapper;
    @Autowired private IHDWalletService hdWalletService;
    @Autowired private List<ChainExecutor> chainExecutors;

    /**
     * 每天凌晨 3:07 触发（错峰避开整点 cron）。
     * 可通过 application.yml 的 aggregation.cron 覆盖。
     */
    @Scheduled(cron = "${aggregation.cron:0 7 3 * * ?}")
    @Async
    public void runDailyAggregation() {
        log.info("===== 启动每日归集 =====");
        List<TChainConfig> configs = chainConfigMapper.selectList(null);
        for (TChainConfig config : configs) {
            if (config.getDepositEnabled() == null || config.getDepositEnabled() != 1) {
                continue;
            }
            try {
                runForChain(config);
            } catch (Exception e) {
                log.error("[{}] 归集异常: {}", config.getChain(), e.getMessage(), e);
            }
        }
        log.info("===== 每日归集结束 =====");
    }

    /**
     * 手动触发某条链的归集（admin UI 调用）。
     */
    public Long runForChain(String chain) {
        TChainConfig config = chainConfigMapper.selectById(chain.toUpperCase());
        if (config == null) {
            throw new IllegalArgumentException("未知链: " + chain);
        }
        return runForChain(config);
    }

    private Long runForChain(TChainConfig config) {
        ChainExecutor executor = pickExecutor(config.getChain());
        if (executor == null) {
            log.warn("[{}] 没有匹配的 ChainExecutor", config.getChain());
            return null;
        }

        // 1. 拿主钱包
        TPlatformWallet mainWallet = platformWalletMapper.selectMain(config.getChain());
        String mainAddress;
        String mainPrivateKey;
        if (mainWallet != null) {
            mainAddress = mainWallet.getAddress();
        } else {
            mainAddress = hdWalletService.getMainAddress(config.getChain());
            log.info("[{}] t_platform_wallet 没有 MAIN 配置，使用 HD 派生地址: {}",
                    config.getChain(), mainAddress);
        }
        Bip32ECKeyPair mainKeyPair = hdWalletService.deriveMainKeyPair(config.getChain());
        mainPrivateKey = Numeric.toHexStringNoPrefixZeroPadded(mainKeyPair.getPrivateKey(), 64);

        // 2. 找候选子地址
        List<AggregationCandidate> candidates = findCandidates(config, executor);
        if (candidates.isEmpty()) {
            log.info("[{}] 无候选子地址（USDT 余额都 < {}），跳过本次归集",
                    config.getChain(), config.getMinAggregationAmount());
            return null;
        }
        log.info("[{}] 发现 {} 个候选子地址，准备归集", config.getChain(), candidates.size());

        // 3. 建 task + items
        TAggregationTask task = new TAggregationTask();
        task.setBatchId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setChain(config.getChain());
        task.setPhase("GAS_TOPUP");
        task.setStatus("RUNNING");
        task.setTargetCount(candidates.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setGasTotal(BigDecimal.ZERO);
        task.setUsdtCollected(BigDecimal.ZERO);
        task.setStartTime(new Date());
        task.setCreateTime(new Date());
        taskMapper.insert(task);

        List<TAggregationItem> items = new ArrayList<>();
        for (AggregationCandidate c : candidates) {
            TAggregationItem item = new TAggregationItem();
            item.setTaskId(task.getId());
            item.setChain(config.getChain());
            item.setUserId(c.userId);
            item.setFromAddress(c.address);
            item.setToAddress(mainAddress);
            item.setUsdtAmount(c.usdtBalance);
            item.setGasTopupAmount(BigDecimal.ZERO);
            item.setStatus("PENDING");
            item.setCreateTime(new Date());
            itemMapper.insert(item);
            items.add(item);
        }

        // 4. 派 gas
        BigDecimal gasTotal = phaseGasTopup(config, executor, mainAddress, mainPrivateKey, items);
        task.setGasTotal(gasTotal);
        task.setPhase("COLLECT");
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        // 5. 归集 USDT
        BigDecimal usdtTotal = phaseCollectUsdt(config, executor, mainAddress, items);

        // 6. 收尾
        int success = 0, failed = 0;
        for (TAggregationItem it : items) {
            if ("SUCCESS".equals(it.getStatus())) success++;
            else if ("FAILED".equals(it.getStatus())) failed++;
        }
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setUsdtCollected(usdtTotal);
        task.setStatus(failed == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        task.setPhase("DONE");
        task.setEndTime(new Date());
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        log.info("[{}] 归集完成 batch={} 成功{} 失败{} 共归集 {} USDT",
                config.getChain(), task.getBatchId(), success, failed, usdtTotal);
        return task.getId();
    }

    private ChainExecutor pickExecutor(String chain) {
        for (ChainExecutor ex : chainExecutors) {
            if (ex.supports(chain)) return ex;
        }
        return null;
    }

    /**
     * 扫所有用户子地址，查链上 USDT 余额，筛选满足 min_aggregation_amount 的。
     * 注：此处每个地址一次 RPC 调用，规模上来后需要优化（multicall 或缓存）。
     */
    private List<AggregationCandidate> findCandidates(TChainConfig config, ChainExecutor executor) {
        List<TUserAddress> all = userAddressMapper.selectList(
                new LambdaQueryWrapper<TUserAddress>().eq(TUserAddress::getChain, config.getChain()));
        List<AggregationCandidate> result = new ArrayList<>();
        for (TUserAddress ua : all) {
            BigDecimal bal = executor.queryUsdtBalance(ua.getAddress(), config);
            if (bal != null && bal.compareTo(config.getMinAggregationAmount()) >= 0) {
                result.add(new AggregationCandidate(ua.getUserId(), ua.getAddress(), bal));
            }
        }
        return result;
    }

    /**
     * 给所有候选子地址转 gas（已有足够 gas 的跳过），等待确认。
     */
    private BigDecimal phaseGasTopup(TChainConfig config, ChainExecutor executor,
                                      String mainAddress, String mainPrivateKey,
                                      List<TAggregationItem> items) {
        BigDecimal gasNeeded = config.getGasTopUpAmount();
        BigDecimal totalGasSent = BigDecimal.ZERO;

        for (TAggregationItem item : items) {
            BigDecimal nativeBal = executor.queryNativeBalance(item.getFromAddress(), config);
            if (nativeBal != null && nativeBal.compareTo(gasNeeded) >= 0) {
                // 已有充足 gas，直接进入下一阶段
                item.setStatus("GAS_SENT");
                item.setGasTopupAmount(BigDecimal.ZERO);
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                continue;
            }

            String hash = executor.sendNative(mainPrivateKey, mainAddress, item.getFromAddress(),
                    gasNeeded, config);
            if (hash == null) {
                item.setStatus("FAILED");
                item.setErrorMsg("gas top-up 发送失败");
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                continue;
            }
            item.setStatus("GAS_SENT");
            item.setGasTopupHash(hash);
            item.setGasTopupAmount(gasNeeded);
            item.setUpdateTime(new Date());
            itemMapper.updateById(item);
            totalGasSent = totalGasSent.add(gasNeeded);
        }

        // 等所有 GAS_SENT 的 hash 确认
        for (TAggregationItem item : items) {
            if (!"GAS_SENT".equals(item.getStatus())) continue;
            if (item.getGasTopupHash() == null) continue; // 之前已有 gas，无需等
            boolean ok = waitConfirmed(executor, item.getGasTopupHash(), config);
            if (!ok) {
                item.setStatus("FAILED");
                item.setErrorMsg("gas top-up 超时未确认: " + item.getGasTopupHash());
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
            }
        }
        return totalGasSent;
    }

    /**
     * 子地址 → 主钱包，转走全部 USDT。
     */
    private BigDecimal phaseCollectUsdt(TChainConfig config, ChainExecutor executor,
                                         String mainAddress, List<TAggregationItem> items) {
        BigDecimal collected = BigDecimal.ZERO;

        for (TAggregationItem item : items) {
            if (!"GAS_SENT".equals(item.getStatus())) continue;

            // TRX 多签拦截
            if ("TRX".equalsIgnoreCase(config.getChain())
                    && item.getUsdtAmount().compareTo(TRX_MULTISIG_THRESHOLD) >= 0) {
                item.setStatus("FAILED");
                item.setErrorMsg("金额 " + item.getUsdtAmount() + " 超过多签阈值 "
                        + TRX_MULTISIG_THRESHOLD + "，需运营走链下多签");
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                log.warn("[TRX] 跳过多签场景：用户 {} 金额 {} USDT", item.getUserId(), item.getUsdtAmount());
                continue;
            }

            // 派生子地址私钥
            Bip32ECKeyPair subKey = hdWalletService.derivePrivateKeyPair(item.getUserId(), config.getChain());
            String subPrivKey = Numeric.toHexStringNoPrefixZeroPadded(subKey.getPrivateKey(), 64);
            String subAddrCheck = "TRX".equalsIgnoreCase(config.getChain())
                    ? TrxAddressUtil.addressFromKey(subKey)
                    : EvmAddressUtil.addressFromKey(subKey);

            if (!subAddrCheck.equalsIgnoreCase(item.getFromAddress())) {
                item.setStatus("FAILED");
                item.setErrorMsg("派生地址不匹配，user_id=" + item.getUserId()
                        + " 期望=" + item.getFromAddress() + " 实际=" + subAddrCheck);
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                log.error("派生不匹配: {}", item.getErrorMsg());
                continue;
            }

            // 重新查一次余额（gas top-up 后链上状态可能已变）
            BigDecimal currentBal = executor.queryUsdtBalance(item.getFromAddress(), config);
            if (currentBal.compareTo(config.getMinAggregationAmount()) < 0) {
                item.setStatus("SUCCESS"); // 余额已不足归集，可能已转走，标记成功
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                continue;
            }

            String hash = executor.sendUsdt(subPrivKey, item.getFromAddress(), mainAddress, currentBal, config);
            if (hash == null) {
                item.setStatus("FAILED");
                item.setErrorMsg("USDT collect 发送失败");
                item.setUpdateTime(new Date());
                itemMapper.updateById(item);
                continue;
            }
            item.setCollectHash(hash);
            item.setStatus("COLLECTING");
            item.setUsdtAmount(currentBal);
            item.setUpdateTime(new Date());
            itemMapper.updateById(item);
        }

        // 等 collect 确认
        for (TAggregationItem item : items) {
            if (!"COLLECTING".equals(item.getStatus())) continue;
            boolean ok = waitConfirmed(executor, item.getCollectHash(), config);
            if (ok) {
                item.setStatus("SUCCESS");
                collected = collected.add(item.getUsdtAmount());
            } else {
                item.setStatus("FAILED");
                item.setErrorMsg("USDT collect 超时未确认: " + item.getCollectHash());
            }
            item.setUpdateTime(new Date());
            itemMapper.updateById(item);
        }
        return collected;
    }

    private boolean waitConfirmed(ChainExecutor executor, String txHash, TChainConfig config) {
        long deadline = System.currentTimeMillis() + TX_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (executor.isTxConfirmed(txHash, config)) return true;
            try {
                Thread.sleep(TX_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 候选子地址 POJO */
    private static class AggregationCandidate {
        final Long userId;
        final String address;
        final BigDecimal usdtBalance;

        AggregationCandidate(Long userId, String address, BigDecimal usdtBalance) {
            this.userId = userId;
            this.address = address;
            this.usdtBalance = usdtBalance;
        }
    }
}
