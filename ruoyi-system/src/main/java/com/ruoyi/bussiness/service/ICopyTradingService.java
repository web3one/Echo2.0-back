package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TContractPosition;
import com.ruoyi.bussiness.domain.TCopyOrder;
import com.ruoyi.bussiness.domain.TCopyRelation;
import com.ruoyi.bussiness.domain.TCopyTrader;
import com.ruoyi.bussiness.domain.TCopyTraderApply;
import com.ruoyi.bussiness.domain.dto.CopyFollowDTO;
import com.ruoyi.bussiness.domain.dto.CopyTraderApplyDTO;

import java.math.BigDecimal;
import java.util.List;

public interface ICopyTradingService {
    TCopyTraderApply applyTrader(Long userId, CopyTraderApplyDTO dto);

    Object getMyTraderStatus(Long userId);

    List<TCopyTrader> listApprovedTraders(TCopyTrader query);

    TCopyTrader getTrader(Long traderUserId);

    TCopyRelation follow(Long followerUserId, CopyFollowDTO dto);

    void unfollow(Long followerUserId, Long traderUserId);

    List<TCopyRelation> myRelations(Long followerUserId);

    List<TCopyOrder> myOrders(Long followerUserId);

    List<TCopyTraderApply> adminApplications(TCopyTraderApply query);

    List<TCopyTrader> adminTraders(TCopyTrader query);

    TCopyTrader approveApplication(Long id, Long adminId, String remark);

    void rejectApplication(Long id, Long adminId, String remark);

    void updateTraderStatus(Long traderUserId, String status, String remark);

    void afterTraderOpen(TContractPosition traderPosition, BigDecimal traderAvailableBeforeOrder);

    void afterTraderClose(TContractPosition traderPosition);
}
