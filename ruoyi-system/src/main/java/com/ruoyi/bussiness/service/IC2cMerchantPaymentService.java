package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;
import java.util.List;

public interface IC2cMerchantPaymentService extends IService<TC2cMerchantPayment> {
    int addPayment(TC2cMerchantPayment payment);
    int updatePayment(TC2cMerchantPayment payment);
    int deletePayment(Long id, Long userId);
    List<TC2cMerchantPayment> listByMerchant(Long merchantId);
    List<TC2cMerchantPayment> listEnabledByMerchant(Long merchantId);
    List<TC2cMerchantPayment> listByUser(Long userId);
    List<TC2cMerchantPayment> listEnabledByUser(Long userId);
}
