package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TContractOrder;
import com.ruoyi.bussiness.domain.TCurrencyOrder;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TContractOrderMapper;
import com.ruoyi.bussiness.mapper.TCurrencyOrderMapper;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TGoldWithdrawOrderMapper;
import com.ruoyi.bussiness.service.IDailyFeeSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * 每日手续费聚合实现。
 *
 * 当前版本说明（B 路线第一组 MVP）：
 * - contract_fee：SUM(t_contract_order.fee)，假设字段已是 USDT 计价（合约本来就 USDT 结算）。
 * - spot_fee：SUM(t_currency_order.fee) WHERE coin='usdt'，**仅算结算币种为 USDT 的订单**。
 *             其他结算币种（如 BTC、ETH）的 fee **暂忽略**，待后续接入 K 线汇率折算后补真实数据。
 * - gold_withdraw：SUM(t_gold_withdraw_order.fee_amount) 已经是 USDT，无需折算。
 *
 * 时间窗口口径：bizDate 是 UTC 业务日，统计 [bizDate 00:00 UTC, bizDate+1 00:00 UTC) 内 createTime 的订单。
 *
 * @date 2026-05-15
 */
@Service
@Slf4j
public class DailyFeeSummaryServiceImpl implements IDailyFeeSummaryService {

    private static final String USDT_SYMBOL = "usdt";

    @Resource
    private TCurrencyOrderMapper currencyOrderMapper;
    @Resource
    private TContractOrderMapper contractOrderMapper;
    @Resource
    private TGoldWithdrawOrderMapper goldWithdrawOrderMapper;
    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();
        r.setTotalCount(1);

        Date from = Date.from(LocalDateTime.of(bizDate, LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC));
        Date to = Date.from(LocalDateTime.of(bizDate.plusDays(1), LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC));

        // 1. Spot 手续费（结算币种 = USDT 的 currency order，其他币种 TODO 接 K 线折算）
        BigDecimal spotFee = sumSpotUsdtFee(from, to);

        // 2. Contract 手续费（合约本来就 USDT 结算）
        BigDecimal contractFee = sumContractFee(from, to);

        // 3. 金矿提现费（独立统计，不进分红基数）
        BigDecimal[] withdrawFees = sumGoldWithdrawFees(from, to);
        BigDecimal totalWithdrawFee = withdrawFees[0];
        BigDecimal founderShare = withdrawFees[1];
        BigDecimal platformFee = withdrawFees[2];

        // 4. UPSERT t_daily_fee_summary
        TDailyFeeSummary existing = dailyFeeSummaryMapper.selectByBizDate(bizDate);
        TDailyFeeSummary row = existing != null ? existing : new TDailyFeeSummary();
        row.setBizDate(bizDate);
        row.setSpotFeeUsdt(spotFee);
        row.setContractFeeUsdt(contractFee);
        row.setDividendBaseUsdt(spotFee.add(contractFee));
        row.setGoldWithdrawFeeUsdt(totalWithdrawFee);
        row.setGoldWithdrawFounderShareUsdt(founderShare);
        row.setGoldWithdrawPlatformFeeUsdt(platformFee);
        row.setStatus(TDailyFeeSummary.STATUS_SETTLED);
        row.setSettleLogId(settleLogId);
        Date now = new Date();
        if (existing == null) {
            row.setCreateTime(now);
            row.setUpdateTime(now);
            dailyFeeSummaryMapper.insert(row);
        } else {
            if (row.getCreateTime() == null) {
                row.setCreateTime(now);
            }
            row.setUpdateTime(now);
            dailyFeeSummaryMapper.updateById(row);
        }

        r.incSuccess();
        r.addAmount(row.getDividendBaseUsdt());
        log.info("[daily_fee_summary] bizDate={} spot={} contract={} base={} withdraw={} (founder={} platform={})",
                bizDate, spotFee, contractFee, row.getDividendBaseUsdt(),
                totalWithdrawFee, founderShare, platformFee);
        return r;
    }

    private BigDecimal sumSpotUsdtFee(Date from, Date to) {
        try {
            List<TCurrencyOrder> orders = currencyOrderMapper.selectList(
                    new LambdaQueryWrapper<TCurrencyOrder>()
                            .eq(TCurrencyOrder::getCoin, USDT_SYMBOL)
                            .eq(TCurrencyOrder::getStatus, 1)
                            .ge(TCurrencyOrder::getCreateTime, from)
                            .lt(TCurrencyOrder::getCreateTime, to)
                            .select(TCurrencyOrder::getId, TCurrencyOrder::getFee));
            BigDecimal sum = BigDecimal.ZERO;
            for (TCurrencyOrder o : orders) {
                if (o.getFee() != null) sum = sum.add(o.getFee());
            }
            return sum;
        } catch (Exception e) {
            log.warn("[daily_fee_summary] sumSpotUsdtFee failed", e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumContractFee(Date from, Date to) {
        try {
            List<TContractOrder> orders = contractOrderMapper.selectList(
                    new LambdaQueryWrapper<TContractOrder>()
                            .ge(TContractOrder::getCreateTime, from)
                            .lt(TContractOrder::getCreateTime, to)
                            .select(TContractOrder::getId, TContractOrder::getFee));
            BigDecimal sum = BigDecimal.ZERO;
            for (TContractOrder o : orders) {
                if (o.getFee() != null) sum = sum.add(o.getFee());
            }
            return sum;
        } catch (Exception e) {
            log.warn("[daily_fee_summary] sumContractFee failed", e);
            return BigDecimal.ZERO;
        }
    }

    /** @return [total, founderShare, platformFee] */
    private BigDecimal[] sumGoldWithdrawFees(Date from, Date to) {
        List<TGoldWithdrawOrder> orders = goldWithdrawOrderMapper.selectList(
                new LambdaQueryWrapper<TGoldWithdrawOrder>()
                        .eq(TGoldWithdrawOrder::getStatus, TGoldWithdrawOrder.STATUS_COMPLETED)
                        .ge(TGoldWithdrawOrder::getCompletedAt, from)
                        .lt(TGoldWithdrawOrder::getCompletedAt, to)
                        .select(TGoldWithdrawOrder::getId,
                                TGoldWithdrawOrder::getFeeAmount,
                                TGoldWithdrawOrder::getFounderShareAmount,
                                TGoldWithdrawOrder::getPlatformFeeAmount));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal founder = BigDecimal.ZERO;
        BigDecimal platform = BigDecimal.ZERO;
        for (TGoldWithdrawOrder o : orders) {
            if (o.getFeeAmount() != null) total = total.add(o.getFeeAmount());
            if (o.getFounderShareAmount() != null) founder = founder.add(o.getFounderShareAmount());
            if (o.getPlatformFeeAmount() != null) platform = platform.add(o.getPlatformFeeAmount());
        }
        return new BigDecimal[]{total, founder, platform};
    }
}
