package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.MyXgtLocksVO;

/**
 * XGT 余额 / 锁仓查询（用户端，PRD §17 GET /xgt/locks/my）。
 *
 * @date 2026-05-09
 */
public interface IXgtQueryService {

    /**
     * 查询当前用户的 XGT 余额 + 锁仓计划列表。
     *
     * 返回包含 status 全集（locked / releasable / completed / frozen），按 lockedAt desc 排序。
     */
    MyXgtLocksVO myLocks(Long userId);
}
