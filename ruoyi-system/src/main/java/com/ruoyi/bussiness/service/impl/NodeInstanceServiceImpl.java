package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.service.INodeInstanceService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 矿机实例服务实现
 *
 * 注意：admin 不允许物理删除矿机实例（业务追溯需要）；只能冻结/解冻。
 */
@Service
@Slf4j
public class NodeInstanceServiceImpl implements INodeInstanceService {

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Override
    public IPage<TNodeInstance> page(int pageNum, int pageSize, TNodeInstance query) {
        LambdaQueryWrapper<TNodeInstance> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(TNodeInstance::getUserId, query.getUserId());
            }
            if (StrUtil.isNotBlank(query.getStatus())) {
                wrapper.eq(TNodeInstance::getStatus, query.getStatus());
            }
            if (StrUtil.isNotBlank(query.getLevelCode())) {
                wrapper.eq(TNodeInstance::getLevelCode, query.getLevelCode());
            }
        }
        wrapper.orderByDesc(TNodeInstance::getCreateTime);
        return nodeInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public TNodeInstance getById(Long id) {
        return nodeInstanceMapper.selectById(id);
    }

    @Override
    @Transactional
    public TNodeInstance freeze(Long instanceId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("冻结原因必填");
        }
        TNodeInstance inst = nodeInstanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new ServiceException("矿机不存在: " + instanceId);
        }
        if (TNodeInstance.STATUS_EXPIRED.equals(inst.getStatus())) {
            throw new ServiceException("矿机已出局，不可冻结");
        }
        if (TNodeInstance.STATUS_FROZEN.equals(inst.getStatus())) {
            return inst;
        }
        inst.setStatus(TNodeInstance.STATUS_FROZEN);
        inst.setFrozenAt(new Date());
        inst.setFrozenReason(reason);
        nodeInstanceMapper.updateById(inst);
        log.info("freeze node instance: id={} userId={} operator={} reason={}",
                instanceId, inst.getUserId(), operatorAdminId, reason);
        return inst;
    }

    @Override
    @Transactional
    public TNodeInstance unfreeze(Long instanceId, Long operatorAdminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("解冻原因必填");
        }
        TNodeInstance inst = nodeInstanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new ServiceException("矿机不存在: " + instanceId);
        }
        if (!TNodeInstance.STATUS_FROZEN.equals(inst.getStatus())) {
            throw new ServiceException("矿机当前状态非 frozen，不能解冻");
        }
        inst.setStatus(TNodeInstance.STATUS_ACTIVE);
        inst.setFrozenAt(null);
        inst.setFrozenReason(null);
        nodeInstanceMapper.updateById(inst);
        log.info("unfreeze node instance: id={} userId={} operator={} reason={}",
                instanceId, inst.getUserId(), operatorAdminId, reason);
        return inst;
    }
}
