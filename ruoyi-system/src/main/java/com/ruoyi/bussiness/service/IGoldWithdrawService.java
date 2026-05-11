package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.domain.dto.GoldWithdrawDTO;
import com.ruoyi.bussiness.domain.vo.GoldWalletVO;
import com.ruoyi.bussiness.domain.vo.GoldWithdrawResultVO;

/**
 * 金矿子钱包→现货提现 Service（用户主动发起）。
 *
 * 5% 提现费拆解（PRD §12.2 创世权益第 5 条）：
 *   1% → t_pool_account.founder_share 创世 49 等分池
 *   4% → t_pool_account.platform_fee 平台收入
 *
 * @date 2026-05-15
 */
public interface IGoldWithdrawService {

    /** 查我的金矿子钱包 + 当前提现费率（mining-gold 资产中心首页用） */
    GoldWalletVO getMyWallet(Long userId);

    /**
     * 提现：扣金矿子钱包 100% → 写 95% 进现货 USDT + 5% 拆 1%/4%。
     * 单事务原子完成。
     */
    GoldWithdrawResultVO submitWithdraw(Long userId,
                                        GoldWithdrawDTO dto,
                                        String clientIp,
                                        String clientUserAgent);

    /** 我的提现历史（mining-gold 提现记录页用） */
    IPage<TGoldWithdrawOrder> pageMyWithdrawals(Long userId, Integer pageNum, Integer pageSize);
}
