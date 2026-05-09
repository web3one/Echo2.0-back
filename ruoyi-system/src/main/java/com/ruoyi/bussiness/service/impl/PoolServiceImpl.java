package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.domain.TPoolBalance;
import com.ruoyi.bussiness.domain.TPoolLog;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import com.ruoyi.bussiness.mapper.TPoolBalanceMapper;
import com.ruoyi.bussiness.mapper.TPoolLogMapper;
import com.ruoyi.bussiness.service.IPoolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class PoolServiceImpl implements IPoolService {

    @Autowired private TPoolBalanceMapper poolBalanceMapper;
    @Autowired private TChainConfigMapper chainConfigMapper;
    @Autowired private TPoolLogMapper poolLogMapper;

    @Override
    public String validateForWithdraw(String symbol, String chain, BigDecimal amount) {
        if (chain == null || chain.isEmpty()) return null;
        String chainUp = chain.toUpperCase();
        String symUp = symbol == null ? "USDT" : symbol.toUpperCase();

        TChainConfig cfg = chainConfigMapper.selectById(chainUp);
        if (cfg == null) return "不支持的链: " + chain;
        if (cfg.getWithdrawEnabled() == null || cfg.getWithdrawEnabled() != 1) {
            return "该链提现已暂停";
        }

        TPoolBalance pool = poolBalanceMapper.selectBySymbolAndChain(symUp, chainUp);
        if (pool == null) return "底池未配置: " + symUp + "-" + chainUp;

        BigDecimal available = pool.getBalance().subtract(
                pool.getFrozenBalance() == null ? BigDecimal.ZERO : pool.getFrozenBalance());
        if (available.compareTo(amount) < 0) {
            log.warn("[{}] 底池余额不足 available={} request={}", chainUp, available, amount);
            return "该链平台余额不足，请稍后重试或换链";
        }
        if (pool.getMaxSingleWithdraw() != null
                && pool.getMaxSingleWithdraw().compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(pool.getMaxSingleWithdraw()) > 0) {
            return "超过该链单笔最大提现限额 " + pool.getMaxSingleWithdraw();
        }
        return null;
    }

    @Override
    public TPoolBalance getBalance(String symbol, String chain) {
        return poolBalanceMapper.selectBySymbolAndChain(symbol.toUpperCase(), chain.toUpperCase());
    }

    @Override
    public List<TPoolBalance> listAll() {
        return poolBalanceMapper.selectList(null);
    }

    @Override
    public List<TPoolBalance> listForH5() {
        return poolBalanceMapper.selectList(new LambdaQueryWrapper<TPoolBalance>()
                .eq(TPoolBalance::getDisplayInH5, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(String symbol, String chain, BigDecimal delta, String operator, String remark) {
        if (symbol == null || chain == null || delta == null) {
            throw new IllegalArgumentException("symbol/chain/delta 必填");
        }
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("手工调整必填备注");
        }
        String symUp = symbol.toUpperCase();
        String chainUp = chain.toUpperCase();

        TPoolBalance pool = poolBalanceMapper.selectBySymbolAndChain(symUp, chainUp);
        if (pool == null) {
            throw new IllegalStateException("底池未配置: " + symUp + "-" + chainUp);
        }
        BigDecimal before = pool.getBalance();
        BigDecimal after = before.add(delta);
        if (after.signum() < 0) {
            throw new IllegalArgumentException("调整后余额会为负数 (" + after + ")，请减小金额");
        }

        poolBalanceMapper.update(null, new LambdaUpdateWrapper<TPoolBalance>()
                .eq(TPoolBalance::getSymbol, symUp)
                .eq(TPoolBalance::getChain, chainUp)
                .set(TPoolBalance::getBalance, after)
                .set(TPoolBalance::getUpdateTime, new Date())
                .set(TPoolBalance::getUpdateBy, operator));

        TPoolLog logRow = new TPoolLog();
        logRow.setSymbol(symUp);
        logRow.setChain(chainUp);
        logRow.setChangeType("MANUAL");
        logRow.setDirection(delta.signum() >= 0 ? 1 : -1);
        logRow.setAmount(delta.abs());
        logRow.setBeforeBalance(before);
        logRow.setAfterBalance(after);
        logRow.setRefType("MANUAL");
        logRow.setRefId(null);
        logRow.setOperator(operator);
        logRow.setRemark(remark);
        logRow.setCreateTime(new Date());
        logRow.setCreateBy(operator);
        poolLogMapper.insert(logRow);

        log.info("[POOL] 手工调整 {}-{} {} → {} by {} ({})",
                symUp, chainUp, before, after, operator, remark);
    }

    @Override
    public IPage<TPoolLog> pageLog(String symbol, String chain, String changeType,
                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<TPoolLog> q = new LambdaQueryWrapper<TPoolLog>()
                .orderByDesc(TPoolLog::getId);
        if (symbol != null && !symbol.isEmpty()) q.eq(TPoolLog::getSymbol, symbol.toUpperCase());
        if (chain != null && !chain.isEmpty()) q.eq(TPoolLog::getChain, chain.toUpperCase());
        if (changeType != null && !changeType.isEmpty()) q.eq(TPoolLog::getChangeType, changeType);
        return poolLogMapper.selectPage(new Page<>(pageNum, pageSize), q);
    }
}
