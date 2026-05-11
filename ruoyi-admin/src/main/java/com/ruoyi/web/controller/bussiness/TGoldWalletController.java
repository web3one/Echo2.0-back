package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWallet;
import com.ruoyi.bussiness.domain.TGoldWalletLog;
import com.ruoyi.bussiness.service.IGoldWalletAdminService;
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
 * 金矿子钱包 admin 管理 Controller（B 路线第一组）。
 *
 *  - GET /bussiness/gold/wallet/list      钱包余额列表（按余额倒序，可按 userId 搜）
 *  - GET /bussiness/gold/wallet/{userId}  单用户余额详情
 *  - GET /bussiness/gold/wallet/log/list  子钱包流水分页（changeType / bizRefType 过滤）
 *
 * @date 2026-05-15
 */
@RestController
@RequestMapping("/bussiness/gold/wallet")
public class TGoldWalletController extends BaseController {

    @Autowired
    private IGoldWalletAdminService goldWalletAdminService;

    @PreAuthorize("@ss.hasPermi('bussiness:gold:wallet:list')")
    @GetMapping("/list")
    public AjaxResult listWallets(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  TGoldWallet query) {
        IPage<TGoldWallet> page = goldWalletAdminService.pageWallets(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:wallet:query')")
    @GetMapping("/{userId}")
    public AjaxResult getWallet(@PathVariable("userId") Long userId) {
        return success(goldWalletAdminService.getByUserId(userId));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:gold:wallet:log:list')")
    @GetMapping("/log/list")
    public AjaxResult listLogs(@RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "10") Integer pageSize,
                               TGoldWalletLog query) {
        IPage<TGoldWalletLog> page = goldWalletAdminService.pageWalletLogs(pageNum, pageSize, query);
        AjaxResult res = AjaxResult.success();
        res.put("rows", page.getRecords());
        res.put("total", page.getTotal());
        return res;
    }
}
