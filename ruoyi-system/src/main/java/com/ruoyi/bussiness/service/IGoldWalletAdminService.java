package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;

/**
 * 金矿子钱包 admin 查询 Service（金矿 B 路线第一组 admin 通道）。
 *
 * 仅查询/导出能力；调账（人工增减余额）当前由 IGoldWalletService.deductBalance/refundBalance 兜底，
 * 后续如需 admin 调账接口再补。
 *
 * @date 2026-05-15
 */
public interface IGoldWalletAdminService {

    /** 分页搜用户子钱包（按 userId 精确 / 余额阈值 / 创建时间） */
    IPage<TGoldWallet> pageWallets(Integer pageNum, Integer pageSize, TGoldWallet query);

    /** 单用户钱包详情 */
    TGoldWallet getByUserId(Long userId);

    /** 分页查流水（按 userId / changeType / bizRefType / 时间） */
    IPage<TGoldWalletLog> pageWalletLogs(Integer pageNum, Integer pageSize, TGoldWalletLog query);
}
