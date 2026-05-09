package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TNodeInstance;

/**
 * 矿机实例服务（金矿 admin 查询/冻结/解冻；不允许物理删除）
 */
public interface INodeInstanceService {

    /**
     * 分页查询。query 字段非空才参与过滤：userId / status / levelCode。
     */
    IPage<TNodeInstance> page(int pageNum, int pageSize, TNodeInstance query);

    TNodeInstance getById(Long id);

    /**
     * 冻结矿机实例（不发奖、不计入有效权益矿机选择）。
     */
    TNodeInstance freeze(Long instanceId, Long operatorAdminId, String reason);

    /**
     * 解冻矿机实例（恢复发奖资格）。
     */
    TNodeInstance unfreeze(Long instanceId, Long operatorAdminId, String reason);
}
