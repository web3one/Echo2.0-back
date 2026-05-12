package com.ruoyi.bussiness.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TXgtBalance;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.TXgtLog;
import com.ruoyi.bussiness.domain.dto.XgtLockPlanCreateDTO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanAdminVO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanDetailVO;
import com.ruoyi.bussiness.mapper.TAppUserMapper;
import com.ruoyi.bussiness.mapper.TXgtBalanceMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.mapper.TXgtLogMapper;
import com.ruoyi.bussiness.service.IXgtLockAdminService;
import com.ruoyi.bussiness.service.IXgtLockReleaseSettleService;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin XGT 锁仓管理实现（PRD §15.5）。
 *
 * 限制：
 *   - create 的 sourceType 仅允许 team_advisor / private_sale / ecosystem_fund / partner，
 *     防止 admin 误创建已被自动路径占用的 static_reward / founder_seat / credit_unlock。
 *   - freeze 仅作用于 locked 状态；completed / frozen 已是终态/冻态。
 *   - unfreeze 仅作用于 frozen 状态；恢复到 locked，cron 下次扫到期会自动释放。
 *   - retry 仅作用于 locked 状态，调 releaseOnePlan；
 *     如果用户被 agent_status 冻结，releaseOnePlan 内部会跳过返回 null。
 *
 * @date 2026-05-12
 */
@Service
@Slf4j
public class XgtLockAdminServiceImpl implements IXgtLockAdminService {

    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int LOCK_DAYS = 30;
    private static final int EXPORT_CAP = 5000;

    private static final Set<String> ADMIN_CREATABLE_SOURCE_TYPES = new HashSet<>(Arrays.asList(
            TXgtLockPlan.SOURCE_TEAM_ADVISOR,
            TXgtLockPlan.SOURCE_PRIVATE_SALE,
            TXgtLockPlan.SOURCE_ECOSYSTEM_FUND,
            TXgtLockPlan.SOURCE_PARTNER
    ));

    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;

    @Resource
    private TXgtBalanceMapper xgtBalanceMapper;

    @Resource
    private TXgtLogMapper xgtLogMapper;

    @Resource
    private TAppUserMapper appUserMapper;

    @Resource
    private IXgtLockReleaseSettleService xgtLockReleaseSettleService;

    @Override
    public IPage<XgtLockPlanAdminVO> pageList(int pageNum, int pageSize,
                                              Long userId, String sourceType, String status,
                                              Date beginLockedAt, Date endLockedAt,
                                              Date beginReleaseAt, Date endReleaseAt) {
        LambdaQueryWrapper<TXgtLockPlan> wrapper = buildQueryWrapper(
                userId, sourceType, status, beginLockedAt, endLockedAt, beginReleaseAt, endReleaseAt);
        wrapper.orderByDesc(TXgtLockPlan::getId);

        // 手动分页规避 MP 3.4.1 PaginationInnerInterceptor 偶发 "SELECT COUNT()" BUG。
        Page<TXgtLockPlan> page = new Page<>(pageNum, pageSize);
        Integer total = xgtLockPlanMapper.selectCount(wrapper);
        long totalLong = total == null ? 0L : total.longValue();
        page.setTotal(totalLong);
        if (totalLong > 0) {
            wrapper.last("LIMIT " + ((long) (pageNum - 1) * pageSize) + ", " + pageSize);
            page.setRecords(xgtLockPlanMapper.selectList(wrapper));
        }
        IPage<XgtLockPlanAdminVO> voPage = page.convert(this::toVO);
        fillUserLoginName(voPage.getRecords());
        return voPage;
    }

    @Override
    public XgtLockPlanDetailVO getDetail(Long id) {
        TXgtLockPlan plan = xgtLockPlanMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException("锁仓计划不存在");
        }
        XgtLockPlanAdminVO vo = toVO(plan);
        fillUserLoginName(java.util.Collections.singletonList(vo));

        XgtLockPlanDetailVO detail = new XgtLockPlanDetailVO();
        detail.setPlan(vo);

        TXgtBalance bal = xgtBalanceMapper.selectByUserId(plan.getUserId());
        if (bal != null) {
            detail.setUserBalanceLocked(nz(bal.getBalanceLocked()));
            detail.setUserBalanceUnlocked(nz(bal.getBalanceUnlocked()));
        } else {
            detail.setUserBalanceLocked(BigDecimal.ZERO);
            detail.setUserBalanceUnlocked(BigDecimal.ZERO);
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TXgtLockPlan create(XgtLockPlanCreateDTO dto, Long adminId) {
        if (dto == null) {
            throw new ServiceException("参数为空");
        }
        if (dto.getUserId() == null) {
            throw new ServiceException("userId 必填");
        }
        if (StrUtil.isBlank(dto.getSourceType())
                || !ADMIN_CREATABLE_SOURCE_TYPES.contains(dto.getSourceType())) {
            throw new ServiceException("sourceType 仅允许：team_advisor / private_sale / ecosystem_fund / partner");
        }
        if (StrUtil.isBlank(dto.getSourceRefId())) {
            throw new ServiceException("sourceRefId 必填（用于幂等）");
        }
        if (dto.getAmountXgt() == null || dto.getAmountXgt().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("amountXgt 必须大于 0");
        }
        if (appUserMapper.selectById(dto.getUserId()) == null) {
            throw new ServiceException("用户不存在: " + dto.getUserId());
        }

        Date lockedAt = dto.getLockedAt() != null ? dto.getLockedAt() : new Date();
        Date releaseAt = new Date(lockedAt.getTime() + (long) LOCK_DAYS * ONE_DAY_MILLIS);

        TXgtLockPlan plan = new TXgtLockPlan();
        plan.setUserId(dto.getUserId());
        plan.setSourceType(dto.getSourceType());
        plan.setSourceRefId(dto.getSourceRefId());
        plan.setAmountXgt(dto.getAmountXgt());
        plan.setAmountUsdNominal(dto.getAmountUsdNominal());
        plan.setLockedAt(lockedAt);
        plan.setReleaseAt(releaseAt);
        plan.setStatus(TXgtLockPlan.STATUS_LOCKED);
        plan.setRemark(StrUtil.isBlank(dto.getRemark())
                ? ("admin#" + adminId + " manual create")
                : dto.getRemark());
        try {
            xgtLockPlanMapper.insert(plan);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("已存在相同 sourceType + sourceRefId 的锁仓计划");
        }

        // 更新 balance_locked，并写流水
        TXgtBalance bal = xgtBalanceMapper.selectByUserId(dto.getUserId());
        BigDecimal newLocked;
        BigDecimal currentUnlocked;
        if (bal == null) {
            bal = new TXgtBalance();
            bal.setUserId(dto.getUserId());
            bal.setBalanceLocked(dto.getAmountXgt());
            bal.setBalanceUnlocked(BigDecimal.ZERO);
            try {
                xgtBalanceMapper.insert(bal);
                newLocked = dto.getAmountXgt();
                currentUnlocked = BigDecimal.ZERO;
            } catch (DuplicateKeyException e) {
                bal = xgtBalanceMapper.selectByUserId(dto.getUserId());
                newLocked = nz(bal.getBalanceLocked()).add(dto.getAmountXgt());
                currentUnlocked = nz(bal.getBalanceUnlocked());
                bal.setBalanceLocked(newLocked);
                xgtBalanceMapper.updateById(bal);
            }
        } else {
            newLocked = nz(bal.getBalanceLocked()).add(dto.getAmountXgt());
            currentUnlocked = nz(bal.getBalanceUnlocked());
            bal.setBalanceLocked(newLocked);
            xgtBalanceMapper.updateById(bal);
        }

        TXgtLog xlog = new TXgtLog();
        xlog.setUserId(dto.getUserId());
        xlog.setChangeType(TXgtLog.CHANGE_LOCK);
        xlog.setAmountXgt(dto.getAmountXgt());
        xlog.setBalanceLockedAfter(newLocked);
        xlog.setBalanceUnlockedAfter(currentUnlocked);
        xlog.setRelatedLockPlanId(plan.getId());
        xlog.setRemark("admin#" + adminId + " create " + dto.getSourceType());
        xgtLogMapper.insert(xlog);

        log.info("[admin xgt_lock] create planId={} userId={} sourceType={} amount={} adminId={}",
                plan.getId(), dto.getUserId(), dto.getSourceType(), dto.getAmountXgt(), adminId);
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TXgtLockPlan freeze(Long id, Long adminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("冻结原因必填");
        }
        TXgtLockPlan plan = xgtLockPlanMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException("锁仓计划不存在");
        }
        if (!TXgtLockPlan.STATUS_LOCKED.equals(plan.getStatus())) {
            throw new ServiceException("仅 locked 状态可冻结，当前状态：" + plan.getStatus());
        }
        plan.setStatus(TXgtLockPlan.STATUS_FROZEN);
        plan.setFrozenReason(reason);
        xgtLockPlanMapper.updateById(plan);
        log.info("[admin xgt_lock] freeze planId={} adminId={} reason={}", id, adminId, reason);
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TXgtLockPlan unfreeze(Long id, Long adminId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new ServiceException("解冻原因必填");
        }
        TXgtLockPlan plan = xgtLockPlanMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException("锁仓计划不存在");
        }
        if (!TXgtLockPlan.STATUS_FROZEN.equals(plan.getStatus())) {
            throw new ServiceException("仅 frozen 状态可解冻，当前状态：" + plan.getStatus());
        }
        plan.setStatus(TXgtLockPlan.STATUS_LOCKED);
        // 保留 frozen_reason 作为审计留痕
        plan.setRemark(StrUtil.isBlank(plan.getRemark())
                ? ("admin#" + adminId + " unfreeze: " + reason)
                : (plan.getRemark() + " | admin#" + adminId + " unfreeze: " + reason));
        xgtLockPlanMapper.updateById(plan);
        log.info("[admin xgt_lock] unfreeze planId={} adminId={} reason={}", id, adminId, reason);
        return plan;
    }

    @Override
    public Map<String, Object> retry(Long id, Long adminId) {
        TXgtLockPlan plan = xgtLockPlanMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException("锁仓计划不存在");
        }
        if (!TXgtLockPlan.STATUS_LOCKED.equals(plan.getStatus())) {
            throw new ServiceException("仅 locked 状态可补发，当前状态：" + plan.getStatus());
        }
        if (plan.getReleaseAt() != null && plan.getReleaseAt().getTime() > System.currentTimeMillis()) {
            throw new ServiceException("锁仓未到期，无法补发");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BigDecimal released = xgtLockReleaseSettleService.releaseOnePlan(id, today, null);
        Map<String, Object> result = new HashMap<>();
        result.put("planId", id);
        result.put("released", released);
        result.put("success", released != null);
        if (released == null) {
            result.put("message", "release 跳过（可能用户被冻结、状态不符或并发已处理）");
        }
        log.info("[admin xgt_lock] retry planId={} adminId={} released={}", id, adminId, released);
        return result;
    }

    @Override
    public List<XgtLockPlanAdminVO> listForExport(Long userId, String sourceType, String status,
                                                  Date beginLockedAt, Date endLockedAt,
                                                  Date beginReleaseAt, Date endReleaseAt) {
        LambdaQueryWrapper<TXgtLockPlan> wrapper = buildQueryWrapper(
                userId, sourceType, status, beginLockedAt, endLockedAt, beginReleaseAt, endReleaseAt);
        wrapper.orderByDesc(TXgtLockPlan::getId).last("LIMIT " + EXPORT_CAP);
        List<TXgtLockPlan> rows = xgtLockPlanMapper.selectList(wrapper);
        List<XgtLockPlanAdminVO> vos = rows.stream().map(this::toVO).collect(Collectors.toList());
        fillUserLoginName(vos);
        return vos;
    }

    // ============================================================================
    // 内部
    // ============================================================================

    private LambdaQueryWrapper<TXgtLockPlan> buildQueryWrapper(
            Long userId, String sourceType, String status,
            Date beginLockedAt, Date endLockedAt,
            Date beginReleaseAt, Date endReleaseAt) {
        LambdaQueryWrapper<TXgtLockPlan> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(TXgtLockPlan::getUserId, userId);
        }
        if (StrUtil.isNotBlank(sourceType)) {
            wrapper.eq(TXgtLockPlan::getSourceType, sourceType);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(TXgtLockPlan::getStatus, status);
        }
        if (beginLockedAt != null) {
            wrapper.ge(TXgtLockPlan::getLockedAt, beginLockedAt);
        }
        if (endLockedAt != null) {
            wrapper.le(TXgtLockPlan::getLockedAt, endLockedAt);
        }
        if (beginReleaseAt != null) {
            wrapper.ge(TXgtLockPlan::getReleaseAt, beginReleaseAt);
        }
        if (endReleaseAt != null) {
            wrapper.le(TXgtLockPlan::getReleaseAt, endReleaseAt);
        }
        return wrapper;
    }

    private XgtLockPlanAdminVO toVO(TXgtLockPlan plan) {
        XgtLockPlanAdminVO vo = new XgtLockPlanAdminVO();
        vo.setId(plan.getId());
        vo.setUserId(plan.getUserId());
        vo.setSourceType(plan.getSourceType());
        vo.setSourceRefId(plan.getSourceRefId());
        vo.setAmountXgt(plan.getAmountXgt());
        vo.setAmountUsdNominal(plan.getAmountUsdNominal());
        vo.setLockedAt(plan.getLockedAt());
        vo.setReleaseAt(plan.getReleaseAt());
        vo.setReleasedAt(plan.getReleasedAt());
        vo.setStatus(plan.getStatus());
        vo.setFrozenReason(plan.getFrozenReason());
        vo.setChainAddress(plan.getChainAddress());
        vo.setTxHash(plan.getTxHash());
        vo.setRemark(plan.getRemark());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }

    private void fillUserLoginName(List<XgtLockPlanAdminVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> userIds = rows.stream().map(XgtLockPlanAdminVO::getUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        List<TAppUser> users = appUserMapper.selectBatchIds(userIds);
        Map<Long, String> loginMap = users.stream()
                .collect(Collectors.toMap(TAppUser::getUserId, u ->
                        u.getLoginName() == null ? "" : u.getLoginName(), (a, b) -> a));
        for (XgtLockPlanAdminVO vo : rows) {
            vo.setUserLoginName(loginMap.getOrDefault(vo.getUserId(), ""));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
