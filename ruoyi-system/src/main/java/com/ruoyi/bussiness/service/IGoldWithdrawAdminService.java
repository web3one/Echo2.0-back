package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.domain.TDailyFeeSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * 金矿提现单 admin Service + 每日手续费报表 Service。
 *
 * @date 2026-05-15
 */
public interface IGoldWithdrawAdminService {

    /** 分页搜提现单（userId / status / 创建时间） */
    IPage<TGoldWithdrawOrder> pageWithdraws(Integer pageNum, Integer pageSize, TGoldWithdrawOrder query);

    /** 单提现单详情 */
    TGoldWithdrawOrder getWithdrawById(Long id);

    /** 每日手续费报表（最近 N 天，倒序） */
    List<TDailyFeeSummary> recentFeeSummaries(Integer days);

    /** 单日报表详情 */
    TDailyFeeSummary getFeeSummaryByDate(LocalDate bizDate);
}
