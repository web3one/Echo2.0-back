package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TEcosystemCreditBalance;
import com.ruoyi.bussiness.domain.TEcosystemCreditUnlockLog;
import com.ruoyi.bussiness.domain.TXgtBalance;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.TXgtLog;
import com.ruoyi.bussiness.domain.dto.EcosystemCreditUnlockDTO;
import com.ruoyi.bussiness.domain.vo.EcosystemCreditUnlockResultVO;
import com.ruoyi.bussiness.domain.vo.MyEcosystemCreditVO;
import com.ruoyi.bussiness.mapper.TEcosystemCreditBalanceMapper;
import com.ruoyi.bussiness.mapper.TEcosystemCreditUnlockLogMapper;
import com.ruoyi.bussiness.mapper.TXgtBalanceMapper;
import com.ruoyi.bussiness.mapper.TXgtLockPlanMapper;
import com.ruoyi.bussiness.mapper.TXgtLogMapper;
import com.ruoyi.bussiness.service.IEcosystemCreditService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ecosystem_credit 服务实现（PRD §10）。
 *
 * @date 2026-05-11 v3.10 C-2
 */
@Service
@Slf4j
public class EcosystemCreditServiceImpl implements IEcosystemCreditService {

    private static final BigDecimal TRADE_VOLUME_MULTIPLIER = new BigDecimal("3");
    private static final long XGT_LOCK_DAYS = 30L;
    private static final long ONE_DAY_MILLIS = 86_400_000L;

    @Resource
    private TEcosystemCreditBalanceMapper ecoBalanceMapper;
    @Resource
    private TEcosystemCreditUnlockLogMapper ecoUnlockLogMapper;
    @Resource
    private TXgtBalanceMapper xgtBalanceMapper;
    @Resource
    private TXgtLockPlanMapper xgtLockPlanMapper;
    @Resource
    private TXgtLogMapper xgtLogMapper;

    @Override
    public MyEcosystemCreditVO getMyCredit(Long userId) {
        MyEcosystemCreditVO vo = new MyEcosystemCreditVO();

        BigDecimal locked = BigDecimal.ZERO;
        BigDecimal unlocked = BigDecimal.ZERO;
        TEcosystemCreditBalance bal = userId == null ? null : ecoBalanceMapper.selectByUserId(userId);
        if (bal != null) {
            locked = nz(bal.getBalanceLocked());
            unlocked = nz(bal.getBalanceUnlocked());
        }
        BigDecimal inProgress = userId == null
                ? BigDecimal.ZERO
                : nz(ecoUnlockLogMapper.sumInProgressAmountByUser(userId));
        BigDecimal available = locked.subtract(inProgress).max(BigDecimal.ZERO);

        vo.setBalanceLocked(locked);
        vo.setBalanceUnlocked(unlocked);
        vo.setBalanceInProgress(inProgress);
        vo.setBalanceAvailable(available);

        List<TEcosystemCreditUnlockLog> rows = userId == null ? new ArrayList<>()
                : ecoUnlockLogMapper.selectList(
                        new LambdaQueryWrapper<TEcosystemCreditUnlockLog>()
                                .eq(TEcosystemCreditUnlockLog::getUserId, userId)
                                .orderByDesc(TEcosystemCreditUnlockLog::getId)
                                .last("LIMIT 50"));

        List<MyEcosystemCreditVO.UnlockEntry> entries = rows.stream().map(r -> {
            MyEcosystemCreditVO.UnlockEntry e = new MyEcosystemCreditVO.UnlockEntry();
            e.setId(r.getId());
            e.setUnlockType(r.getUnlockType());
            e.setAmountCredit(r.getAmountCredit());
            e.setTradeVolumeRequired(r.getTradeVolumeRequired());
            e.setTradeVolumeCompleted(r.getTradeVolumeCompleted());
            e.setXgtLockPlanId(r.getXgtLockPlanId());
            if (r.getXgtLockPlanId() != null) {
                TXgtLockPlan plan = xgtLockPlanMapper.selectById(r.getXgtLockPlanId());
                if (plan != null) {
                    e.setXgtLockReleaseAt(plan.getReleaseAt());
                    e.setXgtLockStatus(plan.getStatus());
                }
            }
            e.setStatus(r.getStatus());
            e.setStartedAt(r.getStartedAt());
            e.setCompletedAt(r.getCompletedAt());
            e.setUsdtCredited(r.getUsdtCredited());
            return e;
        }).collect(Collectors.toList());

        // ensure stable order
        entries.sort(Comparator.comparing(MyEcosystemCreditVO.UnlockEntry::getStartedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        vo.setEntries(entries);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EcosystemCreditUnlockResultVO submitUnlock(Long userId, EcosystemCreditUnlockDTO dto) {
        if (userId == null) {
            throw new ServiceException(MessageUtils.message("api.auth.required"));
        }
        if (dto == null
                || dto.getAmountCredit() == null
                || dto.getAmountCredit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(MessageUtils.message("eco_credit.unlock.amount.invalid"));
        }
        String unlockType = dto.getUnlockType();
        if (!TEcosystemCreditUnlockLog.TYPE_TRADE_VOLUME.equals(unlockType)
                && !TEcosystemCreditUnlockLog.TYPE_XGT_LOCK.equals(unlockType)) {
            throw new ServiceException(MessageUtils.message("eco_credit.unlock.type.invalid"));
        }

        BigDecimal amount = dto.getAmountCredit().setScale(8, RoundingMode.DOWN);

        // 1) 校验可用 credit
        TEcosystemCreditBalance bal = ecoBalanceMapper.selectByUserId(userId);
        BigDecimal locked = bal == null ? BigDecimal.ZERO : nz(bal.getBalanceLocked());
        BigDecimal inProg = nz(ecoUnlockLogMapper.sumInProgressAmountByUser(userId));
        BigDecimal available = locked.subtract(inProg);
        if (available.compareTo(amount) < 0) {
            throw new ServiceException(MessageUtils.message("eco_credit.unlock.insufficient"));
        }

        Date now = new Date();
        TEcosystemCreditUnlockLog row = new TEcosystemCreditUnlockLog();
        row.setUserId(userId);
        row.setUnlockType(unlockType);
        row.setAmountCredit(amount);
        row.setStatus(TEcosystemCreditUnlockLog.STATUS_IN_PROGRESS);
        row.setStartedAt(now);
        row.setTradeVolumeCompleted(BigDecimal.ZERO);
        row.setUsdtCredited(BigDecimal.ZERO);

        Long xgtLockPlanId = null;
        Date xgtLockReleaseAt = null;

        if (TEcosystemCreditUnlockLog.TYPE_TRADE_VOLUME.equals(unlockType)) {
            row.setTradeVolumeRequired(amount.multiply(TRADE_VOLUME_MULTIPLIER));
            ecoUnlockLogMapper.insert(row);
        } else {
            // B 路径：锁 1:1 等值 XGT
            // 检查 XGT 可用余额（balance_unlocked）
            TXgtBalance xgtBal = xgtBalanceMapper.selectByUserId(userId);
            BigDecimal xgtUnlocked = xgtBal == null ? BigDecimal.ZERO : nz(xgtBal.getBalanceUnlocked());
            if (xgtUnlocked.compareTo(amount) < 0) {
                throw new ServiceException(MessageUtils.message("eco_credit.unlock.xgt.insufficient"));
            }

            // 先 INSERT unlock_log 占位拿 id，再创建 XGT lock plan
            ecoUnlockLogMapper.insert(row);
            Long unlockLogId = row.getId();

            TXgtLockPlan plan = new TXgtLockPlan();
            plan.setUserId(userId);
            plan.setSourceType(TXgtLockPlan.SOURCE_CREDIT_UNLOCK);
            plan.setSourceRefId(String.valueOf(unlockLogId));
            plan.setAmountXgt(amount);
            plan.setAmountUsdNominal(amount);
            plan.setLockedAt(now);
            xgtLockReleaseAt = new Date(now.getTime() + XGT_LOCK_DAYS * ONE_DAY_MILLIS);
            plan.setReleaseAt(xgtLockReleaseAt);
            plan.setStatus(TXgtLockPlan.STATUS_LOCKED);
            try {
                xgtLockPlanMapper.insert(plan);
            } catch (DuplicateKeyException e) {
                // 同 source_ref_id 已存在（重复提交场景），读出已有 plan
                TXgtLockPlan exist = xgtLockPlanMapper.selectBySource(
                        TXgtLockPlan.SOURCE_CREDIT_UNLOCK, String.valueOf(unlockLogId));
                if (exist == null) throw e;
                plan = exist;
                xgtLockReleaseAt = plan.getReleaseAt();
            }
            xgtLockPlanId = plan.getId();

            // 调整 XGT 余额：unlocked -= amount, locked += amount
            BigDecimal curLocked = xgtBal == null ? BigDecimal.ZERO : nz(xgtBal.getBalanceLocked());
            BigDecimal newLocked = curLocked.add(amount);
            BigDecimal newUnlocked = xgtUnlocked.subtract(amount);
            if (xgtBal == null) {
                xgtBal = new TXgtBalance();
                xgtBal.setUserId(userId);
                xgtBal.setBalanceLocked(newLocked);
                xgtBal.setBalanceUnlocked(BigDecimal.ZERO); // 异常路径：刚才查到 unlocked >=amount 但 bal 为 null 矛盾，置 0 兜底
                try { xgtBalanceMapper.insert(xgtBal); }
                catch (DuplicateKeyException e) {
                    xgtBal = xgtBalanceMapper.selectByUserId(userId);
                    xgtBal.setBalanceLocked(nz(xgtBal.getBalanceLocked()).add(amount));
                    xgtBal.setBalanceUnlocked(nz(xgtBal.getBalanceUnlocked()).subtract(amount));
                    xgtBalanceMapper.updateById(xgtBal);
                }
            } else {
                xgtBal.setBalanceLocked(newLocked);
                xgtBal.setBalanceUnlocked(newUnlocked);
                xgtBalanceMapper.updateById(xgtBal);
            }

            // 写 XGT 流水 lock
            TXgtLog logRow = new TXgtLog();
            logRow.setUserId(userId);
            logRow.setChangeType(TXgtLog.CHANGE_LOCK);
            logRow.setAmountXgt(amount);
            logRow.setBalanceLockedAfter(newLocked);
            logRow.setBalanceUnlockedAfter(newUnlocked);
            logRow.setRelatedLockPlanId(xgtLockPlanId);
            logRow.setRemark("Lock for credit_unlock #" + unlockLogId);
            xgtLogMapper.insert(logRow);

            // 回写 unlock_log.xgt_lock_plan_id
            TEcosystemCreditUnlockLog upd = new TEcosystemCreditUnlockLog();
            upd.setId(unlockLogId);
            upd.setXgtLockPlanId(xgtLockPlanId);
            ecoUnlockLogMapper.updateById(upd);
            row.setXgtLockPlanId(xgtLockPlanId);
        }

        EcosystemCreditUnlockResultVO vo = new EcosystemCreditUnlockResultVO();
        vo.setUnlockLogId(row.getId());
        vo.setUnlockType(unlockType);
        vo.setAmountCredit(amount);
        vo.setTradeVolumeRequired(row.getTradeVolumeRequired());
        vo.setXgtLockPlanId(xgtLockPlanId);
        vo.setXgtLockReleaseAt(xgtLockReleaseAt);
        vo.setStatus(row.getStatus());
        vo.setStartedAt(now);
        vo.setIdempotent(false);
        return vo;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
