package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.dto.C2cAdvertCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import java.util.List;

public interface IC2cAdvertService extends IService<TC2cAdvert> {
    String createAdvert(Long userId, C2cAdvertCreateDTO dto);
    int offlineAdvert(Long id, Long userId);
    int onlineAdvert(Long id, Long userId);
    int adminDisableAdvert(Long id);
    int adminEnableAdvert(Long id);
    List<C2cAdvertVO> listPublicAdverts(TC2cAdvert query);
    C2cAdvertVO getPublicAdvertDetail(Long id);
    List<TC2cAdvert> listMyAdverts(Long userId);
    List<TC2cAdvert> selectAdvertList(TC2cAdvert advert);
    List<C2cAdvertVO> selectAdminAdvertVOList(TC2cAdvert advert);
    TC2cAdvert getAdvertDetail(Long id);
}
