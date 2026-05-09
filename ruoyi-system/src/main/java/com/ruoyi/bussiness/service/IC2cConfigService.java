package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TC2cConfig;
import java.util.List;

public interface IC2cConfigService extends IService<TC2cConfig> {
    String getConfigValue(String key);
    int getConfigIntValue(String key, int defaultValue);
    List<TC2cConfig> selectConfigList();
    int updateConfig(TC2cConfig config);
}
