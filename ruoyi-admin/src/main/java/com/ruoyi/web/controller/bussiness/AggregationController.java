package com.ruoyi.web.controller.bussiness;

import com.ruoyi.chain.aggregation.AggregationService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归集任务手动触发接口（运营遇到异常时手动触发）。
 * 自动触发由 AggregationService.runDailyAggregation 完成。
 */
@RestController
@RequestMapping("/bussiness/aggregation")
public class AggregationController extends BaseController {

    @Autowired
    private AggregationService aggregationService;

    /**
     * 手动触发某条链的归集（异步）。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:aggregation:trigger')")
    @Log(title = "归集触发", businessType = BusinessType.OTHER)
    @PostMapping("/trigger")
    public AjaxResult trigger(@RequestParam String chain) {
        new Thread(() -> {
            try {
                aggregationService.runForChain(chain);
            } catch (Exception e) {
                logger.error("手动归集失败 chain={}", chain, e);
            }
        }, "aggregation-manual-" + chain).start();
        return AjaxResult.success("已提交，链=" + chain + "，请到任务列表查看进度");
    }
}
