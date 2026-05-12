package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TBinaryTree;
import com.ruoyi.bussiness.domain.TBinaryVolumeDaily;
import com.ruoyi.bussiness.domain.TBinaryVolumeTotal;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.vo.BinaryTeamVO;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TBinaryTreeMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeDailyMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeTotalMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.service.IBinaryTeamQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * 双轨团队查询实现（PRD §17.4）
 *
 * 数据来源：
 * - t_binary_tree 双轨下线计数（leftCount / rightCount）
 * - t_binary_volume_daily 当日业绩（biz_date = 今日 UTC）
 * - t_binary_volume_total 累计业绩 + 直推数
 * - t_agent_status 当前代理等级 + 冻结状态
 * - t_agent_level 代理等级配置（matchRate / globalDividendRate / 升级条件）
 * - t_node_instance active 矿机 → 当前权益矿机（最高级 + 同级最早）
 *
 * @date 2026-05-09
 */
@Service
@Slf4j
public class BinaryTeamQueryServiceImpl implements IBinaryTeamQueryService {

    @Resource
    private TBinaryTreeMapper binaryTreeMapper;
    @Resource
    private TBinaryVolumeDailyMapper binaryVolumeDailyMapper;
    @Resource
    private TBinaryVolumeTotalMapper binaryVolumeTotalMapper;
    @Resource
    private TAgentStatusMapper agentStatusMapper;
    @Resource
    private TAgentLevelMapper agentLevelMapper;
    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;
    @Resource
    private DynamicRewardHelper rewardHelper;

    @Override
    public BinaryTeamVO queryMyTeam(Long userId) {
        BinaryTeamVO vo = new BinaryTeamVO();
        if (userId == null) return vo;

        // 1. 双轨下线计数
        TBinaryTree node = binaryTreeMapper.selectByUserId(userId);
        vo.setLeftCount(node != null && node.getLeftCount() != null ? node.getLeftCount() : 0);
        vo.setRightCount(node != null && node.getRightCount() != null ? node.getRightCount() : 0);

        // 2. 累计业绩 + 直推数
        TBinaryVolumeTotal total = binaryVolumeTotalMapper.selectByUserId(userId);
        vo.setLeftTotalVolume(total != null && total.getLeftVolumeTotal() != null
                ? total.getLeftVolumeTotal() : BigDecimal.ZERO);
        vo.setRightTotalVolume(total != null && total.getRightVolumeTotal() != null
                ? total.getRightVolumeTotal() : BigDecimal.ZERO);
        vo.setDirectReferralCount(total != null && total.getDirectReferralCount() != null
                ? total.getDirectReferralCount() : 0);

        // 3. 当日业绩（biz_date 是 MySQL DATE，使用 java.sql.Date 避免时区转换成 08:00:00 后精确匹配失败）
        Date todayUtc = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC));
        TBinaryVolumeDaily daily = binaryVolumeDailyMapper.selectOne(
                new LambdaQueryWrapper<TBinaryVolumeDaily>()
                        .eq(TBinaryVolumeDaily::getUserId, userId)
                        .eq(TBinaryVolumeDaily::getBizDate, todayUtc)
                        .last("LIMIT 1"));
        BigDecimal leftToday = daily != null && daily.getLeftVolume() != null ? daily.getLeftVolume() : BigDecimal.ZERO;
        BigDecimal rightToday = daily != null && daily.getRightVolume() != null ? daily.getRightVolume() : BigDecimal.ZERO;
        vo.setLeftTodayVolume(leftToday);
        vo.setRightTodayVolume(rightToday);
        BigDecimal weak = leftToday.min(rightToday);
        vo.setWeakTodayVolume(weak);

        // 4. 代理状态 + 等级配置
        TAgentStatus status = agentStatusMapper.selectByUserId(userId);
        String levelCode = status != null ? status.getAgentLevel() : "V0";
        if (levelCode == null) levelCode = "V0";
        vo.setAgentLevel(levelCode);
        vo.setAgentStatus(status != null && status.getStatus() != null ? status.getStatus() : "active");

        TAgentLevel curLevel = agentLevelMapper.selectByLevelCode(levelCode);
        BigDecimal matchRate = curLevel != null && curLevel.getMatchRate() != null
                ? curLevel.getMatchRate() : BigDecimal.ZERO;
        vo.setMatchRate(matchRate);
        vo.setGlobalDividendRate(curLevel != null && curLevel.getGlobalDividendRate() != null
                ? curLevel.getGlobalDividendRate() : BigDecimal.ZERO);

        // 5. 当前权益矿机
        List<TNodeInstance> activeMiners = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<TNodeInstance>()
                        .eq(TNodeInstance::getUserId, userId)
                        .eq(TNodeInstance::getStatus, TNodeInstance.STATUS_ACTIVE));
        TNodeInstance current = rewardHelper.pickHighestActive(activeMiners);
        vo.setHasActiveMiner(current != null);
        vo.setCurrentMinerLevel(current != null ? current.getLevelCode() : null);
        vo.setTeamDailyCap(current != null && current.getTeamDailyCapUsdt() != null
                ? current.getTeamDailyCapUsdt() : BigDecimal.ZERO);

        // 6. 今日预计团队代理奖（信息展示，按 PRD §9.3 公式；用户决策不再受 §9.4 团队日封顶约束）
        BigDecimal est = weak.multiply(matchRate).setScale(8, RoundingMode.HALF_UP);
        vo.setEstimatedTodayReward(est);

        // 7. 升级目标（next level）
        fillNextLevelTarget(vo, levelCode, total, current);

        return vo;
    }

    /**
     * 填充升级目标。V0 → V1，V1 → V2，..., V5 → null。
     */
    private void fillNextLevelTarget(BinaryTeamVO vo, String currentLevel,
                                     TBinaryVolumeTotal total, TNodeInstance currentMiner) {
        String nextLevel = nextLevelOf(currentLevel);
        if (nextLevel == null) {
            vo.setNextLevel(null);
            vo.setNextLevelEligible(Boolean.FALSE);
            return;
        }
        TAgentLevel next = agentLevelMapper.selectByLevelCode(nextLevel);
        if (next == null || next.getEnabled() == null || next.getEnabled() != 1) {
            vo.setNextLevel(null);
            vo.setNextLevelEligible(Boolean.FALSE);
            return;
        }
        vo.setNextLevel(nextLevel);
        vo.setNextLevelMatchRate(next.getMatchRate());
        vo.setNextLevelMinDirectReferralCount(next.getMinDirectReferralCount());
        vo.setNextLevelMinLeftVolume(next.getMinLeftVolumeTotal());
        vo.setNextLevelMinRightVolume(next.getMinRightVolumeTotal());
        vo.setNextLevelMinActiveNodeValue(next.getMinActiveNodeValueUsdt());

        BigDecimal leftTotal = total != null && total.getLeftVolumeTotal() != null
                ? total.getLeftVolumeTotal() : BigDecimal.ZERO;
        BigDecimal rightTotal = total != null && total.getRightVolumeTotal() != null
                ? total.getRightVolumeTotal() : BigDecimal.ZERO;
        int directs = total != null && total.getDirectReferralCount() != null
                ? total.getDirectReferralCount() : 0;
        BigDecimal currentMinerPrice = currentMiner != null && currentMiner.getPriceUsdt() != null
                ? currentMiner.getPriceUsdt() : BigDecimal.ZERO;

        boolean eligible =
                leftTotal.compareTo(nz(next.getMinLeftVolumeTotal())) >= 0
                        && rightTotal.compareTo(nz(next.getMinRightVolumeTotal())) >= 0
                        && directs >= (next.getMinDirectReferralCount() == null ? 0 : next.getMinDirectReferralCount())
                        && currentMinerPrice.compareTo(nz(next.getMinActiveNodeValueUsdt())) >= 0;
        vo.setNextLevelEligible(eligible);
    }

    private static String nextLevelOf(String cur) {
        if (cur == null) return "V1";
        switch (cur) {
            case "V0": return "V1";
            case "V1": return "V2";
            case "V2": return "V3";
            case "V3": return "V4";
            case "V4": return "V5";
            default: return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
