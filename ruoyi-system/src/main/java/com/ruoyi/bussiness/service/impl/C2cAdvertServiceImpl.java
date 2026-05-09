package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;
import com.ruoyi.bussiness.domain.dto.C2cAdvertCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import com.ruoyi.bussiness.mapper.TC2cAdvertMapper;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * C2C买卖广告Service业务层处理
 */
@Service
public class C2cAdvertServiceImpl extends ServiceImpl<TC2cAdvertMapper, TC2cAdvert> implements IC2cAdvertService {

    @Resource
    private TC2cAdvertMapper c2cAdvertMapper;

    @Resource
    private IC2cMerchantService c2cMerchantService;

    @Resource
    private IC2cEscrowService c2cEscrowService;

    @Resource
    private ITAppUserService tAppUserService;

    @Resource
    private IC2cConfigService c2cConfigService;

    @Resource
    private IC2cMerchantPaymentService c2cMerchantPaymentService;

    @Override
    @Transactional
    public String createAdvert(Long userId, C2cAdvertCreateDTO dto) {
        if (c2cConfigService.getConfigIntValue("c2c_enabled", 1) != 1) {
            return "C2C trading is disabled";
        }

        // Get merchant, check status
        TC2cMerchant merchant = c2cMerchantService.getMerchantByUserId(userId);
        if (merchant == null || merchant.getStatus() != 1) {
            return "You are not an approved merchant";
        }

        if (dto.getAdType() == null || (dto.getAdType() != 1 && dto.getAdType() != 2)) {
            return "Invalid advert type";
        }
        String symbol = normalizeSymbol(dto.getCryptoCurrency());
        if (!"usdt".equals(symbol)) {
            return "Only USDT is supported";
        }
        dto.setCryptoCurrency(symbol);

        // Validate fields
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "Price must be greater than 0";
        }
        if (dto.getTotalAmount() == null || dto.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Total amount must be greater than 0";
        }
        if (dto.getMinLimit() == null || dto.getMaxLimit() == null
                || dto.getMinLimit().compareTo(BigDecimal.ZERO) <= 0
                || dto.getMaxLimit().compareTo(dto.getMinLimit()) < 0) {
            return "Invalid trade limits";
        }

        TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            return "User not found";
        }

        List<Long> paymentIds = parsePaymentIds(dto.getPaymentMethodIds());
        if (paymentIds.isEmpty()) {
            return "Please select payment method";
        }
        if (!isUserPaymentIds(userId, paymentIds)) {
            return "Invalid payment method";
        }

        String serialId = "P2P" + OrderUtils.generateOrderNum();

        // If SELL advert, freeze crypto
        if (dto.getAdType() == 1) {
            // Verify fund password
            TAppUserDetail detail = tAppUserService.selectUserDetailByUserId(userId);
            if (detail == null || detail.getUserTardPwd() == null) {
                return "Please set fund password first";
            }
            if (dto.getFundPassword() == null || !SecurityUtils.matchesPassword(dto.getFundPassword(), detail.getUserTardPwd())) {
                return "Incorrect fund password";
            }
            // Freeze crypto asset
            try {
                c2cEscrowService.freezeAsset(userId, symbol, dto.getTotalAmount(),
                        serialId, user.getAdminParentIds());
            } catch (RuntimeException e) {
                return e.getMessage();
            }
        }

        // Generate advert number
        String adNo = "AD" + OrderUtils.generateOrderNum();

        // Create advert
        TC2cAdvert advert = new TC2cAdvert();
        advert.setAdNo(adNo);
        advert.setMerchantId(merchant.getId());
        advert.setUserId(userId);
        advert.setAdType(dto.getAdType());
        advert.setCryptoCurrency(symbol);
        advert.setFiatCurrency("USD");
        advert.setPriceType(1); // Fixed price
        advert.setPrice(dto.getPrice());
        advert.setTotalAmount(dto.getTotalAmount());
        advert.setRemainingAmount(dto.getTotalAmount());
        advert.setMinLimit(dto.getMinLimit());
        advert.setMaxLimit(dto.getMaxLimit());
        int defaultPaymentTimeLimit = c2cConfigService.getConfigIntValue("payment_timeout_minutes", 15);
        advert.setPaymentTimeLimit(dto.getPaymentTimeLimit() != null ? dto.getPaymentTimeLimit() : defaultPaymentTimeLimit);
        advert.setPaymentMethodIds(dto.getPaymentMethodIds());
        advert.setAutoReplyMsg(dto.getAutoReplyMsg());
        advert.setTradeTerms(dto.getTradeTerms());
        advert.setRequireKyc(0);
        advert.setStatus(1); // Online
        advert.setAdminParentIds(user.getAdminParentIds());
        advert.setCreateTime(new Date());

        c2cAdvertMapper.insert(advert);
        return null;
    }

    @Override
    @Transactional
    public int offlineAdvert(Long id, Long userId) {
        TC2cAdvert advert = c2cAdvertMapper.selectById(id);
        if (advert == null || !advert.getUserId().equals(userId)) {
            return 0;
        }
        if (advert.getStatus() != 1 && advert.getStatus() != 2) {
            return 0;
        }

        // If SELL advert and remaining > 0, unfreeze remaining crypto
        if (advert.getAdType() == 1 && advert.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
            String serialId = "P2P" + OrderUtils.generateOrderNum();
            c2cEscrowService.unfreezeAsset(userId, normalizeSymbol(advert.getCryptoCurrency()), advert.getRemainingAmount(),
                    serialId, user.getAdminParentIds());
        }

        advert.setStatus(0);
        advert.setUpdateTime(new Date());
        return c2cAdvertMapper.updateById(advert);
    }

    @Override
    @Transactional
    public int onlineAdvert(Long id, Long userId) {
        TC2cAdvert advert = c2cAdvertMapper.selectById(id);
        if (advert == null || !advert.getUserId().equals(userId)) {
            return 0;
        }
        if (advert.getStatus() != 0) {
            return 0;
        }
        if (advert.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        // If SELL advert, re-freeze remaining amount
        if (advert.getAdType() == 1) {
            TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
            String serialId = "P2P" + OrderUtils.generateOrderNum();
            try {
                c2cEscrowService.freezeAsset(userId, normalizeSymbol(advert.getCryptoCurrency()), advert.getRemainingAmount(),
                        serialId, user.getAdminParentIds());
            } catch (RuntimeException e) {
                return 0;
            }
        }

        advert.setStatus(1);
        advert.setUpdateTime(new Date());
        return c2cAdvertMapper.updateById(advert);
    }

    @Override
    public int adminDisableAdvert(Long id) {
        TC2cAdvert advert = c2cAdvertMapper.selectById(id);
        if (advert == null) {
            return 0;
        }
        // If SELL advert and online with remaining, unfreeze
        if (advert.getStatus() == 1 && advert.getAdType() == 1
                && advert.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            TAppUser user = tAppUserService.selectTAppUserByUserId(advert.getUserId());
            String serialId = "P2P" + OrderUtils.generateOrderNum();
            c2cEscrowService.unfreezeAsset(advert.getUserId(), normalizeSymbol(advert.getCryptoCurrency()), advert.getRemainingAmount(),
                    serialId, user.getAdminParentIds());
        }
        advert.setStatus(3);
        advert.setUpdateTime(new Date());
        return c2cAdvertMapper.updateById(advert);
    }

    @Override
    @Transactional
    public int adminEnableAdvert(Long id) {
        TC2cAdvert advert = c2cAdvertMapper.selectById(id);
        if (advert == null || advert.getStatus() != 3) {
            return 0;
        }
        if (advert.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        // If SELL advert, re-freeze remaining
        if (advert.getAdType() == 1) {
            TAppUser user = tAppUserService.selectTAppUserByUserId(advert.getUserId());
            String serialId = "P2P" + OrderUtils.generateOrderNum();
            try {
                c2cEscrowService.freezeAsset(advert.getUserId(), normalizeSymbol(advert.getCryptoCurrency()), advert.getRemainingAmount(),
                        serialId, user.getAdminParentIds());
            } catch (RuntimeException e) {
                return 0;
            }
        }
        advert.setStatus(1);
        advert.setUpdateTime(new Date());
        return c2cAdvertMapper.updateById(advert);
    }

    @Override
    public List<C2cAdvertVO> listPublicAdverts(TC2cAdvert query) {
        if (query == null) {
            query = new TC2cAdvert();
        }
        query.setCryptoCurrency(normalizeSymbol(query.getCryptoCurrency()));
        return c2cAdvertMapper.selectAdvertVOList(query);
    }

    @Override
    public C2cAdvertVO getPublicAdvertDetail(Long id) {
        C2cAdvertVO vo = c2cAdvertMapper.selectAdvertVOById(id);
        if (vo != null) {
            vo.setPaymentMethods(getPaymentMethods(vo.getPaymentMethodIds()));
        }
        return vo;
    }

    @Override
    public List<TC2cAdvert> listMyAdverts(Long userId) {
        return c2cAdvertMapper.selectList(new LambdaQueryWrapper<TC2cAdvert>()
                .eq(TC2cAdvert::getUserId, userId)
                .orderByDesc(TC2cAdvert::getCreateTime));
    }

    @Override
    public List<TC2cAdvert> selectAdvertList(TC2cAdvert advert) {
        return c2cAdvertMapper.selectAdvertList(advert);
    }

    @Override
    public List<C2cAdvertVO> selectAdminAdvertVOList(TC2cAdvert advert) {
        if (advert != null && advert.getCryptoCurrency() != null && !advert.getCryptoCurrency().trim().isEmpty()) {
            advert.setCryptoCurrency(normalizeSymbol(advert.getCryptoCurrency()));
        }
        return c2cAdvertMapper.selectAdminAdvertVOList(advert);
    }

    @Override
    public TC2cAdvert getAdvertDetail(Long id) {
        return c2cAdvertMapper.selectById(id);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "usdt";
        }
        return symbol.trim().toLowerCase();
    }

    private List<Long> parsePaymentIds(String paymentMethodIds) {
        List<Long> ids = new ArrayList<>();
        if (paymentMethodIds == null || paymentMethodIds.trim().isEmpty()) {
            return ids;
        }
        for (String item : paymentMethodIds.split(",")) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(item.trim()));
            } catch (NumberFormatException ignored) {
                return new ArrayList<>();
            }
        }
        return ids;
    }

    private boolean isUserPaymentIds(Long userId, List<Long> paymentIds) {
        List<TC2cMerchantPayment> payments = c2cMerchantPaymentService.listEnabledByUser(userId);
        if (payments == null || payments.isEmpty()) {
            return false;
        }
        for (Long id : paymentIds) {
            boolean matched = false;
            for (TC2cMerchantPayment payment : payments) {
                if (payment.getId().equals(id)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private List<TC2cMerchantPayment> getPaymentMethods(String paymentMethodIds) {
        List<TC2cMerchantPayment> result = new ArrayList<>();
        for (Long id : parsePaymentIds(paymentMethodIds)) {
            TC2cMerchantPayment payment = c2cMerchantPaymentService.getById(id);
            if (payment != null && Integer.valueOf(1).equals(payment.getIsEnabled())) {
                result.add(payment);
            }
        }
        return result;
    }
}
