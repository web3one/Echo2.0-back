package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.vo.MyMinerVO;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.service.INodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 矿机查询实现。
 *
 * 排序与 isCurrentActive 标识在内存中计算（用户端单用户全表通常 < 50 行，
 * 无需上 SQL CASE WHEN 复杂排序）。
 *
 * @date 2026-05-09
 */
@Service
@Slf4j
public class NodeQueryServiceImpl implements INodeQueryService {

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Override
    public List<MyMinerVO> listMyMiners(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<TNodeInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TNodeInstance::getUserId, userId);
        List<TNodeInstance> list = nodeInstanceMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 当前有效权益矿机（PRD §22.6）：active + 等级最高 + 同级最早激活
        TNodeInstance current = pickCurrentActive(list);

        // 用户聚合（2026-05-09 修订）：active 矿机最高 priceUsdt × 3 = aggExitTarget；
        // active 矿机 accumulated_reward_usdt 之和 = aggAccumulated；
        // 同价矿机同时唯一，所以 max 直接取最高 active priceUsdt 即可。
        BigDecimal aggAccumulated = BigDecimal.ZERO;
        BigDecimal maxActivePrice = BigDecimal.ZERO;
        for (TNodeInstance inst : list) {
            if (!TNodeInstance.STATUS_ACTIVE.equals(inst.getStatus())) continue;
            aggAccumulated = aggAccumulated.add(nz(inst.getAccumulatedRewardUsdt()));
            BigDecimal p = nz(inst.getPriceUsdt());
            if (p.compareTo(maxActivePrice) > 0) maxActivePrice = p;
        }
        BigDecimal aggExitTarget = maxActivePrice.compareTo(BigDecimal.ZERO) > 0
                ? maxActivePrice.multiply(EXIT_MULTIPLIER).setScale(8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal aggRemaining = aggExitTarget.subtract(aggAccumulated);
        if (aggRemaining.compareTo(BigDecimal.ZERO) < 0) aggRemaining = BigDecimal.ZERO;

        List<MyMinerVO> result = new ArrayList<>(list.size());
        for (TNodeInstance inst : list) {
            MyMinerVO vo = MyMinerVO.from(inst);
            if (current != null && current.getId().equals(inst.getId())) {
                vo.setIsCurrentActive(Boolean.TRUE);
            }
            vo.setUserAggregateAccumulated(aggAccumulated);
            vo.setUserAggregateExitTarget(aggExitTarget);
            vo.setUserAggregateRemaining(aggRemaining);
            result.add(vo);
        }
        result.sort(displayOrder());
        return result;
    }

    private static final BigDecimal EXIT_MULTIPLIER = new BigDecimal("3");

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 选当前有效权益矿机：仅 active；levelCode 高者优先（L4>L3>L2>L1）；
     * 同级别看 activatedAt 最早一台。
     */
    private TNodeInstance pickCurrentActive(List<TNodeInstance> list) {
        TNodeInstance picked = null;
        int pickedRank = -1;
        for (TNodeInstance inst : list) {
            if (!TNodeInstance.STATUS_ACTIVE.equals(inst.getStatus())) {
                continue;
            }
            int rank = levelRank(inst.getLevelCode());
            if (rank < 0) {
                continue;
            }
            if (picked == null || rank > pickedRank
                    || (rank == pickedRank && earlier(inst.getActivatedAt(), picked.getActivatedAt()))) {
                picked = inst;
                pickedRank = rank;
            }
        }
        return picked;
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

    private static boolean earlier(java.util.Date a, java.util.Date b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.before(b);
    }

    /**
     * 列表展示排序：active 优先；同 status 按 level 高 → 低；同 level 按 activatedAt 旧 → 新。
     */
    private static Comparator<MyMinerVO> displayOrder() {
        return (a, b) -> {
            int sa = statusRank(a.getStatus());
            int sb = statusRank(b.getStatus());
            if (sa != sb) return Integer.compare(sa, sb);
            int la = levelRank(a.getLevel());
            int lb = levelRank(b.getLevel());
            if (la != lb) return Integer.compare(lb, la);
            java.util.Date pa = a.getPurchasedAt();
            java.util.Date pb = b.getPurchasedAt();
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1;
            if (pb == null) return -1;
            return pa.compareTo(pb);
        };
    }

    private static int statusRank(String status) {
        if (TNodeInstance.STATUS_ACTIVE.equals(status)) return 0;
        if (TNodeInstance.STATUS_FROZEN.equals(status)) return 1;
        if (TNodeInstance.STATUS_EXPIRED.equals(status)) return 2;
        return 3;
    }
}
