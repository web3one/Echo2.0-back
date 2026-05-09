package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.dto.C2cMerchantApplyDTO;
import java.util.List;

public interface IC2cMerchantService extends IService<TC2cMerchant> {
    String applyMerchant(Long userId, C2cMerchantApplyDTO dto);
    int approveMerchant(Long id);
    int rejectMerchant(Long id, String reason);
    int disableMerchant(Long id);
    int enableMerchant(Long id);
    TC2cMerchant getMerchantByUserId(Long userId);
    List<TC2cMerchant> selectMerchantList(TC2cMerchant merchant);
}
