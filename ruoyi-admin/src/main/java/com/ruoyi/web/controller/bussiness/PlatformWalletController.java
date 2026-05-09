package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TPlatformWallet;
import com.ruoyi.bussiness.mapper.TPlatformWalletMapper;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台钱包管理（主/热/冷）。
 */
@RestController
@RequestMapping("/bussiness/platform/wallet")
public class PlatformWalletController extends BaseController {

    @Autowired private TPlatformWalletMapper walletMapper;
    @Autowired private IHDWalletService hdWalletService;

    @PreAuthorize("@ss.hasPermi('bussiness:wallet:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(walletMapper.selectList(null));
    }

    @PreAuthorize("@ss.hasPermi('bussiness:wallet:edit')")
    @Log(title = "钱包配置.新增", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TPlatformWallet w) {
        w.setCreateBy(getUsername());
        w.setCreateTime(new Date());
        if (w.getEnabled() == null) w.setEnabled(1);
        if (w.getUsdtBalance() == null) w.setUsdtBalance(BigDecimal.ZERO);
        if (w.getNativeBalance() == null) w.setNativeBalance(BigDecimal.ZERO);
        walletMapper.insert(w);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('bussiness:wallet:edit')")
    @Log(title = "钱包配置.编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TPlatformWallet w) {
        w.setUpdateBy(getUsername());
        walletMapper.updateById(w);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('bussiness:wallet:remove')")
    @Log(title = "钱包配置.删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        walletMapper.deleteById(id);
        return AjaxResult.success();
    }

    /**
     * 一键根据 HD 助记词派生主钱包并入库（首次部署用）。
     * 已存在的不重复创建。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:wallet:edit')")
    @Log(title = "派生主钱包", businessType = BusinessType.INSERT)
    @PostMapping("/derive-main")
    public AjaxResult deriveMainWallets() {
        Map<String, String> result = new HashMap<>();
        for (String chain : new String[]{"ETH", "BSC", "BASE", "TRX"}) {
            TPlatformWallet existing = walletMapper.selectMain(chain);
            if (existing != null) {
                result.put(chain, "已存在: " + existing.getAddress());
                continue;
            }
            String addr = hdWalletService.getMainAddress(chain);
            TPlatformWallet w = new TPlatformWallet();
            w.setChain(chain);
            w.setAddress(addr);
            w.setWalletType("MAIN");
            w.setDerivePath(chain.equals("TRX") ? "m/44'/195'/0'/0/0" : "m/44'/60'/0'/0/0");
            w.setUsdtBalance(BigDecimal.ZERO);
            w.setNativeBalance(BigDecimal.ZERO);
            w.setEnabled(1);
            w.setCreateBy(getUsername());
            w.setCreateTime(new Date());
            walletMapper.insert(w);
            result.put(chain, "新建: " + addr);
        }
        return AjaxResult.success(result);
    }
}
