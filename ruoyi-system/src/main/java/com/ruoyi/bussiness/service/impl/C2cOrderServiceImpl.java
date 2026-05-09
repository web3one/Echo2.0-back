package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.*;
import com.ruoyi.bussiness.domain.dto.C2cOrderCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cOrderCreateResult;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import com.ruoyi.bussiness.mapper.TC2cAdvertMapper;
import com.ruoyi.bussiness.mapper.TC2cMerchantMapper;
import com.ruoyi.bussiness.mapper.TC2cOrderMapper;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * C2C交易订单Service业务层处理
 */
@Service
public class C2cOrderServiceImpl extends ServiceImpl<TC2cOrderMapper, TC2cOrder> implements IC2cOrderService {

    @Resource
    private TC2cOrderMapper c2cOrderMapper;

    @Resource
    private TC2cAdvertMapper c2cAdvertMapper;

    @Resource
    private IC2cMerchantService c2cMerchantService;

    @Resource
    private IC2cEscrowService c2cEscrowService;

    @Resource
    private ITAppUserService tAppUserService;

    @Resource
    private ITAppUserDetailService tAppUserDetailService;

    @Resource
    private ITAppWalletRecordService tAppWalletRecordService;

    @Resource
    private IC2cConfigService c2cConfigService;

    @Resource
    private IC2cMerchantPaymentService c2cMerchantPaymentService;

    @Resource
    private TC2cMerchantMapper c2cMerchantMapper;

    @Override
    @Transactional
    public C2cOrderCreateResult createOrder(Long userId, C2cOrderCreateDTO dto) {
        if (c2cConfigService.getConfigIntValue("c2c_enabled", 1) != 1) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.disabled"));
        }

        // 1. Get advert and check status
        TC2cAdvert advert = c2cAdvertMapper.selectById(dto.getAdId());
        if (advert == null || advert.getStatus() != 1) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.advert.unavailable"));
        }
        advert.setCryptoCurrency(normalizeSymbol(advert.getCryptoCurrency()));

        // 2. Can't self-trade
        if (advert.getUserId().equals(userId)) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.advert.self_trade"));
        }

        // 3. Get current user and check status
        TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.user.not_found"));
        }
        if ("2".equals(user.getIsFreeze())) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.user.frozen"));
        }

        // 4. Check KYC if required（仅校验实名认证 advanced 通过；"1"=EXAMINATION_PASSED）
        if (advert.getRequireKyc() != null && advert.getRequireKyc() == 1) {
            TAppUserDetail detail = tAppUserService.selectUserDetailByUserId(userId);
            if (detail == null) {
                return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.kyc_required"));
            }
            boolean kycPassed = "1".equals(detail.getAuditStatusAdvanced());
            if (!kycPassed) {
                return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.kyc_required"));
            }
        }

        // 5. Check active orders limit
        int activeOrders = c2cOrderMapper.countActiveOrdersByUser(userId);
        if (activeOrders >= 5) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.too_many_active"));
        }

        // 6. Calculate amounts
        BigDecimal fiatAmount = dto.getFiatAmount();
        BigDecimal cryptoAmount = dto.getCryptoAmount();

        if (fiatAmount != null && fiatAmount.compareTo(BigDecimal.ZERO) > 0) {
            cryptoAmount = fiatAmount.divide(advert.getPrice(), 8, RoundingMode.HALF_UP);
        } else if (cryptoAmount != null && cryptoAmount.compareTo(BigDecimal.ZERO) > 0) {
            fiatAmount = cryptoAmount.multiply(advert.getPrice()).setScale(2, RoundingMode.HALF_UP);
        } else {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.amount_required"));
        }

        // 7. Validate limits
        if (fiatAmount.compareTo(advert.getMinLimit()) < 0) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.below_min"));
        }
        if (fiatAmount.compareTo(advert.getMaxLimit()) > 0) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.above_max"));
        }

        // 8. Validate remaining
        if (advert.getRemainingAmount().compareTo(cryptoAmount) < 0) {
            return C2cOrderCreateResult.error(MessageUtils.message("c2c.order.insufficient_remaining"));
        }

        if (dto.getPaymentMethodId() == null) {
            return C2cOrderCreateResult.error("Please select a valid payment method");
        }
        if (advert.getAdType() == 1) {
            if (!isAdvertPaymentMethod(advert, dto.getPaymentMethodId())) {
                return C2cOrderCreateResult.error("Please select a valid seller payment method");
            }
        } else {
            if (!isUserPaymentMethod(userId, dto.getPaymentMethodId())) {
                return C2cOrderCreateResult.error("Please select one of your enabled payment methods");
            }
        }

        // 9a. Atomically deduct advert remaining
        int deducted = c2cAdvertMapper.deductRemainingAmount(advert.getId(), cryptoAmount);
        if (deducted == 0) {
            return C2cOrderCreateResult.error("Advert remaining amount insufficient, please try again");
        }

        // 9b. Determine buyer/seller
        Long buyerId;
        Long sellerId;
        Long takerId = userId;
        String orderNo = "P2P" + OrderUtils.generateOrderNum();

        if (advert.getAdType() == 1) {
            // SELL advert: ad owner is seller, current user is buyer
            buyerId = userId;
            sellerId = advert.getUserId();
            // Crypto already frozen at ad creation level — no additional freeze needed
        } else {
            // BUY advert: ad owner is buyer, current user is seller
            buyerId = advert.getUserId();
            sellerId = userId;
            // Freeze crypto from seller (current user)
            try {
                c2cEscrowService.freezeAsset(userId, advert.getCryptoCurrency(), cryptoAmount,
                        orderNo, user.getAdminParentIds());
            } catch (RuntimeException e) {
                // Restore advert remaining
                c2cAdvertMapper.restoreRemainingAmount(advert.getId(), cryptoAmount);
                return C2cOrderCreateResult.error(e.getMessage());
            }
        }

        // 9d. Create order
        TC2cOrder order = new TC2cOrder();
        order.setOrderNo(orderNo);
        order.setAdId(advert.getId());
        order.setAdNo(advert.getAdNo());
        order.setAdType(advert.getAdType());
        order.setCryptoCurrency(advert.getCryptoCurrency());
        order.setFiatCurrency(advert.getFiatCurrency());
        order.setPrice(advert.getPrice());
        order.setCryptoAmount(cryptoAmount);
        order.setFiatAmount(fiatAmount);
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setMerchantId(advert.getMerchantId());
        order.setTakerId(takerId);
        order.setPaymentMethodId(dto.getPaymentMethodId());
        order.setStatus(0); // CREATED / pending payment
        int paymentTimeLimit = advert.getPaymentTimeLimit() != null ? advert.getPaymentTimeLimit() : 15;
        order.setTimeoutAt(new Date(System.currentTimeMillis() + paymentTimeLimit * 60 * 1000L));
        order.setAdminParentIds(user.getAdminParentIds());
        order.setCreateTime(new Date());

        c2cOrderMapper.insert(order);

        // 9f. If advert remaining becomes 0, update advert status to DEPLETED
        TC2cAdvert updatedAdvert = c2cAdvertMapper.selectById(advert.getId());
        if (updatedAdvert.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            updatedAdvert.setStatus(2); // DEPLETED
            updatedAdvert.setUpdateTime(new Date());
            c2cAdvertMapper.updateById(updatedAdvert);
        }

        return C2cOrderCreateResult.success(order.getId(), order.getOrderNo());
    }

    @Override
    public String markPaid(Long orderId, Long userId) {
        TC2cOrder order = c2cOrderMapper.selectById(orderId);
        if (order == null) {
            return "Order not found";
        }
        if (order.getStatus() != 0) {
            return "Order status does not allow this operation";
        }
        if (!order.getBuyerId().equals(userId)) {
            return "Only buyer can mark as paid";
        }

        int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                .eq(TC2cOrder::getId, orderId)
                .eq(TC2cOrder::getStatus, 0)
                .eq(TC2cOrder::getBuyerId, userId)
                .set(TC2cOrder::getStatus, 1)
                .set(TC2cOrder::getPayTime, new Date())
                .set(TC2cOrder::getUpdateTime, new Date()));
        if (updated == 0) {
            return "Order status has changed, please refresh";
        }
        return null;
    }

    @Override
    @Transactional
    public String confirmRelease(Long orderId, Long userId, String fundPassword) {
        TC2cOrder order = c2cOrderMapper.selectById(orderId);
        if (order == null) {
            return "Order not found";
        }
        if (order.getStatus() != 1) {
            return "Order status does not allow this operation";
        }
        if (!order.getSellerId().equals(userId)) {
            return "Only seller can confirm release";
        }

        // Verify fund password
        TAppUserDetail detail = tAppUserService.selectUserDetailByUserId(userId);
        if (detail == null || detail.getUserTardPwd() == null) {
            return "Fund password not set";
        }
        if (!SecurityUtils.matchesPassword(fundPassword, detail.getUserTardPwd())) {
            return "Incorrect fund password";
        }

        // Get admin parent IDs for both parties
        TAppUser seller = tAppUserService.selectTAppUserByUserId(order.getSellerId());
        TAppUser buyer = tAppUserService.selectTAppUserByUserId(order.getBuyerId());
        String sellerAdminParentIds = seller != null ? seller.getAdminParentIds() : "";
        String buyerAdminParentIds = buyer != null ? buyer.getAdminParentIds() : "";

        int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                .eq(TC2cOrder::getId, orderId)
                .eq(TC2cOrder::getStatus, 1)
                .eq(TC2cOrder::getSellerId, userId)
                .set(TC2cOrder::getStatus, 2)
                .set(TC2cOrder::getReleaseTime, new Date())
                .set(TC2cOrder::getUpdateTime, new Date()));
        if (updated == 0) {
            return "Order status has changed, please refresh";
        }

        // Release asset from seller to buyer
        String serialId = order.getOrderNo();
        c2cEscrowService.releaseAsset(order.getSellerId(), order.getBuyerId(),
                normalizeSymbol(order.getCryptoCurrency()), order.getCryptoAmount(),
                serialId, sellerAdminParentIds, buyerAdminParentIds);

        // Update merchant stats
        TC2cMerchant merchant = c2cMerchantService.getById(order.getMerchantId());
        if (merchant != null) {
            int newTotalOrders = (merchant.getTotalOrders() != null ? merchant.getTotalOrders() : 0) + 1;
            BigDecimal newTotalVolume = (merchant.getTotalVolume() != null ? merchant.getTotalVolume() : BigDecimal.ZERO)
                    .add(order.getFiatAmount());
            BigDecimal completionRate = merchant.getCompletionRate() != null ? merchant.getCompletionRate() : BigDecimal.ZERO;
            c2cMerchantMapper.updateMerchantStats(merchant.getId(), newTotalOrders, newTotalVolume, completionRate);
        }

        return null;
    }

    @Override
    @Transactional
    public String cancelOrder(Long orderId, Long userId) {
        TC2cOrder order = c2cOrderMapper.selectById(orderId);
        if (order == null) {
            return "Order not found";
        }
        if (order.getStatus() != 0) {
            return "Only pending payment orders can be cancelled";
        }

        // Determine role
        String role;
        if (order.getBuyerId().equals(userId)) {
            role = "buyer";
        } else if (order.getSellerId().equals(userId)) {
            role = "seller";
        } else {
            return "You are not a party of this order";
        }

        // Check daily cancel limit
        int dailyCancelLimit = c2cConfigService.getConfigIntValue("max_daily_cancel", 3);
        int dailyCancels = c2cOrderMapper.countDailyCancelByUser(userId, role);
        if (dailyCancels >= dailyCancelLimit) {
            return "Daily cancel limit reached";
        }

        int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                .eq(TC2cOrder::getId, orderId)
                .eq(TC2cOrder::getStatus, 0)
                .set(TC2cOrder::getStatus, 3)
                .set(TC2cOrder::getCancelTime, new Date())
                .set(TC2cOrder::getCancelledBy, role)
                .set(TC2cOrder::getCancelReason, "Cancelled by " + role)
                .set(TC2cOrder::getUpdateTime, new Date()));
        if (updated == 0) {
            return "Order status has changed, please refresh";
        }

        // Handle asset operations based on ad type
        if (order.getAdType() == 1) {
            // SELL advert: crypto was frozen at ad level, just restore advert remaining
            c2cAdvertMapper.restoreRemainingAmount(order.getAdId(), order.getCryptoAmount());
        } else {
            // BUY advert: unfreeze crypto from seller + restore advert remaining
            TAppUser seller = tAppUserService.selectTAppUserByUserId(order.getSellerId());
            String sellerAdminParentIds = seller != null ? seller.getAdminParentIds() : "";
            c2cEscrowService.unfreezeAsset(order.getSellerId(), normalizeSymbol(order.getCryptoCurrency()), order.getCryptoAmount(),
                    order.getOrderNo(), sellerAdminParentIds);
            c2cAdvertMapper.restoreRemainingAmount(order.getAdId(), order.getCryptoAmount());
        }

        // Update advert status back to online if it was depleted
        TC2cAdvert advert = c2cAdvertMapper.selectById(order.getAdId());
        if (advert != null && advert.getStatus() == 2) {
            advert.setStatus(1);
            advert.setUpdateTime(new Date());
            c2cAdvertMapper.updateById(advert);
        }

        return null;
    }

    @Override
    @Transactional
    public void handleTimeout(TC2cOrder order) {
        // Re-check status for idempotency
        TC2cOrder current = c2cOrderMapper.selectById(order.getId());
        if (current == null || current.getStatus() != 0) {
            return;
        }

        int updated = c2cOrderMapper.update(null, new LambdaUpdateWrapper<TC2cOrder>()
                .eq(TC2cOrder::getId, current.getId())
                .eq(TC2cOrder::getStatus, 0)
                .set(TC2cOrder::getStatus, 3)
                .set(TC2cOrder::getCancelTime, new Date())
                .set(TC2cOrder::getCancelledBy, "system")
                .set(TC2cOrder::getCancelReason, "Payment timeout")
                .set(TC2cOrder::getUpdateTime, new Date()));
        if (updated == 0) {
            return;
        }

        // Same cancel logic as cancelOrder but with system as canceller
        if (current.getAdType() == 1) {
            // SELL advert: restore advert remaining
            c2cAdvertMapper.restoreRemainingAmount(current.getAdId(), current.getCryptoAmount());
        } else {
            // BUY advert: unfreeze from seller + restore advert remaining
            TAppUser seller = tAppUserService.selectTAppUserByUserId(current.getSellerId());
            String sellerAdminParentIds = seller != null ? seller.getAdminParentIds() : "";
            c2cEscrowService.unfreezeAsset(current.getSellerId(), normalizeSymbol(current.getCryptoCurrency()), current.getCryptoAmount(),
                    current.getOrderNo(), sellerAdminParentIds);
            c2cAdvertMapper.restoreRemainingAmount(current.getAdId(), current.getCryptoAmount());
        }

        // Restore advert status if depleted
        TC2cAdvert advert = c2cAdvertMapper.selectById(current.getAdId());
        if (advert != null && advert.getStatus() == 2) {
            advert.setStatus(1);
            advert.setUpdateTime(new Date());
            c2cAdvertMapper.updateById(advert);
        }

    }

    @Override
    public C2cOrderVO getOrderDetail(Long orderId, Long userId) {
        TC2cOrder order = c2cOrderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        // Check user is a party of this order
        if (userId != null && !order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return null;
        }

        C2cOrderVO vo = new C2cOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setAdId(order.getAdId());
        vo.setAdNo(order.getAdNo());
        vo.setAdType(order.getAdType());
        vo.setCryptoCurrency(order.getCryptoCurrency());
        vo.setFiatCurrency(order.getFiatCurrency());
        vo.setPrice(order.getPrice());
        vo.setCryptoAmount(order.getCryptoAmount());
        vo.setFiatAmount(order.getFiatAmount());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setMerchantId(order.getMerchantId());
        vo.setTakerId(order.getTakerId());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setReleaseTime(order.getReleaseTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setTimeoutAt(order.getTimeoutAt());
        vo.setCancelReason(order.getCancelReason());
        vo.setCancelledBy(order.getCancelledBy());

        // Set buyer/seller names
        TAppUser buyer = tAppUserService.selectTAppUserByUserId(order.getBuyerId());
        if (buyer != null) {
            vo.setBuyerName(buyer.getLoginName());
        }
        TAppUser seller = tAppUserService.selectTAppUserByUserId(order.getSellerId());
        if (seller != null) {
            vo.setSellerName(seller.getLoginName());
        }

        // Set merchant nickname
        TC2cMerchant merchant = c2cMerchantService.getById(order.getMerchantId());
        if (merchant != null) {
            vo.setMerchantNickname(merchant.getNickname());
        }

        // Set seller payment method
        if (order.getPaymentMethodId() != null) {
            TC2cMerchantPayment payment = c2cMerchantPaymentService.getById(order.getPaymentMethodId());
            vo.setSellerPayment(payment);
        }

        // Set auto reply message from advert
        TC2cAdvert advert = c2cAdvertMapper.selectById(order.getAdId());
        if (advert != null) {
            vo.setAutoReplyMsg(advert.getAutoReplyMsg());
        }

        return vo;
    }

    @Override
    public List<TC2cOrder> listMyOrders(Long userId, List<Integer> statusList) {
        LambdaQueryWrapper<TC2cOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(TC2cOrder::getBuyerId, userId).or().eq(TC2cOrder::getSellerId, userId));
        if (statusList != null && !statusList.isEmpty()) {
            wrapper.in(TC2cOrder::getStatus, statusList);
        }
        wrapper.orderByDesc(TC2cOrder::getCreateTime);
        return c2cOrderMapper.selectList(wrapper);
    }

    @Override
    public List<TC2cOrder> selectOrderList(TC2cOrder order) {
        return c2cOrderMapper.selectOrderList(order);
    }

    @Override
    public List<C2cOrderVO> selectOrderVOList(TC2cOrder order) {
        return c2cOrderMapper.selectOrderVOList(order);
    }

    @Override
    public List<TC2cOrder> selectTimeoutOrders() {
        return c2cOrderMapper.selectTimeoutOrders();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "usdt";
        }
        return symbol.trim().toLowerCase();
    }

    private boolean isAdvertPaymentMethod(TC2cAdvert advert, Long paymentMethodId) {
        if (advert.getPaymentMethodIds() == null || advert.getPaymentMethodIds().trim().isEmpty()) {
            return false;
        }
        for (String item : advert.getPaymentMethodIds().split(",")) {
            if (item != null && item.trim().equals(String.valueOf(paymentMethodId))) {
                TC2cMerchantPayment payment = c2cMerchantPaymentService.getById(paymentMethodId);
                return payment != null
                        && advert.getUserId().equals(payment.getUserId())
                        && Integer.valueOf(1).equals(payment.getIsEnabled());
            }
        }
        return false;
    }

    private boolean isUserPaymentMethod(Long userId, Long paymentMethodId) {
        TC2cMerchantPayment payment = c2cMerchantPaymentService.getById(paymentMethodId);
        return payment != null
                && userId.equals(payment.getUserId())
                && Integer.valueOf(1).equals(payment.getIsEnabled());
    }
}
