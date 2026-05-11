package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.ruoyi.bussiness.domain.TAgentApplication;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.TAgentStatus;
import com.ruoyi.bussiness.domain.TBinaryVolumeTotal;
import com.ruoyi.bussiness.domain.dto.AgencyApplyDTO;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;
import com.ruoyi.bussiness.domain.vo.MyAgentVO;
import com.ruoyi.bussiness.mapper.TAgentApplicationMapper;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.mapper.TAgentStatusMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeTotalMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.service.IAgencyApplyService;
import com.ruoyi.bussiness.service.IAgentStatusService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 代理升级申请服务实现（H5 通道 1）。
 *
 * 决策：min_direct_referral_count 按 PRD 原义"直推 V1+ 数"计算
 * （JOIN t_agent_status WHERE agent_level <> 'V0' AND status='active'），
 * 不是总直推数。
 *
 * @date 2026-05-11
 */
@Service
@Slf4j
public class AgencyApplyServiceImpl implements IAgencyApplyService {

    /** 等级排序（rank 比较"目标必须高于当前"） */
    private static final List<String> LEVEL_ORDER =
            Arrays.asList("V0", "V1", "V2", "V3", "V4", "V5");

    @Resource
    private TAgentApplicationMapper applicationMapper;

    @Resource
    private TAgentStatusMapper agentStatusMapper;

    @Resource
    private TAgentLevelMapper agentLevelMapper;

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Resource
    private TBinaryVolumeTotalMapper binaryVolumeTotalMapper;

    @Resource
    private IAgentStatusService agentStatusService;

    @Override
    public MyAgentVO getMyAgent(Long userId) {
        if (userId == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        MyAgentVO vo = new MyAgentVO();

        // 1. 当前代理状态（无则按 V0 active 默认值返回，不写库）
        TAgentStatus status = agentStatusMapper.selectByUserId(userId);
        String curLevel = status != null && StrUtil.isNotBlank(status.getAgentLevel())
                ? status.getAgentLevel() : TAgentStatus.LEVEL_V0;
        String curStatus = status != null && StrUtil.isNotBlank(status.getStatus())
                ? status.getStatus() : TAgentStatus.STATUS_ACTIVE;
        vo.setCurrentLevel(curLevel);
        vo.setAgentStatus(curStatus);

        TAgentLevel curLevelCfg = agentLevelMapper.selectByLevelCode(curLevel);
        vo.setMatchRate(nz(curLevelCfg != null ? curLevelCfg.getMatchRate() : null));
        vo.setGlobalDividendRate(nz(curLevelCfg != null ? curLevelCfg.getGlobalDividendRate() : null));

        // 2. 当前真值快照（4 个升级条件实际值）
        BigDecimal activeValue = nz(nodeInstanceMapper.sumActiveValueByUserId(userId));
        int directV1Plus = applicationMapper.countDirectReferralAgentV1Plus(userId);
        TBinaryVolumeTotal total = binaryVolumeTotalMapper.selectByUserId(userId);
        BigDecimal leftTotal = total != null ? nz(total.getLeftVolumeTotal()) : BigDecimal.ZERO;
        BigDecimal rightTotal = total != null ? nz(total.getRightVolumeTotal()) : BigDecimal.ZERO;

        vo.setActiveNodeValueTotal(activeValue);
        vo.setDirectReferralAgentV1Plus(directV1Plus);
        vo.setLeftVolumeTotal(leftTotal);
        vo.setRightVolumeTotal(rightTotal);

        // 3. pending 申请状态（hasPendingApplication + pendingTargetLevel/applicationId）
        TAgentApplication pending = applicationMapper.selectOne(new LambdaQueryWrapper<TAgentApplication>()
                .eq(TAgentApplication::getUserId, userId)
                .eq(TAgentApplication::getStatus, TAgentApplication.STATUS_PENDING)
                .orderByDesc(TAgentApplication::getCreateTime)
                .last("LIMIT 1"));
        vo.setHasPendingApplication(pending != null);
        vo.setPendingTargetLevel(pending != null ? pending.getTargetLevel() : null);
        vo.setPendingApplicationId(pending != null ? pending.getId() : null);

        // 4. 升级条件矩阵（V1-V5 中 enabled=1 且 rank > 当前 rank）
        int curRank = rankOf(curLevel);
        List<MyAgentVO.UpgradeOption> options = new ArrayList<>();
        for (String tgt : Arrays.asList("V1", "V2", "V3", "V4", "V5")) {
            if (rankOf(tgt) <= curRank) continue;
            TAgentLevel cfg = agentLevelMapper.selectByLevelCode(tgt);
            if (cfg == null || cfg.getEnabled() == null || cfg.getEnabled() != 1) continue;

            MyAgentVO.UpgradeOption opt = new MyAgentVO.UpgradeOption();
            opt.setTargetLevel(tgt);
            opt.setNameEn(cfg.getNameEn());
            opt.setNameZh(cfg.getNameZh());
            opt.setMatchRate(nz(cfg.getMatchRate()));
            opt.setGlobalDividendRate(nz(cfg.getGlobalDividendRate()));
            opt.setMinerValue(buildCondition(activeValue, nz(cfg.getMinActiveNodeValueUsdt())));
            opt.setDirectReferral(buildCondition(BigDecimal.valueOf(directV1Plus),
                    BigDecimal.valueOf(cfg.getMinDirectReferralCount() == null ? 0 : cfg.getMinDirectReferralCount())));
            opt.setLeftVolume(buildCondition(leftTotal, nz(cfg.getMinLeftVolumeTotal())));
            opt.setRightVolume(buildCondition(rightTotal, nz(cfg.getMinRightVolumeTotal())));
            opt.setEligible(opt.getMinerValue().getMet()
                    && opt.getDirectReferral().getMet()
                    && opt.getLeftVolume().getMet()
                    && opt.getRightVolume().getMet());
            options.add(opt);
        }
        vo.setUpgradeOptions(options);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAgentApplication submitApplication(Long userId, AgencyApplyDTO dto) {
        if (userId == null) {
            throw new ServiceException(MessageUtils.message("c2c.user.not_found"));
        }
        if (dto == null || StrUtil.isBlank(dto.getTargetLevel())) {
            throw new ServiceException(MessageUtils.message("agency.apply.target.invalid"));
        }
        String targetLevel = dto.getTargetLevel().trim().toUpperCase();

        // 1. targetLevel ∈ V1-V5 + enabled=1
        if (!Arrays.asList("V1", "V2", "V3", "V4", "V5").contains(targetLevel)) {
            throw new ServiceException(MessageUtils.message("agency.apply.target.invalid"));
        }
        TAgentLevel targetCfg = agentLevelMapper.selectByLevelCode(targetLevel);
        if (targetCfg == null || targetCfg.getEnabled() == null || targetCfg.getEnabled() != 1) {
            throw new ServiceException(MessageUtils.message("agency.apply.target.invalid"));
        }

        // 2. 当前等级 rank < target rank
        TAgentStatus status = agentStatusMapper.selectByUserId(userId);
        String curLevel = status != null && StrUtil.isNotBlank(status.getAgentLevel())
                ? status.getAgentLevel() : TAgentStatus.LEVEL_V0;
        if (rankOf(curLevel) >= rankOf(targetLevel)) {
            throw new ServiceException(MessageUtils.message("agency.apply.target.not_higher"));
        }

        // 3. 同用户无 pending（DB 层无 UK，并发极小概率下 admin 详情接口能看到双 pending；
        // 一旦审过其中一条，另一条 admin 可拒绝即可，业务可接受）
        int pendingCount = applicationMapper.countPendingByUserId(userId);
        if (pendingCount > 0) {
            throw new ServiceException(MessageUtils.message("agency.apply.duplicate_pending"));
        }

        // 4. SUM(active.priceUsdt) ≥ min_active_node_value_usdt
        BigDecimal activeValue = nz(nodeInstanceMapper.sumActiveValueByUserId(userId));
        BigDecimal minMinerValue = nz(targetCfg.getMinActiveNodeValueUsdt());
        if (activeValue.compareTo(minMinerValue) < 0) {
            throw new ServiceException(MessageUtils.message("agency.apply.miner.insufficient"));
        }

        // 5. 直推 V1+ 数 ≥ min_direct_referral_count
        int directV1Plus = applicationMapper.countDirectReferralAgentV1Plus(userId);
        int minDirect = targetCfg.getMinDirectReferralCount() == null ? 0 : targetCfg.getMinDirectReferralCount();
        if (directV1Plus < minDirect) {
            throw new ServiceException(MessageUtils.message("agency.apply.referral.insufficient"));
        }

        // 6. left_total ≥ min_left && right_total ≥ min_right
        TBinaryVolumeTotal total = binaryVolumeTotalMapper.selectByUserId(userId);
        BigDecimal leftTotal = total != null ? nz(total.getLeftVolumeTotal()) : BigDecimal.ZERO;
        BigDecimal rightTotal = total != null ? nz(total.getRightVolumeTotal()) : BigDecimal.ZERO;
        if (leftTotal.compareTo(nz(targetCfg.getMinLeftVolumeTotal())) < 0
                || rightTotal.compareTo(nz(targetCfg.getMinRightVolumeTotal())) < 0) {
            throw new ServiceException(MessageUtils.message("agency.apply.volume.insufficient"));
        }

        // 7. 写库（含 4 个 snap 字段）
        TAgentApplication app = new TAgentApplication();
        app.setUserId(userId);
        app.setFromLevel(curLevel);
        app.setTargetLevel(targetLevel);
        app.setReasonUser(StrUtil.isNotBlank(dto.getReasonUser()) ? dto.getReasonUser().trim() : null);
        if (dto.getProofUrls() != null && !dto.getProofUrls().isEmpty()) {
            app.setProofUrls(JSON.toJSONString(dto.getProofUrls()));
        }
        app.setDirectReferralCountSnap(directV1Plus);
        app.setLeftVolumeTotalSnap(leftTotal);
        app.setRightVolumeTotalSnap(rightTotal);
        app.setActiveNodeValueSnap(activeValue);
        app.setStatus(TAgentApplication.STATUS_PENDING);
        applicationMapper.insert(app);
        log.info("agency apply submitted: userId={} from={} target={} appId={}",
                userId, curLevel, targetLevel, app.getId());
        return app;
    }

    @Override
    public List<AgentApplicationVO> listMyApplications(Long userId) {
        if (userId == null) return new ArrayList<>();
        List<TAgentApplication> rows = applicationMapper.selectByUserId(userId);
        List<AgentApplicationVO> list = new ArrayList<>(rows.size());
        for (TAgentApplication app : rows) {
            list.add(toMyHistoryVO(app));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAgentApplication cancelMyApplication(Long userId, Long applicationId) {
        if (userId == null || applicationId == null) {
            throw new ServiceException(MessageUtils.message("agency.cancel.not_pending"));
        }
        TAgentApplication app = applicationMapper.selectById(applicationId);
        if (app == null || !userId.equals(app.getUserId())
                || !TAgentApplication.STATUS_PENDING.equals(app.getStatus())) {
            throw new ServiceException(MessageUtils.message("agency.cancel.not_pending"));
        }
        app.setStatus(TAgentApplication.STATUS_CANCELLED);
        app.setReviewAt(new Date());
        applicationMapper.updateById(app);
        log.info("agency apply cancelled by user: userId={} appId={}", userId, applicationId);
        return app;
    }

    // ============================================================
    // helpers
    // ============================================================

    private MyAgentVO.Condition buildCondition(BigDecimal actual, BigDecimal target) {
        MyAgentVO.Condition c = new MyAgentVO.Condition();
        c.setActual(nz(actual));
        c.setTarget(nz(target));
        c.setMet(nz(actual).compareTo(nz(target)) >= 0);
        return c;
    }

    private AgentApplicationVO toMyHistoryVO(TAgentApplication app) {
        AgentApplicationVO vo = new AgentApplicationVO();
        vo.setId(app.getId());
        vo.setUserId(app.getUserId());
        vo.setFromLevel(app.getFromLevel());
        vo.setTargetLevel(app.getTargetLevel());
        vo.setReasonUser(app.getReasonUser());
        if (StrUtil.isNotBlank(app.getProofUrls())) {
            try {
                JSONArray arr = JSON.parseArray(app.getProofUrls());
                List<String> list = new ArrayList<>(arr.size());
                for (int i = 0; i < arr.size(); i++) list.add(arr.getString(i));
                vo.setProofUrls(list);
            } catch (Exception ignore) { /* 兼容历史脏数据 */ }
        }
        vo.setDirectReferralCountSnap(app.getDirectReferralCountSnap());
        vo.setLeftVolumeTotalSnap(app.getLeftVolumeTotalSnap());
        vo.setRightVolumeTotalSnap(app.getRightVolumeTotalSnap());
        vo.setActiveNodeValueSnap(app.getActiveNodeValueSnap());
        vo.setStatus(app.getStatus());
        vo.setReviewAdminId(app.getReviewAdminId());
        vo.setReviewAt(app.getReviewAt());
        vo.setReviewRemark(app.getReviewRemark());
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        return vo;
    }

    private static int rankOf(String level) {
        if (level == null) return 0;
        int idx = LEVEL_ORDER.indexOf(level);
        return idx < 0 ? 0 : idx;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
