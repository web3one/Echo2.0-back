package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TFounderPurchaseLog;
import com.ruoyi.bussiness.domain.TFounderSeat;

import java.util.List;

/**
 * 创世管理 admin 服务（金矿 Phase 2 A 路线第二组）。
 *
 * 49 席预创建，admin 不允许"新增席位"——这是 PRD 硬约束。
 * admin 操作：冻结席位 / 解冻 / 改备注 / 查购买流水。
 * 退款 / 转让等高风险操作目前不开放，PM 需求出现后再加。
 */
public interface IFounderAdminService {

    /** 全部 49 行（按 seat_no ASC） */
    List<TFounderSeat> listAllSeats();

    TFounderSeat getSeatById(Long seatId);

    /** 冻结席位：owned/available → frozen，记录 frozen_at/reason/by_admin */
    TFounderSeat freezeSeat(Long seatId, Long operatorAdminId, String reason);

    /**
     * 解冻：frozen → owned（owner_user_id 存在）/ available（owner_user_id 为 null）。
     * 不补发冻结期间应得未发奖。
     */
    TFounderSeat unfreezeSeat(Long seatId, Long operatorAdminId, String reason);

    /** 改备注（不影响业务逻辑，仅运营记录） */
    TFounderSeat updateRemark(Long seatId, String remark);

    /** 购买流水分页（含 userId / seatNo 过滤） */
    IPage<TFounderPurchaseLog> pageLogs(int pageNum, int pageSize, TFounderPurchaseLog query);
}
