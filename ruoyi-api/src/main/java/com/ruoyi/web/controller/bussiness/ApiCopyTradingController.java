package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TCopyTrader;
import com.ruoyi.bussiness.domain.dto.CopyFollowDTO;
import com.ruoyi.bussiness.domain.dto.CopyTraderApplyDTO;
import com.ruoyi.bussiness.service.ICopyTradingService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.web.controller.common.ApiBaseController;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/copy")
public class ApiCopyTradingController extends ApiBaseController {
    @Resource
    private ICopyTradingService copyTradingService;

    @ApiOperation("申请成为跟单交易员")
    @PostMapping("/trader/apply")
    public AjaxResult applyTrader(@RequestBody CopyTraderApplyDTO dto) {
        return success(copyTradingService.applyTrader(getStpUserId(), dto));
    }

    @ApiOperation("我的交易员状态")
    @GetMapping("/trader/me")
    public AjaxResult myTraderStatus() {
        return success(copyTradingService.getMyTraderStatus(getStpUserId()));
    }

    @ApiOperation("可跟单交易员列表")
    @GetMapping("/traders")
    public TableDataInfo traders(TCopyTrader query) {
        startPage();
        return getDataTable(copyTradingService.listApprovedTraders(query));
    }

    @ApiOperation("交易员详情")
    @GetMapping("/trader/{userId}")
    public AjaxResult traderDetail(@PathVariable("userId") Long userId) {
        return success(copyTradingService.getTrader(userId));
    }

    @ApiOperation("开始或更新跟单")
    @PostMapping("/follow")
    public AjaxResult follow(@RequestBody CopyFollowDTO dto) {
        return success(copyTradingService.follow(getStpUserId(), dto));
    }

    @ApiOperation("停止跟单")
    @PostMapping("/unfollow")
    public AjaxResult unfollow(@RequestBody CopyFollowDTO dto) {
        copyTradingService.unfollow(getStpUserId(), dto == null ? null : dto.getTraderUserId());
        return success();
    }

    @ApiOperation("我的跟单关系")
    @GetMapping("/my-relations")
    public TableDataInfo myRelations() {
        startPage();
        return getDataTable(copyTradingService.myRelations(getStpUserId()));
    }

    @ApiOperation("我的跟单记录")
    @GetMapping("/my-orders")
    public TableDataInfo myOrders() {
        startPage();
        return getDataTable(copyTradingService.myOrders(getStpUserId()));
    }
}
