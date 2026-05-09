package com.ruoyi.chain.payout;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.domain.TPlatformWallet;
import com.ruoyi.bussiness.domain.TPoolBalance;
import com.ruoyi.bussiness.domain.TWithdraw;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import com.ruoyi.bussiness.mapper.TPlatformWalletMapper;
import com.ruoyi.bussiness.mapper.TPoolBalanceMapper;
import com.ruoyi.bussiness.mapper.TWithdrawMapper;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.bussiness.service.IPoolService;
import com.ruoyi.chain.DepositAutoIngestService;
import com.ruoyi.chain.aggregation.ChainExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 链上提现自动打款服务（Phase 5 核心）。
 *
 * 调用时机：管理员审核通过后，TWithdrawController.passOrder 调本服务。
 *
 * 流程：
 *   1. 校验 t_pool_balance 余额（双重保险，submit 时已校验过一次）
 *   2. 取热钱包私钥（HD 派生 index=0 的主钱包，或 t_platform_wallet 配置的 HOT）
 *   3. 调 ChainExecutor.sendUsdt 发起转账，立即拿 tx_hash
 *   4. 写 t_withdraw.tx_hash + 扣减底池
 *   5. 异步轮询确认：成功保持 status=1；失败回滚 status=2 + 退还用户余额 + 退还底池
 */
@Slf4j
@Service
public class ChainPayoutService {

    @Autowired private TWithdrawMapper withdrawMapper;
    @Autowired private TChainConfigMapper chainConfigMapper;
    @Autowired private TPoolBalanceMapper poolBalanceMapper;
    @Autowired private TPlatformWalletMapper platformWalletMapper;
    @Autowired private TAppAssetMapper assetMapper;
    @Autowired private IHDWalletService hdWalletService;
    @Autowired private IPoolService poolService;
    @Autowired private DepositAutoIngestService poolHelper;
    @Autowired private List<ChainExecutor> chainExecutors;

    /** 异步确认轮询超时（毫秒） */
    private static final long CONFIRM_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final long CONFIRM_POLL_MS = 20_000L;

    /**
     * 提现提交前的底池校验。委托给共享的 IPoolService（admin/api 通用）。
     */
    public String validatePoolBalance(String symbol, String chain, BigDecimal amount) {
        return poolService.validateForWithdraw(symbol, chain, amount);
    }

    /**
     * 审核通过后调用：发起链上转账，写 hash + 扣底池，启动异步确认轮询。
     *
     * @return 错误消息（null=成功提交）
     */
    @Transactional(rollbackFor = Exception.class)
    public String executePayout(Long withdrawId) {
        TWithdraw w = withdrawMapper.selectById(withdrawId);
        if (w == null) return "提现单不存在: " + withdrawId;
        if (w.getChain() == null || w.getChain().isEmpty()) {
            // 不是链上提现（可能走第三方支付通道），跳过
            return null;
        }
        if (w.getTxHash() != null && !w.getTxHash().isEmpty()) {
            // 幂等：已经发过链上交易，直接当成功处理（避免双击时上游 passOrder 误把 status 回滚到 3）
            log.warn("[{}] 提现 {} 重复触发已忽略，已存在 tx_hash={}",
                    w.getChain(), withdrawId, w.getTxHash());
            return null;
        }

        TChainConfig cfg = chainConfigMapper.selectById(w.getChain().toUpperCase());
        if (cfg == null) return "未知链: " + w.getChain();

        ChainExecutor exec = pickExecutor(w.getChain());
        if (exec == null) return "未匹配 ChainExecutor: " + w.getChain();

        // 二次校验底池
        String err = validatePoolBalance(w.getCoin() == null ? "USDT" : w.getCoin().toUpperCase(),
                w.getChain(), w.getAmount());
        if (err != null) return err;

        // 取热钱包：优先 t_platform_wallet HOT，没有则用 HD 主钱包
        String fromAddress;
        String fromPrivKey;
        TPlatformWallet hot = platformWalletMapper.selectHot(w.getChain().toUpperCase());
        if (hot != null) {
            fromAddress = hot.getAddress();
        } else {
            fromAddress = hdWalletService.getMainAddress(w.getChain());
        }
        Bip32ECKeyPair mainKp = hdWalletService.deriveMainKeyPair(w.getChain());
        fromPrivKey = Numeric.toHexStringNoPrefixZeroPadded(mainKp.getPrivateKey(), 64);

        // 发起链上转账
        BigDecimal sendAmount = w.getRealAmount() != null ? w.getRealAmount() : w.getAmount();
        String hash = exec.sendUsdt(fromPrivKey, fromAddress, w.getToAdress(), sendAmount, cfg);
        if (hash == null) {
            return "链上转账提交失败";
        }

        // 写 tx_hash
        w.setTxHash(hash);
        w.setUpdateTime(new Date());
        withdrawMapper.updateById(w);

        // 扣底池（直接 balance-，不动 frozen）
        String symbol = w.getCoin() == null ? "USDT" : w.getCoin().toUpperCase();
        poolHelper.addPoolBalance(symbol, w.getChain().toUpperCase(),
                sendAmount.negate(), "WITHDRAW", "t_withdraw", w.getId().longValue(),
                w.getUpdateBy() == null ? "SYSTEM" : w.getUpdateBy(),
                "提现自动打款 hash=" + hash);

        log.info("[{}] 提现 {} 链上已提交 user={} amount={} hash={}",
                w.getChain(), w.getId(), w.getUserId(), sendAmount, hash);

        // 异步等确认
        pollConfirmAsync(w.getId().longValue(), hash, w.getChain(), symbol, sendAmount, w.getUserId().longValue());
        return null;
    }

    /**
     * 异步轮询：确认成功什么都不做（已 status=1）；超时或失败 → 状态回滚 + 退款。
     */
    @Async
    public void pollConfirmAsync(Long withdrawId, String txHash, String chain,
                                  String symbol, BigDecimal amount, Long userId) {
        TChainConfig cfg = chainConfigMapper.selectById(chain.toUpperCase());
        ChainExecutor exec = pickExecutor(chain);
        if (cfg == null || exec == null) return;

        long deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (exec.isTxConfirmed(txHash, cfg)) {
                    log.info("[{}] 提现 {} 链上已确认 hash={}", chain, withdrawId, txHash);
                    return;
                }
            } catch (Exception e) {
                log.debug("[{}] confirm poll exception: {}", chain, e.getMessage());
            }
            try {
                Thread.sleep(CONFIRM_POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // 超时：回滚提现 + 底池 + 用户余额
        log.error("[{}] 提现 {} 链上超时未确认，回滚 hash={}", chain, withdrawId, txHash);
        rollbackFailedWithdraw(withdrawId, chain, symbol, amount, userId, "链上超时未确认");
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollbackFailedWithdraw(Long withdrawId, String chain, String symbol,
                                         BigDecimal amount, Long userId, String reason) {
        // 1. 提现状态改为 2 失败
        TWithdraw w = withdrawMapper.selectById(withdrawId);
        if (w == null) return;
        w.setStatus(2);
        w.setRemark((w.getRemark() == null ? "" : w.getRemark() + " | ") + "ROLLBACK: " + reason);
        w.setUpdateTime(new Date());
        withdrawMapper.updateById(w);

        // 2. 底池退还
        poolHelper.addPoolBalance(symbol, chain.toUpperCase(), amount,
                "WITHDRAW", "t_withdraw", withdrawId, "SYSTEM", "提现失败回滚: " + reason);

        // 3. 用户余额退还
        assetMapper.update(null, new LambdaUpdateWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, symbol.toLowerCase())
                .eq(TAppAsset::getType, 1)
                .setSql("amout = amout + " + amount)
                .setSql("available_amount = available_amount + " + amount)
                .set(TAppAsset::getUpdateTime, new Date()));
    }

    private ChainExecutor pickExecutor(String chain) {
        for (ChainExecutor ex : chainExecutors) {
            if (ex.supports(chain)) return ex;
        }
        return null;
    }
}
