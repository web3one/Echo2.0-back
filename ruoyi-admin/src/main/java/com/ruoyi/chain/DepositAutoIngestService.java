package com.ruoyi.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppRecharge;
import com.ruoyi.bussiness.domain.TPoolBalance;
import com.ruoyi.bussiness.domain.TPoolLog;
import com.ruoyi.bussiness.domain.TUserAddress;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TAppRechargeMapper;
import com.ruoyi.bussiness.mapper.TPoolBalanceMapper;
import com.ruoyi.bussiness.mapper.TPoolLogMapper;
import com.ruoyi.bussiness.mapper.TUserAddressMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * 自动入账：链监听 worker 检测到一笔充值后调用本服务，
 * 在一个事务里完成：写充值记录 + 加用户余额 + 加底池 + 写底池流水。
 *
 * 幂等：t_app_recharge.(chain, tx_hash) 唯一键防止重复入账。
 */
@Slf4j
@Service
public class DepositAutoIngestService {

    @Autowired private TAppRechargeMapper rechargeMapper;
    @Autowired private TUserAddressMapper userAddressMapper;
    @Autowired private TAppAssetMapper assetMapper;
    @Autowired private TPoolBalanceMapper poolBalanceMapper;
    @Autowired private TPoolLogMapper poolLogMapper;

    /** 平台账户类型：1=平台资产 */
    private static final Integer ASSET_TYPE_PLATFORM = 1;

    /**
     * 处理一笔检测到的充值。返回 true=新入账，false=重复或地址不属于平台已忽略。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean ingest(DetectedDeposit deposit) {
        // 1. 反查地址所属用户
        TUserAddress userAddr = userAddressMapper.selectByChainAndAddress(
                deposit.getChain(), deposit.getToAddress());
        if (userAddr == null) {
            // 不是平台用户的地址，跳过（可能是平台主钱包/归集地址或外部地址）
            return false;
        }

        // 2. 幂等检查
        TAppRecharge dup = rechargeMapper.selectOne(new LambdaQueryWrapper<TAppRecharge>()
                .eq(TAppRecharge::getChain, deposit.getChain())
                .eq(TAppRecharge::getTxHash, deposit.getTxHash()));
        if (dup != null) {
            return false;
        }

        // 3. 写充值记录
        TAppRecharge recharge = new TAppRecharge();
        recharge.setUserId(userAddr.getUserId());
        recharge.setAmount(deposit.getAmount());
        recharge.setRealAmount(deposit.getAmount());
        recharge.setStatus("1"); // 1=成功（自动到账无需审核）
        recharge.setSerialId("AUTO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        recharge.setType(deposit.getSymbol() + "-" + deposit.getChain()); // 兼容旧字段："USDT-ETH"
        recharge.setCoin(deposit.getSymbol());
        recharge.setChain(deposit.getChain());
        recharge.setTxHash(deposit.getTxHash());
        recharge.setFromAddress(deposit.getFromAddress());
        recharge.setToAddress(deposit.getToAddress());
        recharge.setAddress(deposit.getToAddress());
        recharge.setBlockNumber(deposit.getBlockNumber());
        recharge.setBlockTime(deposit.getBlockTime());
        recharge.setOrderType("1");
        recharge.setNoticeFlag(0);
        recharge.setOperateTime(new Date());
        recharge.setCreateTime(new Date());

        try {
            rechargeMapper.insert(recharge);
        } catch (DuplicateKeyException dup2) {
            // 并发同 hash 入账，防御性兜底
            log.info("跳过重复入账 chain={} hash={}", deposit.getChain(), deposit.getTxHash());
            return false;
        }

        // 4. 加用户资产（symbol 统一小写，与现有数据约定一致）
        addUserAsset(userAddr.getUserId(), deposit.getSymbol().toLowerCase(), deposit.getAmount());

        // 5. 加底池 + 写流水
        addPoolBalance(deposit.getSymbol(), deposit.getChain(), deposit.getAmount(),
                "RECHARGE", "t_app_recharge", recharge.getId(), "SYSTEM",
                "自动入账：" + deposit.getChain() + " " + deposit.getTxHash());

        log.info("✓ 自动入账 user={} {} {} {} (hash={})",
                userAddr.getUserId(), deposit.getAmount(), deposit.getSymbol(),
                deposit.getChain(), deposit.getTxHash());
        return true;
    }

    /**
     * 给用户资产 +amount，没记录则建一条。
     */
    private void addUserAsset(Long userId, String symbol, BigDecimal amount) {
        TAppAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, ASSET_TYPE_PLATFORM));

        if (asset == null) {
            asset = new TAppAsset();
            asset.setUserId(userId);
            asset.setSymbol(symbol);
            asset.setType(ASSET_TYPE_PLATFORM);
            asset.setAmout(amount);
            asset.setAvailableAmount(amount);
            asset.setOccupiedAmount(BigDecimal.ZERO);
            asset.setCreateTime(new Date());
            assetMapper.insert(asset);
        } else {
            assetMapper.update(null, new LambdaUpdateWrapper<TAppAsset>()
                    .eq(TAppAsset::getUserId, userId)
                    .eq(TAppAsset::getSymbol, symbol)
                    .eq(TAppAsset::getType, ASSET_TYPE_PLATFORM)
                    .setSql("amout = amout + " + amount)
                    .setSql("available_amount = available_amount + " + amount)
                    .set(TAppAsset::getUpdateTime, new Date()));
        }
    }

    /**
     * 给底池 +amount 并写流水。direction=1 表示增加。
     * 如果 amount 为负则表示扣减（提现/归集等场景复用本方法）。
     */
    public void addPoolBalance(String symbol, String chain, BigDecimal amount,
                                String changeType, String refType, Long refId,
                                String operator, String remark) {
        TPoolBalance pool = poolBalanceMapper.selectBySymbolAndChain(symbol, chain);
        if (pool == null) {
            // 防御：底池行应该在 V004 迁移就建好，没有就建一条
            pool = new TPoolBalance();
            pool.setSymbol(symbol);
            pool.setChain(chain);
            pool.setBalance(BigDecimal.ZERO);
            pool.setFrozenBalance(BigDecimal.ZERO);
            pool.setAlertThreshold(BigDecimal.ZERO);
            pool.setMaxSingleWithdraw(BigDecimal.ZERO);
            pool.setDisplayInH5(1);
            pool.setDisplayBalance(1);
            pool.setCreateTime(new Date());
            poolBalanceMapper.insert(pool);
        }

        BigDecimal before = pool.getBalance();
        BigDecimal after = before.add(amount);

        poolBalanceMapper.update(null, new LambdaUpdateWrapper<TPoolBalance>()
                .eq(TPoolBalance::getSymbol, symbol)
                .eq(TPoolBalance::getChain, chain)
                .set(TPoolBalance::getBalance, after)
                .set(TPoolBalance::getUpdateTime, new Date()));

        TPoolLog logRow = new TPoolLog();
        logRow.setSymbol(symbol);
        logRow.setChain(chain);
        logRow.setChangeType(changeType);
        logRow.setDirection(amount.signum() >= 0 ? 1 : -1);
        logRow.setAmount(amount.abs());
        logRow.setBeforeBalance(before);
        logRow.setAfterBalance(after);
        logRow.setRefType(refType);
        logRow.setRefId(refId);
        logRow.setOperator(operator);
        logRow.setRemark(remark);
        logRow.setCreateTime(new Date());
        poolLogMapper.insert(logRow);
    }
}
