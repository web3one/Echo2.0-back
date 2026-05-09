package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cOrderAppeal;
import com.ruoyi.bussiness.domain.TC2cAppealMessage;
import com.ruoyi.bussiness.domain.dto.C2cAppealCreateDTO;
import com.ruoyi.bussiness.domain.dto.C2cAppealResolveDTO;
import java.util.List;

public interface IC2cAppealService extends IService<TC2cOrderAppeal> {
    String createAppeal(Long userId, C2cAppealCreateDTO dto);
    int addMessage(Long appealId, Long senderId, Integer senderType, Integer contentType, String content);
    String resolveAppeal(C2cAppealResolveDTO dto, Long adminId);
    TC2cOrderAppeal getAppealByOrderId(Long orderId);
    TC2cOrderAppeal getAppealByOrderId(Long orderId, Long userId);
    List<TC2cOrderAppeal> selectAppealList(TC2cOrderAppeal appeal);
    List<TC2cAppealMessage> getAppealMessages(Long appealId);
    List<TC2cAppealMessage> getAppealMessages(Long appealId, Long userId);
}
