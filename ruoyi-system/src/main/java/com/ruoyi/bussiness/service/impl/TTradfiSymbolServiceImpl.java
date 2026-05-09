package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TTradfiSymbol;
import com.ruoyi.bussiness.mapper.TTradfiSymbolMapper;
import com.ruoyi.bussiness.service.ITTradfiSymbolService;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TTradfiSymbolServiceImpl
        extends ServiceImpl<TTradfiSymbolMapper, TTradfiSymbol>
        implements ITTradfiSymbolService {

    @Resource
    private TTradfiSymbolMapper mapper;

    @Override
    public TTradfiSymbol selectTTradfiSymbolById(Long id) {
        return mapper.selectTTradfiSymbolById(id);
    }

    @Override
    public List<TTradfiSymbol> selectTTradfiSymbolList(TTradfiSymbol query) {
        return mapper.selectTTradfiSymbolList(query);
    }

    @Override
    public List<TTradfiSymbol> selectActiveForPolling(String market) {
        return mapper.selectActiveForPolling(market);
    }

    @Override
    public List<TTradfiSymbol> selectH5SymbolList() {
        return mapper.selectH5SymbolList();
    }

    @Override
    public int insertTTradfiSymbol(TTradfiSymbol record) {
        record.setCreateTime(DateUtils.getNowDate());
        return mapper.insertTTradfiSymbol(record);
    }

    @Override
    public int updateTTradfiSymbol(TTradfiSymbol record) {
        record.setUpdateTime(DateUtils.getNowDate());
        return mapper.updateTTradfiSymbol(record);
    }

    @Override
    public int deleteTTradfiSymbolByIds(Long[] ids) {
        return mapper.deleteTTradfiSymbolByIds(ids);
    }

    @Override
    public int deleteTTradfiSymbolById(Long id) {
        return mapper.deleteTTradfiSymbolById(id);
    }
}
