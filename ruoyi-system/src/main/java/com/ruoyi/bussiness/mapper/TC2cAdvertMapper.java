package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TC2cAdvertMapper extends BaseMapper<TC2cAdvert> {

    List<TC2cAdvert> selectAdvertList(TC2cAdvert advert);

    List<C2cAdvertVO> selectAdvertVOList(TC2cAdvert advert);

    List<C2cAdvertVO> selectAdminAdvertVOList(TC2cAdvert advert);

    C2cAdvertVO selectAdvertVOById(@Param("id") Long id);

    /** 原子扣减剩余量，返回影响行数(0=失败,1=成功) */
    int deductRemainingAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** 恢复剩余量 */
    int restoreRemainingAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
