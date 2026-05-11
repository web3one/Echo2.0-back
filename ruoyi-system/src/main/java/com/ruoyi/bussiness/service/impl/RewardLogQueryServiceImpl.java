package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TRewardLog;
import com.ruoyi.bussiness.domain.dto.RewardQueryDTO;
import com.ruoyi.bussiness.domain.vo.RewardEntryVO;
import com.ruoyi.bussiness.mapper.TRewardLogMapper;
import com.ruoyi.bussiness.service.IRewardLogQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 奖励流水查询实现。
 *
 * @date 2026-05-09
 */
@Service
@Slf4j
public class RewardLogQueryServiceImpl implements IRewardLogQueryService {

    @Resource
    private TRewardLogMapper rewardLogMapper;

    @Override
    public IPage<RewardEntryVO> pageMyRewards(Long userId, RewardQueryDTO query) {
        RewardQueryDTO q = query == null ? new RewardQueryDTO() : query;

        LambdaQueryWrapper<TRewardLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TRewardLog::getUserId, userId);
        if (StrUtil.isNotBlank(q.getRewardType())) {
            wrapper.eq(TRewardLog::getRewardType, q.getRewardType());
        }
        if (q.getRelatedNodeInstanceId() != null) {
            wrapper.eq(TRewardLog::getRelatedNodeInstanceId, q.getRelatedNodeInstanceId());
        }
        if (q.getBizDateFrom() != null) {
            wrapper.ge(TRewardLog::getBizDate, q.getBizDateFrom());
        }
        if (q.getBizDateTo() != null) {
            wrapper.le(TRewardLog::getBizDate, q.getBizDateTo());
        }
        wrapper.orderByDesc(TRewardLog::getCreateTime).orderByDesc(TRewardLog::getId);

        Page<TRewardLog> page = new Page<>(q.safePageNum(), q.safePageSize());
        IPage<TRewardLog> raw = rewardLogMapper.selectPage(page, wrapper);

        return raw.convert(RewardEntryVO::from);
    }
}
