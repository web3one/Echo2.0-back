package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TTradfiSymbol;

import java.util.List;

/**
 * TradFi 标的 Mapper
 */
public interface TTradfiSymbolMapper extends BaseMapper<TTradfiSymbol> {

    TTradfiSymbol selectTTradfiSymbolById(Long id);

    List<TTradfiSymbol> selectTTradfiSymbolList(TTradfiSymbol query);

    /** 给 ChainMonitor / 轮询任务用：仅启用且 H5 可见的 */
    List<TTradfiSymbol> selectActiveForPolling(String market);

    /** 给 H5 getCoinList 用：启用 + 展示 + 按 sort 排 */
    List<TTradfiSymbol> selectH5SymbolList();

    int insertTTradfiSymbol(TTradfiSymbol record);

    int updateTTradfiSymbol(TTradfiSymbol record);

    int deleteTTradfiSymbolById(Long id);

    int deleteTTradfiSymbolByIds(Long[] ids);
}
