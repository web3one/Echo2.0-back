package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.mapper.TDailyFeeSummaryMapper;
import com.ruoyi.bussiness.mapper.TGoldWithdrawOrderMapper;
import com.ruoyi.bussiness.service.IGoldWithdrawAdminService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service
public class GoldWithdrawAdminServiceImpl implements IGoldWithdrawAdminService {

    @Resource
    private TGoldWithdrawOrderMapper withdrawOrderMapper;
    @Resource
    private TDailyFeeSummaryMapper dailyFeeSummaryMapper;

    @Override
    public IPage<TGoldWithdrawOrder> pageWithdraws(Integer pageNum, Integer pageSize, TGoldWithdrawOrder query) {
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<TGoldWithdrawOrder> p = new Page<>(pn, ps);
        LambdaQueryWrapper<TGoldWithdrawOrder> qw = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) qw.eq(TGoldWithdrawOrder::getUserId, query.getUserId());
            if (query.getStatus() != null && !query.getStatus().isEmpty()) {
                qw.eq(TGoldWithdrawOrder::getStatus, query.getStatus());
            }
            if (query.getAssetType() != null && !query.getAssetType().isEmpty()) {
                qw.eq(TGoldWithdrawOrder::getAssetType, query.getAssetType());
            }
        }
        qw.orderByDesc(TGoldWithdrawOrder::getId);
        return withdrawOrderMapper.selectPage(p, qw);
    }

    @Override
    public TGoldWithdrawOrder getWithdrawById(Long id) {
        return withdrawOrderMapper.selectById(id);
    }

    @Override
    public List<TDailyFeeSummary> recentFeeSummaries(Integer days) {
        int d = days == null || days < 1 ? 30 : Math.min(days, 365);
        LocalDate from = LocalDate.now().minusDays(d);
        return dailyFeeSummaryMapper.selectList(
                new LambdaQueryWrapper<TDailyFeeSummary>()
                        .ge(TDailyFeeSummary::getBizDate, from)
                        .orderByDesc(TDailyFeeSummary::getBizDate));
    }

    @Override
    public TDailyFeeSummary getFeeSummaryByDate(LocalDate bizDate) {
        return dailyFeeSummaryMapper.selectByBizDate(bizDate);
    }
}
