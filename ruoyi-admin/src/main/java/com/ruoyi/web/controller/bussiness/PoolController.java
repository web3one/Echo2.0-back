package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TPoolBalance;
import com.ruoyi.bussiness.domain.TPoolLog;
import com.ruoyi.bussiness.service.IPoolService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台底池管理（管理后台）。
 */
@RestController
@RequestMapping("/bussiness/pool")
public class PoolController extends BaseController {

    @Autowired
    private IPoolService poolService;

    /** 全部底池余额（按 币种+链 列出 4×N 行） */
    @PreAuthorize("@ss.hasPermi('bussiness:pool:list')")
    @GetMapping("/list")
    public AjaxResult listAll() {
        List<TPoolBalance> list = poolService.listAll();
        return AjaxResult.success(list);
    }

    /** 单条 */
    @PreAuthorize("@ss.hasPermi('bussiness:pool:list')")
    @GetMapping("/balance")
    public AjaxResult getBalance(@RequestParam String symbol, @RequestParam String chain) {
        return AjaxResult.success(poolService.getBalance(symbol, chain));
    }

    /** 流水分页 */
    @PreAuthorize("@ss.hasPermi('bussiness:pool:list')")
    @GetMapping("/log")
    public TableDataInfo log(String symbol, String chain, String changeType,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "20") int pageSize) {
        com.baomidou.mybatisplus.core.metadata.IPage<TPoolLog> page =
                poolService.pageLog(symbol, chain, changeType, pageNum, pageSize);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("OK");
        return info;
    }

    /** 手动调整底池（必填备注，全程留痕） */
    @PreAuthorize("@ss.hasPermi('bussiness:pool:edit')")
    @Log(title = "底池手工调整", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public AjaxResult adjust(@RequestParam String symbol,
                              @RequestParam String chain,
                              @RequestParam BigDecimal delta,
                              @RequestParam String remark) {
        try {
            poolService.adjust(symbol, chain, delta, getUsername(), remark);
            return AjaxResult.success("已调整");
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }
}
