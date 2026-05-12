package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.mapper.TGoldWalletLogMapper;
import com.ruoyi.bussiness.mapper.TGoldWalletMapper;
import com.ruoyi.bussiness.service.IGoldWalletAdminService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class GoldWalletAdminServiceImpl implements IGoldWalletAdminService {

    @Resource
    private TGoldWalletMapper goldWalletMapper;
    @Resource
    private TGoldWalletLogMapper goldWalletLogMapper;

    @Override
    public IPage<TGoldWallet> pageWallets(Integer pageNum, Integer pageSize, TGoldWallet query) {
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<TGoldWallet> p = new Page<>(pn, ps);
        LambdaQueryWrapper<TGoldWallet> qw = new LambdaQueryWrapper<>();
        if (query != null && query.getUserId() != null) {
            qw.eq(TGoldWallet::getUserId, query.getUserId());
        }
        qw.orderByDesc(TGoldWallet::getUsdtBalance);
        // MP 3.4.1 PaginationInnerInterceptor BUG 规避：手动 selectCount + selectList(LIMIT)
        Integer total = goldWalletMapper.selectCount(qw);
        long totalLong = total == null ? 0L : total.longValue();
        p.setTotal(totalLong);
        if (totalLong > 0) {
            qw.last("LIMIT " + ((pn - 1) * ps) + ", " + ps);
            p.setRecords(goldWalletMapper.selectList(qw));
        }
        return p;
    }

    @Override
    public TGoldWallet getByUserId(Long userId) {
        return goldWalletMapper.selectByUserId(userId);
    }

    @Override
    public IPage<TGoldWalletLog> pageWalletLogs(Integer pageNum, Integer pageSize, TGoldWalletLog query) {
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<TGoldWalletLog> p = new Page<>(pn, ps);
        LambdaQueryWrapper<TGoldWalletLog> qw = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) qw.eq(TGoldWalletLog::getUserId, query.getUserId());
            if (query.getChangeType() != null && !query.getChangeType().isEmpty()) {
                qw.eq(TGoldWalletLog::getChangeType, query.getChangeType());
            }
            if (query.getBizRefType() != null && !query.getBizRefType().isEmpty()) {
                qw.eq(TGoldWalletLog::getBizRefType, query.getBizRefType());
            }
        }
        qw.orderByDesc(TGoldWalletLog::getId);
        // MP 3.4.1 PaginationInnerInterceptor BUG 规避：手动 selectCount + selectList(LIMIT)
        Integer total = goldWalletLogMapper.selectCount(qw);
        long totalLong = total == null ? 0L : total.longValue();
        p.setTotal(totalLong);
        if (totalLong > 0) {
            qw.last("LIMIT " + ((pn - 1) * ps) + ", " + ps);
            p.setRecords(goldWalletLogMapper.selectList(qw));
        }
        return p;
    }
}
