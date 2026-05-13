package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TFounderSeat;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TFounderSeatMapper;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.service.IFounderDividendSettleService;
import com.ruoyi.bussiness.service.IGoldWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 创世 49 席手续费分红实现（PRD §12 + §22）。
 *
 * 关键点：
 * - 池子构成（用户决策 2026-05-15 B 口径）：
 *   pool = (spot + contract) × 10%  +  goldWithdrawFounderShareUsdt（全额）
 * - 等分 N = owned 席位数（冻结席位剔除）
 * - 不计入健康出局，100% USDT，直接入金矿子钱包
 *
 * @date 2026-05-15
 */
@Service
@Slf4j
public class FounderDividendSettleServiceImpl implements IFounderDividendSettleService {

    private static final BigDecimal FOUNDER_DIVIDEND_RATE = new BigDecimal("0.10");

    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;
    @Resource
    private TFounderSeatMapper founderSeatMapper;
    @Resource
    private TRewardLogMapper rewardLogMapper;
    @Resource
    private IGoldWalletService goldWalletService;

    @Resource
    @Lazy
    private IFounderDividendSettleService self;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();

        // 1. 读手续费聚合行
        TDailyFeeSummary fee = dailyFeeSummaryMapper.selectByBizDate(bizDate);
        if (fee == null) {
            log.warn("[founder_dividend] no fee summary for bizDate={}, skip", bizDate);
            return r;
        }
        BigDecimal spot = nz(fee.getSpotFeeUsdt());
        BigDecimal contract = nz(fee.getContractFeeUsdt());
        BigDecimal founderShare = nz(fee.getGoldWithdrawFounderShareUsdt());
        BigDecimal pool = spot.add(contract).multiply(FOUNDER_DIVIDEND_RATE).add(founderShare);
        if (pool.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[founder_dividend] pool=0 for bizDate={}, skip", bizDate);
            return r;
        }

        // 2. 查 owned 席位
        List<TFounderSeat> seats = founderSeatMapper.selectAllOwned();
        if (seats == null || seats.isEmpty()) {
            log.info("[founder_dividend] no owned seat, pool {} unfunded bizDate={}", pool, bizDate);
            return r;
        }
        r.setTotalCount(seats.size());

        BigDecimal perSeat = pool.divide(new BigDecimal(seats.size()), 8, RoundingMode.DOWN);
        if (perSeat.compareTo(BigDecimal.ZERO) <= 0) {
            return r;
        }

        log.info("[founder_dividend] bizDate={} pool={}=({}+{})*10%+{} seats={} perSeat={}",
                bizDate, pool, spot, contract, founderShare, seats.size(), perSeat);

        // 3. 子事务发放
        for (TFounderSeat seat : seats) {
            try {
                BigDecimal credited = self.settleOneSeat(
                        seat.getId(), seat.getSeatNo(), seat.getOwnerUserId(),
                        perSeat, bizDate, settleLogId);
                if (credited == null) {
                    r.incSkipped();
                } else {
                    r.incSuccess();
                    r.addAmount(credited);
                }
            } catch (DuplicateKeyException e) {
                log.info("[founder_dividend] idempotent hit, skip seatId={}", seat.getId());
                r.incSkipped();
            } catch (Exception e) {
                log.error("[founder_dividend] settle seat failed seatId={}", seat.getId(), e);
                r.incFailed();
            }
        }

        return r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BigDecimal settleOneSeat(Long seatId, Integer seatNo, Long ownerUserId,
                                    BigDecimal perSeatUsdt,
                                    LocalDate bizDate, Long settleLogId) {
        if (seatId == null || ownerUserId == null
                || perSeatUsdt == null || perSeatUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // 状态二次校验：主流程已查 owned，子事务前再 check 一次防漂移
        TFounderSeat current = founderSeatMapper.selectById(seatId);
        if (current == null
                || !TFounderSeat.STATUS_OWNED.equals(current.getStatus())
                || current.getOwnerUserId() == null
                || !current.getOwnerUserId().equals(ownerUserId)) {
            return null;
        }

        BigDecimal gross = perSeatUsdt.setScale(8, RoundingMode.HALF_UP);
        // 一人一席：用 seatId 做幂等 key（即使后续席位转让也保证同日同席不重发）
        String idempotentKey = bizDate + ":founder_fee:" + seatId;

        // INSERT t_reward_log（100% USDT，不计入健康出局，无 active 矿机要求）
        TRewardLog reward = new TRewardLog();
        reward.setUserId(ownerUserId);
        reward.setRewardType(TRewardLog.TYPE_FOUNDER_FEE);
        reward.setGrossAmountUsdt(gross);
        reward.setUsdtCredited(gross);
        reward.setExitTruncatedAmount(BigDecimal.ZERO);
        reward.setEcoCreditAmount(BigDecimal.ZERO);
        reward.setCountedInHealthExit(0);
        reward.setSettleLogId(settleLogId);
        reward.setBizDate(bizDate);
        reward.setIdempotentKey(idempotentKey);
        reward.setStatus(TRewardLog.STATUS_SETTLED);
        reward.setRemark("seat_no=" + seatNo);
        Date now = new Date();
        reward.setCreateTime(now);
        reward.setUpdateTime(now);
        rewardLogMapper.insert(reward);
        Long rewardLogId = reward.getId();

        // 入账子钱包
        goldWalletService.addBalance(
                ownerUserId,
                gross,
                TGoldWalletLog.CHANGE_REWARD_FOUNDER_FEE,
                TGoldWalletLog.BIZ_REF_REWARD_LOG,
                String.valueOf(rewardLogId),
                "founder_fee:" + rewardLogId,
                "FOUNDER #" + seatNo);

        return gross;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
