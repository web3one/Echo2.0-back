package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TFounderPurchaseLog;
import com.ruoyi.bussiness.domain.TFounderSeat;
import com.ruoyi.bussiness.mapper.TFounderPurchaseLogMapper;
import com.ruoyi.bussiness.mapper.TFounderSeatMapper;
import com.ruoyi.bussiness.service.IFounderAdminService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 创世管理 admin 服务实现。
 *
 * @date 2026-05-11
 */
@Service
@Slf4j
public class FounderAdminServiceImpl implements IFounderAdminService {

    @Resource
    private TFounderSeatMapper seatMapper;

    @Resource
    private TFounderPurchaseLogMapper logMapper;

    @Override
    public List<TFounderSeat> listAllSeats() {
        return seatMapper.selectList(
                new LambdaQueryWrapper<TFounderSeat>().orderByAsc(TFounderSeat::getSeatNo));
    }

    @Override
    public TFounderSeat getSeatById(Long seatId) {
        return seatMapper.selectById(seatId);
    }

    @Override
    @Transactional
    public TFounderSeat freezeSeat(Long seatId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("冻结原因必填");
        }
        TFounderSeat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new ServiceException("席位不存在");
        }
        if (TFounderSeat.STATUS_FROZEN.equals(seat.getStatus())) {
            throw new ServiceException("席位已冻结");
        }
        seat.setStatus(TFounderSeat.STATUS_FROZEN);
        seat.setFrozenAt(new Date());
        seat.setFrozenReason(reason);
        seat.setFrozenByAdminId(operatorAdminId);
        seatMapper.updateById(seat);
        log.info("founder seat frozen: seatNo={} ownerUserId={} adminId={}",
                seat.getSeatNo(), seat.getOwnerUserId(), operatorAdminId);
        return seat;
    }

    @Override
    @Transactional
    public TFounderSeat unfreezeSeat(Long seatId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("解冻原因必填");
        }
        TFounderSeat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new ServiceException("席位不存在");
        }
        if (!TFounderSeat.STATUS_FROZEN.equals(seat.getStatus())) {
            throw new ServiceException("仅冻结状态的席位可解冻");
        }
        // 解冻还原：有主 → owned；无主 → available
        seat.setStatus(seat.getOwnerUserId() != null
                ? TFounderSeat.STATUS_OWNED : TFounderSeat.STATUS_AVAILABLE);
        seat.setFrozenAt(null);
        seat.setFrozenReason(null);
        seat.setFrozenByAdminId(null);
        seatMapper.updateById(seat);
        log.info("founder seat unfrozen: seatNo={} ownerUserId={} adminId={} reason={}",
                seat.getSeatNo(), seat.getOwnerUserId(), operatorAdminId, reason);
        return seat;
    }

    @Override
    @Transactional
    public TFounderSeat updateRemark(Long seatId, String remark) {
        TFounderSeat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new ServiceException("席位不存在");
        }
        seat.setRemark(remark);
        seatMapper.updateById(seat);
        return seat;
    }

    @Override
    public IPage<TFounderPurchaseLog> pageLogs(int pageNum, int pageSize, TFounderPurchaseLog query) {
        LambdaQueryWrapper<TFounderPurchaseLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(TFounderPurchaseLog::getUserId, query.getUserId());
            }
            if (query.getSeatNo() != null) {
                wrapper.eq(TFounderPurchaseLog::getSeatNo, query.getSeatNo());
            }
        }
        wrapper.orderByDesc(TFounderPurchaseLog::getCreateTime);
        // MP 3.4.1 PaginationInnerInterceptor BUG 规避：手动 selectCount + selectList(LIMIT)
        Page<TFounderPurchaseLog> p = new Page<>(pageNum, pageSize);
        Integer total = logMapper.selectCount(wrapper);
        long totalLong = total == null ? 0L : total.longValue();
        p.setTotal(totalLong);
        if (totalLong > 0) {
            wrapper.last("LIMIT " + ((long)(pageNum - 1) * pageSize) + ", " + pageSize);
            p.setRecords(logMapper.selectList(wrapper));
        }
        return p;
    }
}
