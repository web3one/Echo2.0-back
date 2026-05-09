package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TChainConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TChainConfigMapper extends BaseMapper<TChainConfig> {

    @Update("UPDATE t_chain_config SET last_scanned_block = #{blockNumber} WHERE chain = #{chain}")
    int updateLastScannedBlock(@Param("chain") String chain, @Param("blockNumber") Long blockNumber);
}
