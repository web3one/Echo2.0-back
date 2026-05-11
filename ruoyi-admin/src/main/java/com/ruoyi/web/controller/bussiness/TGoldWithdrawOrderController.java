package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.service.IGoldWithdrawAdminService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 金矿提现单 admin Controller（B 路线第一组）。
 *
 *  - GET /bussiness/gold/withdraw/list   提现单分页（userId / status / assetType 过滤）
 *  - GET /bussiness/gold/withdraw/{id}   单单详情（审计用：含费率快照 + IP/UA）
 *
 * @date 2026-05-15
 */
@RestController
@RequestMapping("/bussiness/gold/withdraw")
public class TGoldWithdrawOrderController extends BaseController {

    @Autowired
    private IGoldWithdrawAdminService goldWithdrawAdminService;

    @PreAuthorize("@ss.hasPermi('bussiness:gold:withdraw:list')")
    @GetMapping("/list")
    public AjaxResult listWithdraws(@RequestParam(defaultValue = "1") Integer pageNum,
                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                    TGoldWithdrawOrder query) {
        IPage<TGoldWithdrawOrder> page = goldWithdrawAdminService.pageWithdraws(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:withdraw:query')")
    @GetMapping("/{id}")
    public AjaxResult getWithdraw(@PathVariable("id") Long id) {
        return success(goldWithdrawAdminService.getWithdrawById(id));
    }
}
