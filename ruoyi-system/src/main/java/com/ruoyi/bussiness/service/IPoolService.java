package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TPoolBalance;

import java.math.BigDecimal;
import java.util.List;

/**
 * 底池查询/校验服务（Phase 5/6 共用）。
 *
 * 注：底池"增减"由 chain.DepositAutoIngestService.addPoolBalance 处理（admin 内部）。
 * 本服务只做查询和校验，admin/api 都能调。
 */
public interface IPoolService {

    /**
     * 提现提交前的底池校验。
     * @return null = OK；非 null = 错误消息
     */
    String validateForWithdraw(String symbol, String chain, BigDecimal amount);

    /** 单条底池查询 */
    TPoolBalance getBalance(String symbol, String chain);

    /** 全表（H5 / admin 展示用） */
    List<TPoolBalance> listAll();

    /** H5 端只展示 display_in_h5=1 的底池 */
    List<TPoolBalance> listForH5();

    /**
     * 管理员手工调整底池（必填备注，全程留痕）。
     * @param delta 正数=加，负数=减
     */
    void adjust(String symbol, String chain, java.math.BigDecimal delta,
                String operator, String remark);

    /** 流水分页查询 */
    com.baomidou.mybatisplus.core.metadata.IPage<com.ruoyi.bussiness.domain.TPoolLog> pageLog(
            String symbol, String chain, String changeType, int pageNum, int pageSize);
}
