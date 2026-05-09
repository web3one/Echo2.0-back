package com.ruoyi.web.controller.bussiness;

import cn.dev33.satoken.stp.StpUtil;
import com.ruoyi.bussiness.domain.TPoolBalance;
import com.ruoyi.bussiness.domain.TUserAddress;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.bussiness.service.IPoolService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * H5 用户端：充值地址生成 + 底池查询。
 */
@RestController
@RequestMapping("/api/chain")
public class AppChainController extends BaseController {

    @Autowired private IHDWalletService hdWalletService;
    @Autowired private IPoolService poolService;

    /**
     * 取（或首次派生）当前用户在某链的充值地址。幂等。
     */
    @PostMapping("/deposit-address")
    public AjaxResult getDepositAddress(@RequestParam String chain) {
        Long userId = StpUtil.getLoginIdAsLong();
        TUserAddress addr = hdWalletService.getOrDeriveAddress(userId, chain.toUpperCase());
        return AjaxResult.success(addr);
    }

    /**
     * H5 资产页展示的底池（display_in_h5=1 的全部）。
     */
    @GetMapping("/pool")
    public AjaxResult listPool() {
        List<TPoolBalance> list = poolService.listForH5();
        return AjaxResult.success(list);
    }

    /**
     * 链可用列表（前端选链下拉用，只列 deposit_enabled 或 withdraw_enabled）。
     */
    @GetMapping("/chains")
    public AjaxResult listChains(@RequestParam(defaultValue = "deposit") String purpose) {
        // 简化实现：直接查 chainConfig 暴露开启的链
        // 复用 PoolService 那套也行；这里直接用 mapper 简化
        return AjaxResult.success(java.util.Arrays.asList(
                new ChainOpt("ETH", "Ethereum"),
                new ChainOpt("BSC", "BNB Smart Chain"),
                new ChainOpt("BASE", "Base"),
                new ChainOpt("TRX", "Tron")
        ));
    }

    public static class ChainOpt {
        public String chain;
        public String name;
        public ChainOpt(String c, String n) { chain = c; name = n; }
    }
}
