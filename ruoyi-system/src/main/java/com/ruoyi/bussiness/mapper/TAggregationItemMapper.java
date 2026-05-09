package com.ruoyi.bussiness.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.bussiness.domain.TAggregationItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TAggregationItemMapper extends BaseMapper<TAggregationItem> {

    default List<TAggregationItem> selectByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapper<TAggregationItem>()
                .eq(TAggregationItem::getTaskId, taskId));
    }

    default List<TAggregationItem> selectByTaskAndStatus(Long taskId, String status) {
        return selectList(new LambdaQueryWrapper<TAggregationItem>()
                .eq(TAggregationItem::getTaskId, taskId)
                .eq(TAggregationItem::getStatus, status));
    }
}
