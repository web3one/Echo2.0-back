package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.service.IAgencyGlobalDividendSettleService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * V4/V5 全网手续费分红实现（PRD §8.1 + §11 + §22）。
 *
 * 关键点：
 * - 不走 DynamicRewardHelper（那是 70/30 + 健康出局通用方法，不适合 100% USDT 不截断的全网分红）
 * - 直接 INSERT t_reward_log + goldWalletService.addBalance
 * - 不更新 t_node_instance.accumulated_*（PRD §22.6：本类不计入健康出局）
 *
 * @date 2026-05-15
 */
@Service
@Slf4j
public class AgencyGlobalDividendSettleServiceImpl implements IAgencyGlobalDividendSettleService {

    private static final BigDecimal V4_RATE = new BigDecimal("0.005");
    private static final BigDecimal V5_RATE = new BigDecimal("0.010");

    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;
    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;
    @Resource
    private TRewardLogMapper rewardLogMapper;
    @Resource
    private IGoldWalletService goldWalletService;

    @Resource
    @Lazy
    private IAgencyGlobalDividendSettleService self;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();

        // 1. 读手续费聚合行（B 第一组的 daily_fee_summary cron 应该已跑过）
        TDailyFeeSummary fee = dailyFeeSummaryMapper.selectByBizDate(bizDate);
        if (fee == null) {
            log.warn("[agency_global_dividend] no fee summary for bizDate={}, skip", bizDate);
            return r;
        }
        BigDecimal base = nz(fee.getDividendBaseUsdt());
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[agency_global_dividend] dividend base=0 for bizDate={}, skip", bizDate);
            return r;
        }

        // 2. 分别处理 V4 / V5
        settleLevel(r, TAgentStatus.LEVEL_V4, base.multiply(V4_RATE), bizDate, settleLogId);
        settleLevel(r, TAgentStatus.LEVEL_V5, base.multiply(V5_RATE), bizDate, settleLogId);

        log.info("[agency_global_dividend] done bizDate={} base={} total={} success={} skipped={} amount={}",
                bizDate, base, r.getTotalCount(), r.getSuccessCount(),
                r.getSkippedCount(), r.getAmountSettledUsdt());
        return r;
    }

    private void settleLevel(SettleResult r, String levelCode, BigDecimal pool,
                             LocalDate bizDate, Long settleLogId) {
        if (pool.compareTo(BigDecimal.ZERO) <= 0) return;

        // 1) 查该等级 active 用户
        List<TAgentStatus> agents = agentStatusMapper.selectActiveByLevel(levelCode);
        if (agents == null || agents.isEmpty()) {
            log.info("[agency_global_dividend] no active {} agent, skip pool {}", levelCode, pool);
            return;
        }

        // 2) 过滤：必须至少 1 台 active 矿机（PRD §2.3 表）
        List<Long> eligibleUserIds = new ArrayList<>();
        for (TAgentStatus a : agents) {
            BigDecimal sumActive = nodeInstanceMapper.sumActiveValueByUserId(a.getUserId());
            if (nz(sumActive).compareTo(BigDecimal.ZERO) > 0) {
                eligibleUserIds.add(a.getUserId());
            }
        }
        if (eligibleUserIds.isEmpty()) {
            log.info("[agency_global_dividend] {} agents have no active miner, pool {} unfunded",
                    levelCode, pool);
            return;
        }

        // 3) 等分
        BigDecimal share = pool.divide(new BigDecimal(eligibleUserIds.size()), 8, RoundingMode.DOWN);
        if (share.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        r.setTotalCount(r.getTotalCount() + eligibleUserIds.size());

        // 4) 子事务发放
        for (Long userId : eligibleUserIds) {
            try {
                BigDecimal credited = self.settleOneUser(userId, levelCode, share, bizDate, settleLogId);
                if (credited == null) {
                    r.incSkipped();
                } else {
                    r.incSuccess();
                    r.addAmount(credited);
                }
            } catch (DuplicateKeyException e) {
                log.info("[agency_global_dividend] idempotent hit, skip userId={}", userId);
                r.incSkipped();
            } catch (Exception e) {
                log.error("[agency_global_dividend] settle one user failed userId={}", userId, e);
                r.incFailed();
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BigDecimal settleOneUser(Long userId, String agentLevelSnap, BigDecimal perUserShareUsdt,
                                    LocalDate bizDate, Long settleLogId) {
        if (userId == null || perUserShareUsdt == null
                || perUserShareUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 冻结再校验（避免主流程读到 active 但子事务前被改成 frozen）
        TAgentStatus agent = agentStatusMapper.selectByUserId(userId);
        if (agent == null
                || TAgentStatus.STATUS_FROZEN.equals(agent.getStatus())
                || !agentLevelSnap.equals(agent.getAgentLevel())) {
            return null;
        }

        // active 矿机存在性二次校验
        BigDecimal sumActive = nodeInstanceMapper.sumActiveValueByUserId(userId);
        if (nz(sumActive).compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 找一台当前权益矿机（最高级 active）作为 related_node_instance_id 用于审计
        List<TNodeInstance> miners = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<TNodeInstance>()
                        .eq(TNodeInstance::getUserId, userId)
                        .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE));
        Long relatedNodeId = null;
        int bestRank = -1;
        for (TNodeInstance m : miners) {
            int rank = levelRank(m.getLevelCode());
            if (rank > bestRank) {
                bestRank = rank;
                relatedNodeId = m.getId();
            }
        }

        BigDecimal gross = perUserShareUsdt.setScale(8, RoundingMode.HALF_UP);
        String idempotentKey = bizDate + ":agency_fee:" + userId;

        // INSERT t_reward_log（100% USDT，不计入健康出局）
        TRewardLog reward = new TRewardLog();
        reward.setUserId(userId);
        reward.setRewardType(TRewardLog.TYPE_AGENCY_FEE);
        reward.setGrossAmountUsdt(gross);
        reward.setUsdtCredited(gross);
        reward.setExitTruncatedAmount(BigDecimal.ZERO);
        reward.setEcoCreditAmount(BigDecimal.ZERO);
        reward.setRelatedNodeInstanceId(relatedNodeId);
        reward.setAgentLevelSnapshot(agentLevelSnap);
        reward.setCountedInHealthExit(0);
        reward.setSettleLogId(settleLogId);
        reward.setBizDate(bizDate);
        reward.setIdempotentKey(idempotentKey);
        reward.setStatus(TRewardLog.STATUS_SETTLED);
        rewardLogMapper.insert(reward);
        Long rewardLogId = reward.getId();

        // 入账子钱包
        goldWalletService.addBalance(
                userId,
                gross,
                TGoldWalletLog.CHANGE_REWARD_AGENCY_FEE,
                TGoldWalletLog.BIZ_REF_REWARD_LOG,
                String.valueOf(rewardLogId),
                "agency_fee:" + rewardLogId,
                "AGENCY_FEE " + agentLevelSnap);

        return gross;
    }

    private static int levelRank(String levelCode) {
        if (levelCode == null) return -1;
        switch (levelCode) {
            case "L1": return 1;
            case "L2": return 2;
            case "L3": return 3;
            case "L4": return 4;
            default: return -1;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
