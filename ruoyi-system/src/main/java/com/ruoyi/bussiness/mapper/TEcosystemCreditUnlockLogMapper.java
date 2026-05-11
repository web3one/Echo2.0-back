package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TEcosystemCreditUnlockLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ecosystem_credit 解锁请求 Mapper
 */
public interface TEcosystemCreditUnlockLogMapper extends BaseMapper<TEcosystemCreditUnlockLog> {

    /** 用户 in_progress 占用量（A+B 都算，扣减可用 balance_locked） */
    @Select("SELECT COALESCE(SUM(amount_credit), 0) FROM t_ecosystem_credit_unlock_log " +
            "WHERE user_id = #{userId} AND status = 'in_progress'")
    java.math.BigDecimal sumInProgressAmountByUser(@Param("userId") Long userId);

    /** cron 扫所有 in_progress 行：trade_volume 检查交易量；xgt_lock 检查关联 plan 是否到期 */
    @Select("SELECT * FROM t_ecosystem_credit_unlock_log WHERE status = 'in_progress' " +
            "ORDER BY started_at ASC LIMIT 1000")
    List<TEcosystemCreditUnlockLog> selectAllInProgress();
}
