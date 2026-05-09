package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.*;
import com.ruoyi.bussiness.domain.dto.C2cAppealCreateDTO;
import com.ruoyi.bussiness.domain.dto.C2cAppealResolveDTO;
import com.ruoyi.bussiness.mapper.TC2cAdvertMapper;
import com.ruoyi.bussiness.mapper.TC2cAppealMessageMapper;
import com.ruoyi.bussiness.mapper.TC2cMerchantMapper;
import com.ruoyi.bussiness.mapper.TC2cOrderAppealMapper;
import com.ruoyi.bussiness.mapper.TC2cOrderMapper;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.utils.OrderUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * C2C订单申诉Service业务层处理
 */
@Service
public class C2cAppealServiceImpl extends ServiceImpl<TC2cOrderAppealMapper, TC2cOrderAppeal> implements IC2cAppealService {

    @Resource
    private TC2cOrderAppealMapper c2cOrderAppealMapper;

    @Resource
    private TC2cAppealMessageMapper c2cAppealMessageMapper;

    @Resource
    private IC2cOrderService c2cOrderService;

    @Resource
    private IC2cEscrowService c2cEscrowService;

    @Resource
    private TC2cOrderMapper c2cOrderMapper;

    @Resource
    private TC2cAdvertMapper c2cAdvertMapper;

    @Resource
    private ITAppUserService tAppUserService;

    @Resource
    private TC2cMerchantMapper c2cMerchantMapper;

    @Override
    @Transactional
    public String createAppeal(Long userId, C2cAppealCreateDTO dto) {
        // 1. Get order and verify user is buyer or seller
        TC2cOrder order = c2cOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            return "Order not found";
        }

        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            return "You are not a party of this order";
        }

        // 2. Check order status (0=pending payment, 1=paid)
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            return "Cannot appeal on this order status";
        }

        // 3. Check no existing active appeal
        TC2cOrderAppeal existingAppeal = c2cOrderAppealMapper.selectByOrderId(dto.getOrderId());
        if (existingAppeal != null && existingAppeal.getStatus() != null
                && (existingAppeal.getStatus() == 0 || existingAppeal.getStatus() == 1)) {
            return "An active appeal already exists for this order";
        }

        // Determine respondent
        Long respondentId = isBuyer ? order.getSellerId() : order.getBuyerId();
        TAppUser user = tAppUserService.selectTAppUserByUserId(userId);

        // 4a. Update order status to APPEALING
        int updatedOrder = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                .eq(TC2cOrder::getId, order.getId())
                .in(TC2cOrder::getStatus, 0, 1)
                .set(TC2cOrder::getStatus, 4)
                .set(TC2cOrder::getUpdateTime, new Date()));
        if (updatedOrder == 0) {
            return "Order status has changed, please refresh";
        }

        // 4b. Create appeal record
        TC2cOrderAppeal appeal = new TC2cOrderAppeal();
        appeal.setAppealNo("APL" + OrderUtils.generateOrderNum());
        appeal.setOrderId(order.getId());
        appeal.setOrderNo(order.getOrderNo());
        appeal.setInitiatorId(userId);
        appeal.setRespondentId(respondentId);
        appeal.setAppealType(dto.getAppealType());
        appeal.setAppealReason(dto.getAppealReason());
        appeal.setEvidenceUrls(dto.getEvidenceUrls());
        appeal.setStatus(0); // PENDING
        appeal.setAdminParentIds(user != null ? user.getAdminParentIds() : "");
        appeal.setCreateTime(new Date());
        c2cOrderAppealMapper.insert(appeal);

        // 4c. Create initial appeal message
        TC2cAppealMessage message = new TC2cAppealMessage();
        message.setAppealId(appeal.getId());
        message.setSenderId(userId);
        message.setSenderType(1); // User
        message.setContentType(1); // Text
        message.setContent(dto.getAppealReason());
        message.setCreateTime(new Date());
        c2cAppealMessageMapper.insert(message);

        return null;
    }

    @Override
    public int addMessage(Long appealId, Long senderId, Integer senderType, Integer contentType, String content) {
        TC2cOrderAppeal appeal = c2cOrderAppealMapper.selectById(appealId);
        if (appeal == null) {
            return 0;
        }
        if (Integer.valueOf(1).equals(senderType) && !isAppealParty(appeal, senderId)) {
            return 0;
        }
        TC2cAppealMessage message = new TC2cAppealMessage();
        message.setAppealId(appealId);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setContentType(contentType != null ? contentType : 1);
        message.setContent(content);
        message.setCreateTime(new Date());
        return c2cAppealMessageMapper.insert(message);
    }

    @Override
    @Transactional
    public String resolveAppeal(C2cAppealResolveDTO dto, Long adminId) {
        // 1. Get appeal and check status
        TC2cOrderAppeal appeal = c2cOrderAppealMapper.selectById(dto.getAppealId());
        if (appeal == null) {
            return "Appeal not found";
        }
        if (appeal.getStatus() != 0 && appeal.getStatus() != 1) {
            return "Appeal has already been resolved";
        }

        // 2. Get order
        TC2cOrder order = c2cOrderMapper.selectById(appeal.getOrderId());
        if (order == null) {
            return "Order not found";
        }

        TAppUser seller = tAppUserService.selectTAppUserByUserId(order.getSellerId());
        TAppUser buyer = tAppUserService.selectTAppUserByUserId(order.getBuyerId());
        String sellerAdminParentIds = seller != null ? seller.getAdminParentIds() : "";
        String buyerAdminParentIds = buyer != null ? buyer.getAdminParentIds() : "";

        if ("release".equals(dto.getDecision())) {
            int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                    .eq(TC2cOrder::getId, order.getId())
                    .eq(TC2cOrder::getStatus, 4)
                    .set(TC2cOrder::getStatus, 5)
                    .set(TC2cOrder::getReleaseTime, new Date())
                    .set(TC2cOrder::getUpdateTime, new Date()));
            if (updated == 0) {
                return "Order status has changed, please refresh";
            }

            // Release crypto to buyer
            c2cEscrowService.releaseAsset(order.getSellerId(), order.getBuyerId(),
                    normalizeSymbol(order.getCryptoCurrency()), order.getCryptoAmount(),
                    order.getOrderNo(), sellerAdminParentIds, buyerAdminParentIds);

            // Update merchant stats
            TC2cMerchant merchant = c2cMerchantMapper.selectById(order.getMerchantId());
            if (merchant != null) {
                int newTotalOrders = (merchant.getTotalOrders() != null ? merchant.getTotalOrders() : 0) + 1;
                BigDecimal newTotalVolume = (merchant.getTotalVolume() != null ? merchant.getTotalVolume() : BigDecimal.ZERO)
                        .add(order.getFiatAmount());
                BigDecimal completionRate = merchant.getCompletionRate() != null ? merchant.getCompletionRate() : BigDecimal.ZERO;
                c2cMerchantMapper.updateMerchantStats(merchant.getId(), newTotalOrders, newTotalVolume, completionRate);
            }

            // Update appeal status
            appeal.setStatus(2); // RESOLVED - RELEASE
            appeal.setAdminId(adminId);
            appeal.setAdminResult(dto.getAdminResult());
            appeal.setUpdateTime(new Date());
            c2cOrderAppealMapper.updateById(appeal);

        } else if ("cancel".equals(dto.getDecision())) {
            int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                    .eq(TC2cOrder::getId, order.getId())
                    .eq(TC2cOrder::getStatus, 4)
                    .set(TC2cOrder::getStatus, 5)
                    .set(TC2cOrder::getCancelTime, new Date())
                    .set(TC2cOrder::getCancelledBy, "admin")
                    .set(TC2cOrder::getCancelReason, dto.getAdminResult())
                    .set(TC2cOrder::getUpdateTime, new Date()));
            if (updated == 0) {
                return "Order status has changed, please refresh";
            }

            // Cancel and return crypto to seller
            if (order.getAdType() == 1) {
                // SELL advert: crypto was frozen at ad level, just restore advert remaining
                c2cAdvertMapper.restoreRemainingAmount(order.getAdId(), order.getCryptoAmount());
            } else {
                // BUY advert: unfreeze crypto from seller + restore advert remaining
                c2cEscrowService.unfreezeAsset(order.getSellerId(), normalizeSymbol(order.getCryptoCurrency()), order.getCryptoAmount(),
                        order.getOrderNo(), sellerAdminParentIds);
                c2cAdvertMapper.restoreRemainingAmount(order.getAdId(), order.getCryptoAmount());
            }

            // Restore advert status if depleted
            TC2cAdvert advert = c2cAdvertMapper.selectById(order.getAdId());
            if (advert != null && advert.getStatus() == 2) {
                advert.setStatus(1);
                advert.setUpdateTime(new Date());
                c2cAdvertMapper.updateById(advert);
            }

            // Update appeal status
            appeal.setStatus(3); // RESOLVED - CANCEL
            appeal.setAdminId(adminId);
            appeal.setAdminResult(dto.getAdminResult());
            appeal.setUpdateTime(new Date());
            c2cOrderAppealMapper.updateById(appeal);

        } else {
            return "Invalid decision. Must be 'release' or 'cancel'";
        }

        return null;
    }

    @Override
    public TC2cOrderAppeal getAppealByOrderId(Long orderId) {
        return c2cOrderAppealMapper.selectByOrderId(orderId);
    }

    @Override
    public TC2cOrderAppeal getAppealByOrderId(Long orderId, Long userId) {
        TC2cOrderAppeal appeal = c2cOrderAppealMapper.selectByOrderId(orderId);
        if (appeal == null || !isAppealParty(appeal, userId)) {
            return null;
        }
        return appeal;
    }

    @Override
    public List<TC2cOrderAppeal> selectAppealList(TC2cOrderAppeal appeal) {
        return c2cOrderAppealMapper.selectAppealList(appeal);
    }

    @Override
    public List<TC2cAppealMessage> getAppealMessages(Long appealId) {
        return c2cAppealMessageMapper.selectByAppealId(appealId);
    }

    @Override
    public List<TC2cAppealMessage> getAppealMessages(Long appealId, Long userId) {
        TC2cOrderAppeal appeal = c2cOrderAppealMapper.selectById(appealId);
        if (appeal == null || !isAppealParty(appeal, userId)) {
            return Collections.emptyList();
        }
        return c2cAppealMessageMapper.selectByAppealId(appealId);
    }

    private boolean isAppealParty(TC2cOrderAppeal appeal, Long userId) {
        if (appeal == null || userId == null) {
            return false;
        }
        return userId.equals(appeal.getInitiatorId()) || userId.equals(appeal.getRespondentId());
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "usdt";
        }
        return symbol.trim().toLowerCase();
    }
}
