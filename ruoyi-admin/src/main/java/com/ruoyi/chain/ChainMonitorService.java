package com.ruoyi.chain;

import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 链监听服务（Phase 3 核心）。
 *
 * 启动时为每条 deposit_enabled=1 的链创建一个定时任务，按 block_polling_interval_ms 周期：
 *   1. 调对应 scanner 扫一段区块，得到 DetectedDeposit 列表
 *   2. 每条调 DepositAutoIngestService.ingest 入账
 *   3. 把进度持久化到 t_chain_config.last_scanned_block
 *
 * 只在 admin 进程跑（@ConditionalOnProperty），api 进程不会启动。
 */
@Slf4j
@Service
@DependsOn("flyway")
@ConditionalOnProperty(name = "chain.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class ChainMonitorService {

    @Autowired private TChainConfigMapper chainConfigMapper;
    @Autowired private EvmDepositScanner evmScanner;
    @Autowired private TrxDepositScanner trxScanner;
    @Autowired private DepositAutoIngestService ingestService;

    private ScheduledExecutorService executor;

    @PostConstruct
    public void start() {
        List<TChainConfig> configs = chainConfigMapper.selectList(null);
        if (configs == null || configs.isEmpty()) {
            log.warn("t_chain_config 没有配置，链监听服务不启动");
            return;
        }

        AtomicInteger threadIdx = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(configs.size(), r -> {
            Thread t = new Thread(r, "chain-monitor-" + threadIdx.incrementAndGet());
            t.setDaemon(true);
            return t;
        });

        int activeCount = 0;
        for (TChainConfig config : configs) {
            if (config.getDepositEnabled() == null || config.getDepositEnabled() != 1) {
                log.info("[{}] 充值已停用，跳过监听", config.getChain());
                continue;
            }

            long interval = (config.getBlockPollingIntervalMs() != null
                    && config.getBlockPollingIntervalMs() > 0)
                    ? config.getBlockPollingIntervalMs() : 10_000L;

            // 启动时延迟 5 秒，避免应用初始化期间打节点
            executor.scheduleWithFixedDelay(
                    () -> safeRunScan(config),
                    5_000,
                    interval,
                    TimeUnit.MILLISECONDS);

            activeCount++;
            log.info("[{}] 监听已启动，间隔 {} ms，初始 lastScannedBlock={}",
                    config.getChain(), interval, config.getLastScannedBlock());
        }
        log.info("ChainMonitorService 启动，监听 {} 条链", activeCount);
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            log.info("ChainMonitorService 已停止");
        }
    }

    /**
     * 单链单次扫描循环。任何异常被吞掉，避免单链失败影响其他链。
     */
    private void safeRunScan(TChainConfig config) {
        long startMs = System.currentTimeMillis();
        try {
            // 每轮从 DB 重读 last_scanned_block，让运维手动 UPDATE 能被扫描器立即识别
            // （否则内存里旧值会在持久化时覆盖 DB 的修改）
            TChainConfig fresh = chainConfigMapper.selectById(config.getChain());
            if (fresh != null && fresh.getLastScannedBlock() != null) {
                config.setLastScannedBlock(fresh.getLastScannedBlock());
            }

            List<DetectedDeposit> deposits;
            if ("TRX".equalsIgnoreCase(config.getChain())) {
                deposits = trxScanner.scan(config);
            } else {
                deposits = evmScanner.scan(config);
            }

            int ingested = 0;
            for (DetectedDeposit d : deposits) {
                try {
                    if (ingestService.ingest(d)) {
                        ingested++;
                    }
                } catch (Exception e) {
                    log.error("[{}] 入账失败 hash={}: {}", config.getChain(), d.getTxHash(), e.getMessage());
                }
            }

            // 持久化扫描进度
            if (config.getLastScannedBlock() != null && config.getLastScannedBlock() > 0) {
                chainConfigMapper.updateLastScannedBlock(config.getChain(), config.getLastScannedBlock());
            }

            if (ingested > 0) {
                log.info("[{}] ✓ 自动入账 {} 笔（扫描事件 {}, 耗时 {}ms）",
                        config.getChain(), ingested, deposits.size(), System.currentTimeMillis() - startMs);
            }
        } catch (Throwable t) {
            log.error("[{}] 扫描异常: {}", config.getChain(), t.getMessage(), t);
        }
    }
}
