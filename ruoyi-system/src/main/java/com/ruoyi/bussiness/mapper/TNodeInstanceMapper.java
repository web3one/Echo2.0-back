package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TNodeInstance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 矿机实例 Mapper（admin 查询走 LambdaQueryWrapper，自定义聚合 SQL 放此处）
 */
public interface TNodeInstanceMapper extends BaseMapper<TNodeInstance> {

    /**
     * 统计用户当前 active 矿机价格总和（代理升级条件 min_active_node_value_usdt 校验依据）。
     * frozen/expired/cancelled 不计入。
     */
    @Select("SELECT COALESCE(SUM(price_usdt), 0) FROM t_node_instance "
            + "WHERE user_id = #{userId} AND status = 'active'")
    BigDecimal sumActiveValueByUserId(@Param("userId") Long userId);
}
