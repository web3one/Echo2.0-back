package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.bussiness.domain.TAggregationItem;
import com.ruoyi.bussiness.domain.TAggregationTask;
import com.ruoyi.bussiness.mapper.TAggregationItemMapper;
import com.ruoyi.bussiness.mapper.TAggregationTaskMapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归集任务/明细查询。
 */
@RestController
@RequestMapping("/bussiness/aggregation")
public class AggregationListController extends BaseController {

    @Autowired private TAggregationTaskMapper taskMapper;
    @Autowired private TAggregationItemMapper itemMapper;

    @PreAuthorize("@ss.hasPermi('bussiness:aggregation:list')")
    @GetMapping("/tasks")
    public TableDataInfo tasks(String chain,
                                @RequestParam(defaultValue = "1") int pageNum,
                                @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<TAggregationTask> q = new LambdaQueryWrapper<TAggregationTask>()
                .orderByDesc(TAggregationTask::getId);
        if (chain != null && !chain.isEmpty()) q.eq(TAggregationTask::getChain, chain.toUpperCase());

        com.baomidou.mybatisplus.core.metadata.IPage<TAggregationTask> page =
                taskMapper.selectPage(new Page<>(pageNum, pageSize), q);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("OK");
        return info;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:aggregation:list')")
    @GetMapping("/items/{taskId}")
    public AjaxResult itemsByTask(@org.springframework.web.bind.annotation.PathVariable Long taskId) {
        return AjaxResult.success(itemMapper.selectByTaskId(taskId));
    }
}
