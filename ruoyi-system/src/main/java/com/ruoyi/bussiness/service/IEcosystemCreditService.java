package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.dto.EcosystemCreditUnlockDTO;
import com.ruoyi.bussiness.domain.vo.EcosystemCreditUnlockResultVO;
import com.ruoyi.bussiness.domain.vo.MyEcosystemCreditVO;

/**
 * ecosystem_credit 服务（PRD §10）。
 *
 * 两条解锁路径：
 *   - trade_volume：累计交易量 = amount × 3 后自动完成
 *   - xgt_lock：锁等值 $XGT 30 天到期后自动完成
 *
 * cron {@link com.ruoyi.bussiness.service.IEcosystemCreditUnlockCheckService}
 * 每小时扫 in_progress 推进。
 */
public interface IEcosystemCreditService {

    /** GET /api/ecosystem-credit/my */
    MyEcosystemCreditVO getMyCredit(Long userId);

    /** POST /api/ecosystem-credit/unlock 提交解锁申请 */
    EcosystemCreditUnlockResultVO submitUnlock(Long userId, EcosystemCreditUnlockDTO dto);
}
