package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TAgentApplication;
import com.ruoyi.bussiness.domain.TAgentLevel;
import com.ruoyi.bussiness.domain.TAgentLevelChangeLog;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TBinaryVolumeTotal;
import com.ruoyi.bussiness.domain.vo.AgentApplicationVO;
import com.ruoyi.bussiness.mapper.TAgentApplicationMapper;
import com.ruoyi.bussiness.mapper.TAgentLevelMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeTotalMapper;
import com.ruoyi.bussiness.mapper.TNodeInstanceMapper;
import com.ruoyi.bussiness.service.IAgencyReviewService;
import com.ruoyi.bussiness.service.IAgentStatusService;
import com.ruoyi.bussiness.service.ITAppUserService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 代理申请审核服务实现（admin 通道）。
 *
 * @date 2026-05-11
 */
@Service
@Slf4j
public class AgencyReviewServiceImpl implements IAgencyReviewService {

    @Resource
    private TAgentApplicationMapper applicationMapper;

    @Resource
    private TAgentLevelMapper agentLevelMapper;

    @Resource
    private TBinaryVolumeTotalMapper binaryVolumeTotalMapper;

    @Resource
    private TNodeInstanceMapper nodeInstanceMapper;

    @Resource
    private IAgentStatusService agentStatusService;

    @Resource
    private ITAppUserService appUserService;

    @Override
    public IPage<AgentApplicationVO> pageList(int pageNum, int pageSize, TAgentApplication query) {
        LambdaQueryWrapper<TAgentApplication> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(TAgentApplication::getUserId, query.getUserId());
            }
            if (StrUtil.isNotBlank(query.getStatus())) {
                wrapper.eq(TAgentApplication::getStatus, query.getStatus());
            }
            if (StrUtil.isNotBlank(query.getTargetLevel())) {
                wrapper.eq(TAgentApplication::getTargetLevel, query.getTargetLevel());
            }
        }
        wrapper.orderByDesc(TAgentApplication::getCreateTime);
        // MP 3.4.1 PaginationInnerInterceptor BUG 规避：手动 selectCount + selectList(LIMIT)
        Page<TAgentApplication> raw = new Page<>(pageNum, pageSize);
        Integer total = applicationMapper.selectCount(wrapper);
        long totalLong = total == null ? 0L : total.longValue();
        raw.setTotal(totalLong);
        if (totalLong > 0) {
            wrapper.last("LIMIT " + ((long)(pageNum - 1) * pageSize) + ", " + pageSize);
            raw.setRecords(applicationMapper.selectList(wrapper));
        }
        IPage<TAgentApplication> page = raw;
        Page<AgentApplicationVO> ret = new Page<>(pageNum, pageSize);
        ret.setTotal(page.getTotal());
        List<AgentApplicationVO> records = new ArrayList<>(page.getRecords().size());
        for (TAgentApplication app : page.getRecords()) {
            records.add(toListVO(app));
        }
        ret.setRecords(records);
        return ret;
    }

    @Override
    public AgentApplicationVO getDetail(Long id) {
        TAgentApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new ServiceException("申请记录不存在");
        }
        AgentApplicationVO vo = toListVO(app);

        // 当前真值（实时查询）
        BigDecimal activeValue = nz(nodeInstanceMapper.sumActiveValueByUserId(app.getUserId()));
        int directV1Plus = applicationMapper.countDirectReferralAgentV1Plus(app.getUserId());
        TBinaryVolumeTotal total = binaryVolumeTotalMapper.selectByUserId(app.getUserId());
        BigDecimal leftTotal = total != null ? nz(total.getLeftVolumeTotal()) : BigDecimal.ZERO;
        BigDecimal rightTotal = total != null ? nz(total.getRightVolumeTotal()) : BigDecimal.ZERO;
        vo.setActiveNodeValueCurrent(activeValue);
        vo.setDirectReferralCountCurrent(directV1Plus);
        vo.setLeftVolumeTotalCurrent(leftTotal);
        vo.setRightVolumeTotalCurrent(rightTotal);

        // 目标等级当前配置
        TAgentLevel cfg = agentLevelMapper.selectByLevelCode(app.getTargetLevel());
        if (cfg != null) {
            vo.setMinActiveNodeValue(nz(cfg.getMinActiveNodeValueUsdt()));
            vo.setMinDirectReferralCount(cfg.getMinDirectReferralCount() == null ? 0 : cfg.getMinDirectReferralCount());
            vo.setMinLeftVolumeTotal(nz(cfg.getMinLeftVolumeTotal()));
            vo.setMinRightVolumeTotal(nz(cfg.getMinRightVolumeTotal()));
            boolean eligible = activeValue.compareTo(nz(cfg.getMinActiveNodeValueUsdt())) >= 0
                    && directV1Plus >= (cfg.getMinDirectReferralCount() == null ? 0 : cfg.getMinDirectReferralCount())
                    && leftTotal.compareTo(nz(cfg.getMinLeftVolumeTotal())) >= 0
                    && rightTotal.compareTo(nz(cfg.getMinRightVolumeTotal())) >= 0;
            vo.setEligibleNow(eligible);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAgentApplication approve(Long id, Long reviewAdminId, String reviewRemark) {
        if (StrUtil.isBlank(reviewRemark)) {
            throw new ServiceException("审核备注必填");
        }
        TAgentApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new ServiceException("申请记录不存在");
        }
        if (!TAgentApplication.STATUS_PENDING.equals(app.getStatus())) {
            throw new ServiceException("仅待审核状态的申请可通过");
        }

        // 调代理状态服务改等级（统一审计走 t_agent_level_change_log source=user_apply）
        agentStatusService.changeAgentLevel(
                app.getUserId(),
                app.getTargetLevel(),
                TAgentLevelChangeLog.SOURCE_USER_APPLY,
                app.getId(),
                reviewAdminId,
                reviewRemark);

        // UPDATE 本表
        app.setStatus(TAgentApplication.STATUS_APPROVED);
        app.setReviewAdminId(reviewAdminId);
        app.setReviewAt(new Date());
        app.setReviewRemark(reviewRemark);
        applicationMapper.updateById(app);
        log.info("agency apply approved: appId={} userId={} -> {} adminId={}",
                id, app.getUserId(), app.getTargetLevel(), reviewAdminId);
        return app;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAgentApplication reject(Long id, Long reviewAdminId, String reviewRemark) {
        if (StrUtil.isBlank(reviewRemark)) {
            throw new ServiceException("拒绝原因必填");
        }
        TAgentApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new ServiceException("申请记录不存在");
        }
        if (!TAgentApplication.STATUS_PENDING.equals(app.getStatus())) {
            throw new ServiceException("仅待审核状态的申请可拒绝");
        }
        app.setStatus(TAgentApplication.STATUS_REJECTED);
        app.setReviewAdminId(reviewAdminId);
        app.setReviewAt(new Date());
        app.setReviewRemark(reviewRemark);
        applicationMapper.updateById(app);
        log.info("agency apply rejected: appId={} userId={} adminId={}",
                id, app.getUserId(), reviewAdminId);
        return app;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 转 VO（含用户登录名 JOIN，admin 列表/详情通用） */
    private AgentApplicationVO toListVO(TAgentApplication app) {
        AgentApplicationVO vo = new AgentApplicationVO();
        vo.setId(app.getId());
        vo.setUserId(app.getUserId());
        TAppUser u = appUserService.selectTAppUserByUserId(app.getUserId());
        vo.setUserName(u != null ? u.getLoginName() : null);
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
