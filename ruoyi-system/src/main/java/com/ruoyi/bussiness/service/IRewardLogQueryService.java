package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.dto.RewardQueryDTO;
import com.ruoyi.bussiness.domain.vo.RewardEntryVO;

/**
 * 奖励流水查询（用户端，PRD §17 GET /rewards/daily）。
 *
 * @date 2026-05-09
 */
public interface IRewardLogQueryService {

    /**
     * 分页查询当前用户的奖励流水。createTime 倒序。
     */
    IPage<RewardEntryVO> pageMyRewards(Long userId, RewardQueryDTO query);
}
