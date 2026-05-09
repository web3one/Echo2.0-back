package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.TNodeLevel;

import java.util.List;

/**
 * 矿机等级配置服务（金矿 admin CRUD）
 */
public interface INodeLevelService {

    List<TNodeLevel> listAll();

    TNodeLevel getById(Long id);

    TNodeLevel getByLevelCode(String levelCode);

    int insert(TNodeLevel record);

    int update(TNodeLevel record);

    /**
     * 启用/停用切换。enabled ∈ {0, 1}。
     */
    int toggleEnabled(Long id, Integer enabled);

    /**
     * 物理删除（admin 慎用，只允许删除从未被购买过的等级；有 t_node_instance 引用的级不能删）。
     */
    int deleteById(Long id);
}
