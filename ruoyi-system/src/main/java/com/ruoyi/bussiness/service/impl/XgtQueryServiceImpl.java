package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TXgtBalance;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.vo.MyXgtLocksVO;
import com.ruoyi.bussiness.domain.vo.XgtLockVO;
import com.ruoyi.bussiness.mapper.TXgtBalanceMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.service.IXgtQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * XGT 查询实现。
 *
 * @date 2026-05-09
 */
@Service
@Slf4j
public class XgtQueryServiceImpl implements IXgtQueryService {

    @Resource
    private TXgtBalanceMapper xgtBalanceMapper;

    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;

    @Override
    public MyXgtLocksVO myLocks(Long userId) {
        MyXgtLocksVO vo = new MyXgtLocksVO();
        BigDecimal locked = BigDecimal.ZERO;
        BigDecimal unlocked = BigDecimal.ZERO;

        TXgtBalance balance = userId == null ? null : xgtBalanceMapper.selectByUserId(userId);
        if (balance != null) {
            locked = balance.getBalanceLocked() == null ? BigDecimal.ZERO : balance.getBalanceLocked();
            unlocked = balance.getBalanceUnlocked() == null ? BigDecimal.ZERO : balance.getBalanceUnlocked();
        }
        vo.setBalanceLocked(locked);
        vo.setBalanceUnlocked(unlocked);
        vo.setTotalBalance(locked.add(unlocked));

        List<XgtLockVO> locks;
        if (userId == null) {
            locks = Collections.emptyList();
        } else {
            LambdaQueryWrapper<TXgtLockPlan> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TXgtLockPlan::getUserId, userId);
            wrapper.orderByDesc(TXgtLockPlan::getLockedAt).orderByDesc(TXgtLockPlan::getId);
            List<TXgtLockPlan> raw = xgtLockPlanMapper.selectList(wrapper);
            locks = raw == null ? Collections.<XgtLockVO>emptyList()
                    : raw.stream().map(XgtLockVO::from).collect(Collectors.toList());
        }
        vo.setLocks(locks == null ? new ArrayList<>() : locks);
        return vo;
    }
}
