package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TBinaryVolumeDaily;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.vo.SettleResult;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeDailyMapper;
import com.ruoyi.bussiness.service.IAgencyTeamRewardSettleService;
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
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * 团队代理奖结算实现（PRD §9 + §22）
 *
 * @date 2026-05-09
 */
@Service
@Slf4j
public class AgencyTeamRewardSettleServiceImpl implements IAgencyTeamRewardSettleService {

    @Resource
    private TBinaryVolumeDailyMapper binaryVolumeDailyMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;
    @Resource
    private TAgentLevelMapper agentLevelMapper;
    @Resource
    private DynamicRewardHelper rewardHelper;

    /** self proxy 触发 @Transactional REQUIRES_NEW（同类自调用不走代理）。 */
    @Resource
    @Lazy
    private IAgencyTeamRewardSettleService self;

    @Override
    public SettleResult settle(LocalDate bizDate, Long settleLogId) {
        SettleResult r = new SettleResult();
        Date bizDateSql = java.sql.Date.valueOf(bizDate);

        // 查当日双轨业绩行（PRD §9.2 第 3 条：左右区当日均有新增 → 由 settleOneUser 内判 0 跳过）
        List<TBinaryVolumeDaily> rows = binaryVolumeDailyMapper.selectList(
                new LambdaQueryWrapper<TBinaryVolumeDaily>()
                        .eq(TBinaryVolumeDaily::getBizDate, bizDateSql));
        r.setTotalCount(rows.size());
        if (rows.isEmpty()) {
            log.info("[agency_team_reward] no binary volume row, bizDate={}", bizDate);
            return r;
        }

        for (TBinaryVolumeDaily row : rows) {
            try {
                BigDecimal credited = self.settleOneUser(
                        row.getUserId(),
                        row.getLeftVolume(), row.getRightVolume(),
                        row.getId(),
                        bizDate, settleLogId);
                if (credited == null) {
                    r.incSkipped();
                } else {
                    r.incSuccess();
                    r.addAmount(credited);
                }
            } catch (DuplicateKeyException e) {
                log.info("[agency_team_reward] idempotent hit, skip: userId={}", row.getUserId());
                r.incSkipped();
            } catch (Exception e) {
                log.error("[agency_team_reward] settle one user failed: userId={}",
                        row.getUserId(), e);
                r.incFailed();
            }
        }
        return r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BigDecimal settleOneUser(Long userId, BigDecimal leftToday, BigDecimal rightToday,
                                    Long volumeDailyRowId,
                                    LocalDate bizDate, Long settleLogId) {
        if (userId == null) return null;

        BigDecimal left = nz(leftToday);
        BigDecimal right = nz(rightToday);
        // PRD §9.2 第 3 条：左右区当日均有新增；任一为 0 直接跳过
        if (left.compareTo(BigDecimal.ZERO) <= 0 || right.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal weak = left.min(right);

        // 1. 代理状态/等级校验
        TAgentStatus agent = agentStatusMapper.selectByUserId(userId);
        if (agent == null) {
            // 用户未在代理体系（V0 默认行也应该有；没有视为 V0）
            return null;
        }
        if (TAgentStatus.STATUS_FROZEN.equals(agent.getStatus())) {
            return null;
        }
        String levelCode = agent.getAgentLevel();
        if (levelCode == null || "V0".equalsIgnoreCase(levelCode)) {
            // PRD §9.2 第 1 条：V1+ 才领团队代理奖
            return null;
        }

        TAgentLevel agentLevel = agentLevelMapper.selectByLevelCode(levelCode);
        if (agentLevel == null
                || agentLevel.getEnabled() == null || agentLevel.getEnabled() != 1
                || agentLevel.getMatchRate() == null
                || agentLevel.getMatchRate().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 2. 公式（PRD §9.3）：gross = weak × matchRate
        BigDecimal matchRate = agentLevel.getMatchRate();
        BigDecimal gross = weak.multiply(matchRate).setScale(8, RoundingMode.HALF_UP);
        if (gross.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 3. 通过 helper 发奖（健康出局截断 + 70/30 入账 + 累计判 expired 全在 helper 里）
        String idempotentKey = bizDate + ":team:" + userId;
        BigDecimal credited = rewardHelper.awardDynamicReward(
                userId,
                TRewardLog.TYPE_TEAM,
                gross,
                null,                // sourceUserId 团队奖无单一来源
                weak,                // sourceAmount = 弱区业绩
                matchRate,
                levelCode,
                idempotentKey,
                settleLogId,
                bizDate,
                levelCode);

        // 4. 回写 t_binary_volume_daily 快照（便于审计；不影响幂等）
        if (volumeDailyRowId != null) {
            TBinaryVolumeDaily upd = new TBinaryVolumeDaily();
            upd.setId(volumeDailyRowId);
            upd.setWeakVolume(weak);
            upd.setMatchedAmountUsdt(gross);
            upd.setSettleLogId(settleLogId);
            binaryVolumeDailyMapper.updateById(upd);
        }

        return credited;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
