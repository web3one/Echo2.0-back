package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.*;
import com.ruoyi.bussiness.domain.dto.CopyFollowDTO;
import com.ruoyi.bussiness.domain.dto.CopyTraderApplyDTO;
import com.ruoyi.bussiness.mapper.*;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ucontract.ContractComputerUtil;
import com.ruoyi.system.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
public class CopyTradingServiceImpl implements ICopyTradingService {
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_DISABLED = "disabled";
    private static final String REL_ACTIVE = "active";
    private static final String REL_STOPPED = "stopped";
    private static final String CFG_SHARE_RATE = "copy.trade.profit_share_rate";

    @Resource
    private TCopyTraderMapper copyTraderMapper;
    @Resource
    private TCopyTraderApplyMapper copyTraderApplyMapper;
    @Resource
    private TCopyRelationMapper copyRelationMapper;
    @Resource
    private TCopyOrderMapper copyOrderMapper;
    @Resource
    private TCopyProfitShareLogMapper copyProfitShareLogMapper;
    @Resource
    private TContractPositionMapper contractPositionMapper;
    @Resource
    private ITContractCoinService contractCoinService;
    @Resource
    private ITAppAssetService appAssetService;
    @Resource
    private ITAppUserService appUserService;
    @Resource
    private ITAppWalletRecordService appWalletRecordService;
    @Resource
    private ITContractPositionService contractPositionService;
    @Resource
    private ISysConfigService sysConfigService;

    @Override
    @Transactional
    public TCopyTraderApply applyTrader(Long userId, CopyTraderApplyDTO dto) {
        TCopyTrader trader = getTrader(userId);
        if (trader != null && STATUS_APPROVED.equals(trader.getStatus())) {
            throw new ServiceException("您已经是交易员");
        }
        TCopyTraderApply pending = copyTraderApplyMapper.selectOne(new LambdaQueryWrapper<TCopyTraderApply>()
                .eq(TCopyTraderApply::getUserId, userId)
                .eq(TCopyTraderApply::getStatus, STATUS_PENDING)
                .last("limit 1"));
        if (pending != null) {
            return pending;
        }
        TAppUser user = appUserService.selectTAppUserByUserId(userId);
        TCopyTraderApply apply = new TCopyTraderApply();
        apply.setUserId(userId);
        apply.setDisplayName(StringUtils.isNotEmpty(dto == null ? null : dto.getDisplayName())
                ? dto.getDisplayName()
                : (user == null ? "Trader " + userId : user.getLoginName()));
        apply.setBio(dto == null ? null : dto.getBio());
        apply.setStatus(STATUS_PENDING);
        apply.setCreateTime(new Date());
        apply.setUpdateTime(new Date());
        copyTraderApplyMapper.insert(apply);
        return apply;
    }

    @Override
    public Object getMyTraderStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("trader", getTrader(userId));
        result.put("latestApply", copyTraderApplyMapper.selectOne(new LambdaQueryWrapper<TCopyTraderApply>()
                .eq(TCopyTraderApply::getUserId, userId)
                .orderByDesc(TCopyTraderApply::getCreateTime)
                .last("limit 1")));
        return result;
    }

    @Override
    public List<TCopyTrader> listApprovedTraders(TCopyTrader query) {
        LambdaQueryWrapper<TCopyTrader> wrapper = new LambdaQueryWrapper<TCopyTrader>()
                .eq(TCopyTrader::getStatus, STATUS_APPROVED)
                .orderByDesc(TCopyTrader::getFollowerCount)
                .orderByDesc(TCopyTrader::getCreateTime);
        if (query != null && query.getUserId() != null) {
            wrapper.eq(TCopyTrader::getUserId, query.getUserId());
        }
        return copyTraderMapper.selectList(wrapper);
    }

    @Override
    public TCopyTrader getTrader(Long traderUserId) {
        if (traderUserId == null) return null;
        return copyTraderMapper.selectOne(new LambdaQueryWrapper<TCopyTrader>()
                .eq(TCopyTrader::getUserId, traderUserId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public TCopyRelation follow(Long followerUserId, CopyFollowDTO dto) {
        if (dto == null || dto.getTraderUserId() == null) {
            throw new ServiceException("请选择交易员");
        }
        if (Objects.equals(followerUserId, dto.getTraderUserId())) {
            throw new ServiceException("不能跟单自己");
        }
        TCopyTrader trader = getTrader(dto.getTraderUserId());
        if (trader == null || !STATUS_APPROVED.equals(trader.getStatus())) {
            throw new ServiceException("交易员暂不可跟单");
        }
        validatePositive(dto.getCopyTotalAmount(), "跟单总额必须大于0");
        validatePositive(dto.getMaxSingleAmount(), "单笔上限必须大于0");
        if (dto.getMaxSingleAmount().compareTo(dto.getCopyTotalAmount()) > 0) {
            throw new ServiceException("单笔上限不能大于跟单总额");
        }
        BigDecimal stopLossRate = nvl(dto.getStopLossRate());
        if (stopLossRate.compareTo(BigDecimal.ZERO) < 0 || stopLossRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ServiceException("止损比例必须在0到1之间");
        }
        TCopyRelation relation = copyRelationMapper.selectOne(new LambdaQueryWrapper<TCopyRelation>()
                .eq(TCopyRelation::getFollowerUserId, followerUserId)
                .eq(TCopyRelation::getTraderUserId, dto.getTraderUserId())
                .last("limit 1"));
        Date now = new Date();
        if (relation == null) {
            relation = new TCopyRelation();
            relation.setFollowerUserId(followerUserId);
            relation.setTraderUserId(dto.getTraderUserId());
            relation.setCreateTime(now);
        }
        relation.setCopyTotalAmount(dto.getCopyTotalAmount());
        relation.setMaxSingleAmount(dto.getMaxSingleAmount());
        relation.setStopLossRate(stopLossRate);
        relation.setStatus(REL_ACTIVE);
        relation.setUpdateTime(now);
        if (relation.getId() == null) {
            copyRelationMapper.insert(relation);
        } else {
            copyRelationMapper.updateById(relation);
        }
        refreshTraderStats(dto.getTraderUserId());
        return relation;
    }

    @Override
    @Transactional
    public void unfollow(Long followerUserId, Long traderUserId) {
        TCopyRelation relation = copyRelationMapper.selectOne(new LambdaQueryWrapper<TCopyRelation>()
                .eq(TCopyRelation::getFollowerUserId, followerUserId)
                .eq(TCopyRelation::getTraderUserId, traderUserId)
                .last("limit 1"));
        if (relation != null) {
            relation.setStatus(REL_STOPPED);
            relation.setUpdateTime(new Date());
            copyRelationMapper.updateById(relation);
            refreshTraderStats(traderUserId);
        }
    }

    @Override
    public List<TCopyRelation> myRelations(Long followerUserId) {
        return copyRelationMapper.selectList(new LambdaQueryWrapper<TCopyRelation>()
                .eq(TCopyRelation::getFollowerUserId, followerUserId)
                .orderByDesc(TCopyRelation::getCreateTime));
    }

    @Override
    public List<TCopyOrder> myOrders(Long followerUserId) {
        return copyOrderMapper.selectList(new LambdaQueryWrapper<TCopyOrder>()
                .eq(TCopyOrder::getFollowerUserId, followerUserId)
                .orderByDesc(TCopyOrder::getCreateTime));
    }

    @Override
    public List<TCopyTraderApply> adminApplications(TCopyTraderApply query) {
        LambdaQueryWrapper<TCopyTraderApply> wrapper = new LambdaQueryWrapper<TCopyTraderApply>()
                .orderByDesc(TCopyTraderApply::getCreateTime);
        if (query != null) {
            if (query.getUserId() != null) wrapper.eq(TCopyTraderApply::getUserId, query.getUserId());
            if (StringUtils.isNotEmpty(query.getStatus())) wrapper.eq(TCopyTraderApply::getStatus, query.getStatus());
        }
        return copyTraderApplyMapper.selectList(wrapper);
    }

    @Override
    public List<TCopyTrader> adminTraders(TCopyTrader query) {
        LambdaQueryWrapper<TCopyTrader> wrapper = new LambdaQueryWrapper<TCopyTrader>()
                .orderByDesc(TCopyTrader::getCreateTime);
        if (query != null) {
            if (query.getUserId() != null) wrapper.eq(TCopyTrader::getUserId, query.getUserId());
            if (StringUtils.isNotEmpty(query.getStatus())) wrapper.eq(TCopyTrader::getStatus, query.getStatus());
        }
        return copyTraderMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public TCopyTrader approveApplication(Long id, Long adminId, String remark) {
        TCopyTraderApply apply = copyTraderApplyMapper.selectById(id);
        if (apply == null || !STATUS_PENDING.equals(apply.getStatus())) {
            throw new ServiceException("申请状态不正确");
        }
        Date now = new Date();
        apply.setStatus(STATUS_APPROVED);
        apply.setReviewAdminId(adminId);
        apply.setReviewRemark(remark);
        apply.setReviewTime(now);
        apply.setUpdateTime(now);
        copyTraderApplyMapper.updateById(apply);

        TCopyTrader trader = getTrader(apply.getUserId());
        if (trader == null) {
            trader = new TCopyTrader();
            trader.setUserId(apply.getUserId());
            trader.setCreateTime(now);
            trader.setFollowerCount(0);
            trader.setTotalCopyAmount(BigDecimal.ZERO);
            trader.setTotalProfit(BigDecimal.ZERO);
            trader.setWinRate(BigDecimal.ZERO);
        }
        trader.setDisplayName(apply.getDisplayName());
        trader.setBio(apply.getBio());
        trader.setStatus(STATUS_APPROVED);
        trader.setProfitShareRate(loadDefaultShareRate());
        trader.setReviewAdminId(adminId);
        trader.setReviewRemark(remark);
        trader.setReviewTime(now);
        trader.setUpdateTime(now);
        if (trader.getId() == null) {
            copyTraderMapper.insert(trader);
        } else {
            copyTraderMapper.updateById(trader);
        }
        return trader;
    }

    @Override
    @Transactional
    public void rejectApplication(Long id, Long adminId, String remark) {
        TCopyTraderApply apply = copyTraderApplyMapper.selectById(id);
        if (apply == null || !STATUS_PENDING.equals(apply.getStatus())) {
            throw new ServiceException("申请状态不正确");
        }
        apply.setStatus(STATUS_REJECTED);
        apply.setReviewAdminId(adminId);
        apply.setReviewRemark(remark);
        apply.setReviewTime(new Date());
        apply.setUpdateTime(new Date());
        copyTraderApplyMapper.updateById(apply);
    }

    @Override
    @Transactional
    public void updateTraderStatus(Long traderUserId, String status, String remark) {
        if (!STATUS_APPROVED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new ServiceException("交易员状态不正确");
        }
        TCopyTrader trader = getTrader(traderUserId);
        if (trader == null) {
            throw new ServiceException("交易员不存在");
        }
        trader.setStatus(status);
        trader.setReviewRemark(remark);
        trader.setUpdateTime(new Date());
        copyTraderMapper.updateById(trader);
    }

    @Override
    public void afterTraderOpen(TContractPosition traderPosition, BigDecimal traderAvailableBeforeOrder) {
        if (traderPosition == null || traderPosition.getId() == null || traderAvailableBeforeOrder == null
                || traderAvailableBeforeOrder.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TCopyTrader trader = getTrader(traderPosition.getUserId());
        if (trader == null || !STATUS_APPROVED.equals(trader.getStatus())) {
            return;
        }
        List<TCopyRelation> relations = copyRelationMapper.selectList(new LambdaQueryWrapper<TCopyRelation>()
                .eq(TCopyRelation::getTraderUserId, traderPosition.getUserId())
                .eq(TCopyRelation::getStatus, REL_ACTIVE));
        for (TCopyRelation relation : relations) {
            try {
                copyOpenForRelation(trader, relation, traderPosition, traderAvailableBeforeOrder);
            } catch (Exception e) {
                log.warn("copy open failed: relation={} traderPosition={} err={}", relation.getId(), traderPosition.getId(), e.getMessage());
                recordFailedCopy(relation, trader, traderPosition, e.getMessage());
            }
        }
    }

    @Override
    public void afterTraderClose(TContractPosition traderPosition) {
        if (traderPosition == null || traderPosition.getId() == null || traderPosition.getStatus() == null
                || traderPosition.getStatus() != 1) {
            return;
        }
        List<TCopyOrder> orders = copyOrderMapper.selectList(new LambdaQueryWrapper<TCopyOrder>()
                .eq(TCopyOrder::getTraderPositionId, traderPosition.getId())
                .eq(TCopyOrder::getOpenStatus, "success")
                .eq(TCopyOrder::getCloseStatus, "pending"));
        for (TCopyOrder order : orders) {
            try {
                String result = contractPositionService.stopPosition(order.getFollowerPositionId());
                if (!"success".equals(result)) {
                    order.setCloseStatus("failed");
                    order.setCloseFailReason(result);
                    order.setUpdateTime(new Date());
                    copyOrderMapper.updateById(order);
                    continue;
                }
                TContractPosition followerPosition = contractPositionMapper.selectById(order.getFollowerPositionId());
                settleProfitShare(order, followerPosition);
                order.setCloseStatus("success");
                order.setFollowerEarn(nvl(followerPosition.getEarn()));
                order.setUpdateTime(new Date());
                copyOrderMapper.updateById(order);
                refreshTraderStats(order.getTraderUserId());
                applyStopLossIfNeeded(order.getRelationId());
            } catch (Exception e) {
                order.setCloseStatus("failed");
                order.setCloseFailReason(e.getMessage());
                order.setUpdateTime(new Date());
                copyOrderMapper.updateById(order);
                log.warn("copy close failed: copyOrder={} err={}", order.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void copyOpenForRelation(TCopyTrader trader, TCopyRelation relation, TContractPosition traderPosition, BigDecimal traderAvailableBeforeOrder) {
        BigDecimal ratio = traderPosition.getAdjustAmount().divide(traderAvailableBeforeOrder, 10, RoundingMode.DOWN);
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("复制比例无效");
        }
        BigDecimal followerMargin = relation.getCopyTotalAmount().multiply(ratio).setScale(8, RoundingMode.DOWN);
        if (followerMargin.compareTo(relation.getMaxSingleAmount()) > 0) {
            followerMargin = relation.getMaxSingleAmount();
        }
        if (followerMargin.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("跟单金额过小");
        }
        TContractCoin coin = contractCoinService.selectContractCoinBySymbol(traderPosition.getSymbol());
        if (coin == null || coin.getShareNumber() == null || coin.getShareNumber().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("合约币种配置不存在");
        }
        BigDecimal price = traderPosition.getOpenPrice();
        BigDecimal delegateTotal = followerMargin.multiply(traderPosition.getLeverage())
                .divide(price.multiply(coin.getShareNumber()), 6, RoundingMode.DOWN);
        if (coin.getMinShare() != null && delegateTotal.compareTo(coin.getMinShare()) < 0) {
            throw new ServiceException("跟单金额低于最小开仓手数");
        }
        if (coin.getMaxShare() != null && delegateTotal.compareTo(coin.getMaxShare()) > 0) {
            delegateTotal = coin.getMaxShare();
        }
        BigDecimal num = delegateTotal.multiply(coin.getShareNumber());
        BigDecimal amount = ContractComputerUtil.getAmount(price, num, traderPosition.getLeverage());
        if (amount.compareTo(relation.getMaxSingleAmount()) > 0) {
            throw new ServiceException("跟单金额超过单笔上限");
        }
        TAppAsset followerAsset = appAssetService.getAssetByUserIdAndType(relation.getFollowerUserId(), AssetEnum.CONTRACT_ASSETS.getCode());
        if (followerAsset == null || followerAsset.getAvailableAmount().compareTo(amount) < 0) {
            throw new ServiceException("跟随者合约账户余额不足");
        }
        BigDecimal before = followerAsset.getAvailableAmount();
        BigDecimal openFee = coin.getOpenFee().multiply(amount).setScale(6, RoundingMode.HALF_UP);
        BigDecimal closePrice = ContractComputerUtil.getStrongPrice(traderPosition.getLeverage(), traderPosition.getType(), price, num, amount, openFee);
        String serialId = "CP" + OrderUtils.generateOrderNum();
        TAppUser follower = appUserService.selectTAppUserByUserId(relation.getFollowerUserId());

        followerAsset.setAmout(followerAsset.getAmout().subtract(amount));
        followerAsset.setAvailableAmount(followerAsset.getAvailableAmount().subtract(amount));
        appAssetService.updateTAppAsset(followerAsset);

        TContractPosition followerPosition = new TContractPosition();
        followerPosition.setSymbol(traderPosition.getSymbol());
        followerPosition.setAmount(amount.subtract(openFee));
        followerPosition.setAdjustAmount(amount.subtract(openFee));
        followerPosition.setLeverage(traderPosition.getLeverage());
        followerPosition.setClosePrice(closePrice);
        followerPosition.setOpenNum(num);
        followerPosition.setOpenPrice(price);
        followerPosition.setOpenFee(openFee);
        followerPosition.setStatus(0);
        followerPosition.setType(traderPosition.getType());
        followerPosition.setDelegateType(1);
        followerPosition.setCreateTime(new Date());
        followerPosition.setRemainMargin(amount.subtract(openFee));
        followerPosition.setOrderNo(serialId);
        followerPosition.setUserId(relation.getFollowerUserId());
        followerPosition.setEntrustmentValue(price.multiply(num).setScale(6, RoundingMode.HALF_UP));
        followerPosition.setAdminParentIds(follower == null ? null : follower.getAdminParentIds());
        followerPosition.setDeliveryDays(0);
        followerPosition.setSubTime(new Date());
        contractPositionMapper.insert(followerPosition);

        appWalletRecordService.generateRecord(relation.getFollowerUserId(), amount, RecordEnum.CONTRACT_TRANSACTIONSUB.getCode(),
                follower == null ? null : follower.getLoginName(), serialId, "跟单开仓",
                before, before.subtract(amount), coin.getBaseCoin().toLowerCase(), follower == null ? null : follower.getAdminParentIds());

        TCopyOrder order = new TCopyOrder();
        order.setRelationId(relation.getId());
        order.setTraderUserId(relation.getTraderUserId());
        order.setFollowerUserId(relation.getFollowerUserId());
        order.setTraderPositionId(traderPosition.getId());
        order.setFollowerPositionId(followerPosition.getId());
        order.setSymbol(traderPosition.getSymbol());
        order.setType(traderPosition.getType());
        order.setLeverage(traderPosition.getLeverage());
        order.setCopyRatio(ratio);
        order.setTraderMargin(traderPosition.getAdjustAmount());
        order.setFollowerMargin(amount);
        order.setProfitShareRateSnap(trader.getProfitShareRate() == null ? loadDefaultShareRate() : trader.getProfitShareRate());
        order.setOpenStatus("success");
        order.setCloseStatus("pending");
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        copyOrderMapper.insert(order);
    }

    private void recordFailedCopy(TCopyRelation relation, TCopyTrader trader, TContractPosition traderPosition, String reason) {
        TCopyOrder order = new TCopyOrder();
        order.setRelationId(relation.getId());
        order.setTraderUserId(relation.getTraderUserId());
        order.setFollowerUserId(relation.getFollowerUserId());
        order.setTraderPositionId(traderPosition.getId());
        order.setSymbol(traderPosition.getSymbol());
        order.setType(traderPosition.getType());
        order.setLeverage(traderPosition.getLeverage());
        order.setCopyRatio(BigDecimal.ZERO);
        order.setTraderMargin(nvl(traderPosition.getAdjustAmount()));
        order.setFollowerMargin(BigDecimal.ZERO);
        order.setProfitShareRateSnap(trader.getProfitShareRate() == null ? loadDefaultShareRate() : trader.getProfitShareRate());
        order.setOpenStatus("failed");
        order.setCloseStatus("skipped");
        order.setFailReason(reason == null ? "copy failed" : reason);
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        try {
            copyOrderMapper.insert(order);
        } catch (Exception ignored) {
        }
    }

    private void settleProfitShare(TCopyOrder order, TContractPosition followerPosition) {
        BigDecimal earn = nvl(followerPosition.getEarn());
        if (earn.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TCopyProfitShareLog exists = copyProfitShareLogMapper.selectOne(new LambdaQueryWrapper<TCopyProfitShareLog>()
                .eq(TCopyProfitShareLog::getCopyOrderId, order.getId())
                .last("limit 1"));
        if (exists != null) {
            return;
        }
        BigDecimal shareAmount = earn.multiply(order.getProfitShareRateSnap()).setScale(8, RoundingMode.DOWN);
        if (shareAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TAppAsset followerAsset = appAssetService.getAssetByUserIdAndType(order.getFollowerUserId(), AssetEnum.CONTRACT_ASSETS.getCode());
        TAppAsset traderAsset = appAssetService.getAssetByUserIdAndType(order.getTraderUserId(), AssetEnum.CONTRACT_ASSETS.getCode());
        if (followerAsset == null || traderAsset == null || followerAsset.getAvailableAmount().compareTo(shareAmount) < 0) {
            throw new ServiceException("盈利分成余额不足");
        }
        TAppUser follower = appUserService.selectTAppUserByUserId(order.getFollowerUserId());
        TAppUser trader = appUserService.selectTAppUserByUserId(order.getTraderUserId());
        BigDecimal followerBefore = followerAsset.getAvailableAmount();
        BigDecimal traderBefore = traderAsset.getAvailableAmount();
        followerAsset.setAmout(followerAsset.getAmout().subtract(shareAmount));
        followerAsset.setAvailableAmount(followerAsset.getAvailableAmount().subtract(shareAmount));
        traderAsset.setAmout(traderAsset.getAmout().add(shareAmount));
        traderAsset.setAvailableAmount(traderAsset.getAvailableAmount().add(shareAmount));
        appAssetService.updateTAppAsset(followerAsset);
        appAssetService.updateTAppAsset(traderAsset);
        appWalletRecordService.generateRecord(order.getFollowerUserId(), shareAmount, RecordEnum.CONTRACT_TRANSACTIONSUB.getCode(),
                null, "CPS" + order.getId(), "跟单盈利分成-", followerBefore, followerBefore.subtract(shareAmount),
                "usdt", follower == null ? null : follower.getAdminParentIds());
        appWalletRecordService.generateRecord(order.getTraderUserId(), shareAmount, RecordEnum.CONTRACT_TRANSACTION_CLOSING.getCode(),
                null, "CPS" + order.getId(), "跟单盈利分成+", traderBefore, traderBefore.add(shareAmount),
                "usdt", trader == null ? null : trader.getAdminParentIds());
        TCopyProfitShareLog log = new TCopyProfitShareLog();
        log.setCopyOrderId(order.getId());
        log.setTraderUserId(order.getTraderUserId());
        log.setFollowerUserId(order.getFollowerUserId());
        log.setFollowerPositionId(order.getFollowerPositionId());
        log.setGrossProfit(earn);
        log.setShareRate(order.getProfitShareRateSnap());
        log.setShareAmount(shareAmount);
        log.setCreateTime(new Date());
        copyProfitShareLogMapper.insert(log);
        order.setProfitShareAmount(shareAmount);
    }

    private void applyStopLossIfNeeded(Long relationId) {
        TCopyRelation relation = copyRelationMapper.selectById(relationId);
        if (relation == null || relation.getStopLossRate() == null || relation.getStopLossRate().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<TCopyOrder> closed = copyOrderMapper.selectList(new LambdaQueryWrapper<TCopyOrder>()
                .eq(TCopyOrder::getRelationId, relationId)
                .eq(TCopyOrder::getCloseStatus, "success"));
        BigDecimal totalEarn = BigDecimal.ZERO;
        for (TCopyOrder order : closed) {
            totalEarn = totalEarn.add(nvl(order.getFollowerEarn()));
        }
        BigDecimal maxLoss = relation.getCopyTotalAmount().multiply(relation.getStopLossRate()).negate();
        if (totalEarn.compareTo(maxLoss) <= 0) {
            relation.setStatus(REL_STOPPED);
            relation.setUpdateTime(new Date());
            copyRelationMapper.updateById(relation);
            refreshTraderStats(relation.getTraderUserId());
        }
    }

    private void refreshTraderStats(Long traderUserId) {
        if (traderUserId == null) return;
        TCopyTrader trader = getTrader(traderUserId);
        if (trader == null) return;
        List<TCopyRelation> active = copyRelationMapper.selectList(new LambdaQueryWrapper<TCopyRelation>()
                .eq(TCopyRelation::getTraderUserId, traderUserId)
                .eq(TCopyRelation::getStatus, REL_ACTIVE));
        trader.setFollowerCount(active.size());
        BigDecimal totalCopy = BigDecimal.ZERO;
        for (TCopyRelation relation : active) {
            totalCopy = totalCopy.add(nvl(relation.getCopyTotalAmount()));
        }
        trader.setTotalCopyAmount(totalCopy);
        List<TCopyOrder> closed = copyOrderMapper.selectList(new LambdaQueryWrapper<TCopyOrder>()
                .eq(TCopyOrder::getTraderUserId, traderUserId)
                .eq(TCopyOrder::getCloseStatus, "success"));
        BigDecimal totalProfit = BigDecimal.ZERO;
        int wins = 0;
        for (TCopyOrder order : closed) {
            BigDecimal earn = nvl(order.getFollowerEarn());
            totalProfit = totalProfit.add(earn);
            if (earn.compareTo(BigDecimal.ZERO) > 0) wins++;
        }
        trader.setTotalProfit(totalProfit);
        trader.setWinRate(closed.isEmpty() ? BigDecimal.ZERO : new BigDecimal(wins).divide(new BigDecimal(closed.size()), 6, RoundingMode.DOWN));
        trader.setUpdateTime(new Date());
        copyTraderMapper.updateById(trader);
    }

    private BigDecimal loadDefaultShareRate() {
        try {
            String value = sysConfigService.selectConfigByKey(CFG_SHARE_RATE);
            if (StringUtils.isNotEmpty(value)) {
                return new BigDecimal(value);
            }
        } catch (Exception e) {
            log.warn("load copy trade share rate failed", e);
        }
        return new BigDecimal("0.10");
    }

    private void validatePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(message);
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
