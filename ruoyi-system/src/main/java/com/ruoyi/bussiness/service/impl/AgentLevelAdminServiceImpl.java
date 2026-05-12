package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.dto.AgentLevelUpdateDTO;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.service.IAgentLevelAdminService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 代理等级配置 admin 实现（PRD §15.3）。
 *
 * @date 2026-05-12
 */
@Service
@Slf4j
public class AgentLevelAdminServiceImpl implements IAgentLevelAdminService {

    @Resource
    private TAgentLevelMapper agentLevelMapper;

    @Override
    public List<TAgentLevel> listAll() {
        return agentLevelMapper.selectList(
                new LambdaQueryWrapper<TAgentLevel>().orderByAsc(TAgentLevel::getSort));
    }

    @Override
    public TAgentLevel getById(Long id) {
        TAgentLevel level = agentLevelMapper.selectById(id);
        if (level == null) {
            throw new ServiceException("等级配置不存在");
        }
        return level;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAgentLevel update(Long id, AgentLevelUpdateDTO dto, Long adminId) {
        TAgentLevel level = agentLevelMapper.selectById(id);
        if (level == null) {
            throw new ServiceException("等级配置不存在");
        }
        if (dto.getMatchRate() != null) {
            if (dto.getMatchRate().compareTo(BigDecimal.ZERO) < 0
                    || dto.getMatchRate().compareTo(BigDecimal.ONE) > 0) {
                throw new ServiceException("match_rate 必须在 [0, 1] 区间");
            }
            level.setMatchRate(dto.getMatchRate());
        }
        if (dto.getGlobalDividendRate() != null) {
            if (dto.getGlobalDividendRate().compareTo(BigDecimal.ZERO) < 0
                    || dto.getGlobalDividendRate().compareTo(BigDecimal.ONE) > 0) {
                throw new ServiceException("global_dividend_rate 必须在 [0, 1] 区间");
            }
            level.setGlobalDividendRate(dto.getGlobalDividendRate());
        }
        if (dto.getMinActiveNodeValueUsdt() != null) {
            if (dto.getMinActiveNodeValueUsdt().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("min_active_node_value_usdt 不能为负");
            }
            level.setMinActiveNodeValueUsdt(dto.getMinActiveNodeValueUsdt());
        }
        if (dto.getMinDirectReferralCount() != null) {
            if (dto.getMinDirectReferralCount() < 0) {
                throw new ServiceException("min_direct_referral_count 不能为负");
            }
            level.setMinDirectReferralCount(dto.getMinDirectReferralCount());
        }
        if (dto.getMinLeftVolumeTotal() != null) {
            if (dto.getMinLeftVolumeTotal().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("min_left_volume_total 不能为负");
            }
            level.setMinLeftVolumeTotal(dto.getMinLeftVolumeTotal());
        }
        if (dto.getMinRightVolumeTotal() != null) {
            if (dto.getMinRightVolumeTotal().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("min_right_volume_total 不能为负");
            }
            level.setMinRightVolumeTotal(dto.getMinRightVolumeTotal());
        }
        if (dto.getNameEn() != null) {
            level.setNameEn(dto.getNameEn());
        }
        if (dto.getNameZh() != null) {
            level.setNameZh(dto.getNameZh());
        }
        if (dto.getEnabled() != null) {
            if (dto.getEnabled() != 0 && dto.getEnabled() != 1) {
                throw new ServiceException("enabled 仅允许 0 或 1");
            }
            level.setEnabled(dto.getEnabled());
        }
        if (dto.getSort() != null) {
            level.setSort(dto.getSort());
        }
        if (dto.getRemark() != null) {
            level.setRemark(dto.getRemark());
        }
        agentLevelMapper.updateById(level);
        log.info("[admin agent_level] update id={} levelCode={} adminId={}",
                id, level.getLevelCode(), adminId);
        return level;
    }
}
