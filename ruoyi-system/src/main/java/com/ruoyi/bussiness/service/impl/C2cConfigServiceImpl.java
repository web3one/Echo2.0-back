package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TC2cConfig;
import com.ruoyi.bussiness.mapper.TC2cConfigMapper;
import com.ruoyi.bussiness.service.IC2cConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * C2C全局配置Service业务层处理
 */
@Service
public class C2cConfigServiceImpl extends ServiceImpl<TC2cConfigMapper, TC2cConfig> implements IC2cConfigService {

    @Resource
    private TC2cConfigMapper c2cConfigMapper;

    @Override
    public String getConfigValue(String key) {
        TC2cConfig config = c2cConfigMapper.selectByKey(key);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public int getConfigIntValue(String key, int defaultValue) {
        String value = getConfigValue(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public List<TC2cConfig> selectConfigList() {
        return c2cConfigMapper.selectList(null);
    }

    @Override
    public int updateConfig(TC2cConfig config) {
        return c2cConfigMapper.updateById(config);
    }
}
