package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 链配置管理（RPC、合约、确认数、充提开关）。
 */
@RestController
@RequestMapping("/bussiness/chain/config")
public class ChainConfigController extends BaseController {

    @Autowired
    private TChainConfigMapper chainConfigMapper;

    @PreAuthorize("@ss.hasPermi('bussiness:chain:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        List<TChainConfig> list = chainConfigMapper.selectList(null);
        return AjaxResult.success(list);
    }

    @PreAuthorize("@ss.hasPermi('bussiness:chain:edit')")
    @Log(title = "链配置.编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TChainConfig cfg) {
        cfg.setUpdateBy(getUsername());
        chainConfigMapper.updateById(cfg);
        return AjaxResult.success();
    }

    /** 切换链充值开关 */
    @PreAuthorize("@ss.hasPermi('bussiness:chain:edit')")
    @Log(title = "链充值开关", businessType = BusinessType.UPDATE)
    @PostMapping("/deposit/{chain}")
    public AjaxResult toggleDeposit(@PathVariable String chain, @RequestParam Integer enabled) {
        TChainConfig cfg = new TChainConfig();
        cfg.setChain(chain.toUpperCase());
        cfg.setDepositEnabled(enabled);
        cfg.setUpdateBy(getUsername());
        chainConfigMapper.updateById(cfg);
        return AjaxResult.success();
    }

    /** 切换链提现开关 */
    @PreAuthorize("@ss.hasPermi('bussiness:chain:edit')")
    @Log(title = "链提现开关", businessType = BusinessType.UPDATE)
    @PostMapping("/withdraw/{chain}")
    public AjaxResult toggleWithdraw(@PathVariable String chain, @RequestParam Integer enabled) {
        TChainConfig cfg = new TChainConfig();
        cfg.setChain(chain.toUpperCase());
        cfg.setWithdrawEnabled(enabled);
        cfg.setUpdateBy(getUsername());
        chainConfigMapper.updateById(cfg);
        return AjaxResult.success();
    }
}
