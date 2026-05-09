package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;
import com.ruoyi.bussiness.mapper.TC2cMerchantPaymentMapper;
import com.ruoyi.bussiness.service.IC2cMerchantPaymentService;
import com.ruoyi.bussiness.service.IC2cMerchantService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * C2C商家收付款方式Service业务层处理
 */
@Service
public class C2cMerchantPaymentServiceImpl extends ServiceImpl<TC2cMerchantPaymentMapper, TC2cMerchantPayment> implements IC2cMerchantPaymentService {

    @Resource
    private TC2cMerchantPaymentMapper c2cMerchantPaymentMapper;

    @Resource
    private IC2cMerchantService c2cMerchantService;

    @Override
    public int addPayment(TC2cMerchantPayment payment) {
        if (payment.getUserId() == null) {
            return 0;
        }
        if (payment.getMerchantId() == null) {
            payment.setMerchantId(0L);
        }
        if (!Long.valueOf(0L).equals(payment.getMerchantId())) {
            TC2cMerchant merchant = c2cMerchantService.getById(payment.getMerchantId());
            if (merchant == null || !merchant.getUserId().equals(payment.getUserId())) {
                return 0;
            }
        }
        payment.setCreateTime(new Date());
        payment.setPaymentType(payment.getPaymentType() != null ? payment.getPaymentType() : 1);
        payment.setIsEnabled(1);
        return c2cMerchantPaymentMapper.insert(payment);
    }

    @Override
    public int updatePayment(TC2cMerchantPayment payment) {
        // Validate ownership
        TC2cMerchantPayment existing = c2cMerchantPaymentMapper.selectById(payment.getId());
        if (existing == null || !existing.getUserId().equals(payment.getUserId())) {
            return 0;
        }
        payment.setUpdateTime(new Date());
        return c2cMerchantPaymentMapper.updateById(payment);
    }

    @Override
    public int deletePayment(Long id, Long userId) {
        // Validate ownership
        TC2cMerchantPayment existing = c2cMerchantPaymentMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return 0;
        }
        return c2cMerchantPaymentMapper.deleteById(id);
    }

    @Override
    public List<TC2cMerchantPayment> listByMerchant(Long merchantId) {
        return c2cMerchantPaymentMapper.selectByMerchantId(merchantId);
    }

    @Override
    public List<TC2cMerchantPayment> listEnabledByMerchant(Long merchantId) {
        return c2cMerchantPaymentMapper.selectEnabledByMerchantId(merchantId);
    }

    @Override
    public List<TC2cMerchantPayment> listByUser(Long userId) {
        return c2cMerchantPaymentMapper.selectByUserId(userId);
    }

    @Override
    public List<TC2cMerchantPayment> listEnabledByUser(Long userId) {
        return c2cMerchantPaymentMapper.selectEnabledByUserId(userId);
    }
}
