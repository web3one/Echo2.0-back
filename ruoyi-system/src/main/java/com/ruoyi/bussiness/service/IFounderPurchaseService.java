package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.dto.FounderPurchaseDTO;
import com.ruoyi.bussiness.domain.vo.FounderPurchaseResultVO;
import com.ruoyi.bussiness.domain.vo.FounderStatusVO;

/**
 * 创世合伙人席位购买服务（金矿 Phase 2 A 路线第二组）。
 *
 *  - 决策 4 直接扣不审核：用户点购 → 校验资金密码 → 现货 USDT ≥200k → 行级锁
 *    占第一个 available 席 → 扣款 → 写流水。
 *  - 决策 3 不动等级：本服务**不**自动改 t_agent_status；V5 提升走 admin 直改通道。
 *  - 决策 5：XGT 锁仓本轮不写，xgt_lock_plan_id 留 NULL，C 路线再补。
 *
 * 接口：
 *   GET /api/founder/status      查 49 席矩阵 + 我的席位
 *   POST /api/founder/purchase   购买席位
 */
public interface IFounderPurchaseService {

    FounderStatusVO getStatus(Long userId);

    FounderPurchaseResultVO buySeat(Long userId, FounderPurchaseDTO dto,
                                    String clientIp, String clientUserAgent);
}
