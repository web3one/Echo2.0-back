package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TC2cMerchantMapper extends BaseMapper<TC2cMerchant> {

    List<TC2cMerchant> selectMerchantList(TC2cMerchant merchant);

    TC2cMerchant selectByUserId(@Param("userId") Long userId);

    int updateMerchantStats(@Param("id") Long id, @Param("totalOrders") int totalOrders,
                            @Param("totalVolume") java.math.BigDecimal totalVolume,
                            @Param("completionRate") java.math.BigDecimal completionRate);
}
