package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.bussiness.domain.TTradfiSymbol;

import java.util.List;

/**
 * TradFi 标的 Service
 */
public interface ITTradfiSymbolService extends IService<TTradfiSymbol> {

    TTradfiSymbol selectTTradfiSymbolById(Long id);

    List<TTradfiSymbol> selectTTradfiSymbolList(TTradfiSymbol query);

    /** 给轮询任务用，按数据源筛选启用项 */
    List<TTradfiSymbol> selectActiveForPolling(String market);

    /** 给 H5 getCoinList 用：启用 + 展示 + 注入 Redis 实时价 */
    List<TTradfiSymbol> selectH5SymbolList();

    int insertTTradfiSymbol(TTradfiSymbol record);

    int updateTTradfiSymbol(TTradfiSymbol record);

    int deleteTTradfiSymbolByIds(Long[] ids);

    int deleteTTradfiSymbolById(Long id);
}
