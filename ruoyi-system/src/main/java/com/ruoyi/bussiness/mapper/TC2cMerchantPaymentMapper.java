package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cMerchantPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TC2cMerchantPaymentMapper extends BaseMapper<TC2cMerchantPayment> {

    List<TC2cMerchantPayment> selectByMerchantId(@Param("merchantId") Long merchantId);

    List<TC2cMerchantPayment> selectEnabledByMerchantId(@Param("merchantId") Long merchantId);

    List<TC2cMerchantPayment> selectByUserId(@Param("userId") Long userId);

    List<TC2cMerchantPayment> selectEnabledByUserId(@Param("userId") Long userId);
}
