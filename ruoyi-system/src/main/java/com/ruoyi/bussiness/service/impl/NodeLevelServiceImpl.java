package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.bussiness.domain.TNodeInstance;
import com.ruoyi.bussiness.domain.TNodeLevel;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.mapper.TNodeLevelMapper;
import com.ruoyi.bussiness.service.INodeLevelService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 矿机等级配置服务实现
 */
@Service
@Slf4j
public class NodeLevelServiceImpl implements INodeLevelService {

    @Resource
    private TNodeLevelMapper nodeLevelMapper;

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Override
    public List<TNodeLevel> listAll() {
        return nodeLevelMapper.selectList(
                new LambdaQueryWrapper<TNodeLevel>().orderByAsc(TNodeLevel::getSort));
    }

    @Override
    public TNodeLevel getById(Long id) {
        return nodeLevelMapper.selectById(id);
    }

    @Override
    public TNodeLevel getByLevelCode(String levelCode) {
        return nodeLevelMapper.selectByLevelCode(levelCode);
    }

    @Override
    public int insert(TNodeLevel record) {
        validate(record);
        TNodeLevel exists = nodeLevelMapper.selectByLevelCode(record.getLevelCode());
        if (exists != null) {
            throw new ServiceException("level_code 已存在: " + record.getLevelCode());
        }
        return nodeLevelMapper.insert(record);
    }

    @Override
    public int update(TNodeLevel record) {
        if (record.getId() == null) {
            throw new ServiceException("id 不能为空");
        }
        // 不允许通过 update 改 level_code（业务标识，避免历史矿机引用错乱）
        TNodeLevel old = nodeLevelMapper.selectById(record.getId());
        if (old == null) {
            throw new ServiceException("等级不存在: " + record.getId());
        }
        if (StrUtil.isNotBlank(record.getLevelCode())
                && !record.getLevelCode().equals(old.getLevelCode())) {
            throw new ServiceException("level_code 不允许修改（业务标识）");
        }
        record.setLevelCode(old.getLevelCode());
        validate(record);
        return nodeLevelMapper.updateById(record);
    }

    @Override
    public int toggleEnabled(Long id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new ServiceException("enabled 必须为 0 或 1");
        }
        return nodeLevelMapper.update(null,
                new LambdaUpdateWrapper<TNodeLevel>()
                        .eq(TNodeLevel::getId, id)
                        .set(TNodeLevel::getEnabled, enabled));
    }

    @Override
    public int deleteById(Long id) {
        TNodeLevel level = nodeLevelMapper.selectById(id);
        if (level == null) {
            throw new ServiceException("等级不存在: " + id);
        }
        // 检查是否有 t_node_instance 引用
        Integer count = nodeInstanceMapper.selectCount(
                new LambdaQueryWrapper<TNodeInstance>().eq(TNodeInstance::getLevelId, id));
        if (count != null && count > 0) {
            throw new ServiceException("已有 " + count + " 台矿机引用此等级，不允许删除");
        }
        return nodeLevelMapper.deleteById(id);
    }

    private void validate(TNodeLevel record) {
        if (StrUtil.isBlank(record.getLevelCode())) {
            throw new ServiceException("level_code 不能为空");
        }
        if (record.getPriceUsdt() == null || record.getPriceUsdt().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("price_usdt 必须 > 0");
        }
        if (record.getDailyYieldRate() == null || record.getDailyYieldRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("daily_yield_rate 必须 >= 0");
        }
        if (record.getExitMultiplier() == null || record.getExitMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("exit_multiplier 必须 > 0");
        }
    }
}
