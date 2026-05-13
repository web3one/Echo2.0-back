package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TChainConfig;
import com.ruoyi.bussiness.domain.TPlatformWallet;
import com.ruoyi.bussiness.mapper.TChainConfigMapper;
import com.ruoyi.bussiness.mapper.TPlatformWalletMapper;
import com.ruoyi.bussiness.service.IHDWalletService;
import com.ruoyi.chain.aggregation.ChainExecutor;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台钱包管理（主/热/冷）。
 */
@Slf4j
@RestController
@RequestMapping("/bussiness/platform/wallet")
public class PlatformWalletController extends BaseController {

    @Autowired private TPlatformWalletMapper walletMapper;
    @Autowired private IHDWalletService hdWalletService;
    @Autowired private TChainConfigMapper chainConfigMapper;
    @Autowired private List<ChainExecutor> chainExecutors;

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

    /**
     * 实时刷新平台钱包余额（从链上 RPC 查 USDT + 原生币余额并写回 DB）。
     * 不传 id = 刷全部 enabled 的钱包；传 id = 只刷一条。
     */
    @PreAuthorize("@ss.hasPermi('bussiness:wallet:edit')")
    @Log(title = "钱包配置.刷新余额", businessType = BusinessType.UPDATE)
    @PostMapping("/refresh-balance")
    public AjaxResult refreshBalance(@RequestParam(required = false) Long id) {
        List<TPlatformWallet> wallets;
        if (id != null) {
            TPlatformWallet w = walletMapper.selectById(id);
            if (w == null) {
                return AjaxResult.error("钱包不存在: id=" + id);
            }
            wallets = Collections.singletonList(w);
        } else {
            wallets = walletMapper.selectList(
                    new LambdaQueryWrapper<TPlatformWallet>().eq(TPlatformWallet::getEnabled, 1));
        }

        int ok = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (TPlatformWallet w : wallets) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", w.getId());
            row.put("chain", w.getChain());
            row.put("walletType", w.getWalletType());
            row.put("address", w.getAddress());
            try {
                TChainConfig cfg = chainConfigMapper.selectById(w.getChain());
                if (cfg == null) {
                    row.put("error", "未配置链: " + w.getChain());
                    failed++;
                    details.add(row);
                    continue;
                }
                ChainExecutor executor = pickExecutor(w.getChain());
                if (executor == null) {
                    row.put("error", "没有匹配的 ChainExecutor");
                    failed++;
                    details.add(row);
                    continue;
                }

                BigDecimal usdt = executor.queryUsdtBalance(w.getAddress(), cfg);
                BigDecimal nativeBal = executor.queryNativeBalance(w.getAddress(), cfg);

                w.setUsdtBalance(usdt != null ? usdt : BigDecimal.ZERO);
                w.setNativeBalance(nativeBal != null ? nativeBal : BigDecimal.ZERO);
                w.setUpdateBy(getUsername());
                w.setUpdateTime(new Date());
                walletMapper.updateById(w);

                row.put("usdtBalance", w.getUsdtBalance());
                row.put("nativeBalance", w.getNativeBalance());
                ok++;
            } catch (Exception e) {
                log.error("刷新余额失败 id={} chain={} addr={}",
                        w.getId(), w.getChain(), w.getAddress(), e);
                row.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                failed++;
            }
            details.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", wallets.size());
        result.put("ok", ok);
        result.put("failed", failed);
        result.put("details", details);
        return AjaxResult.success(result);
    }

    private ChainExecutor pickExecutor(String chain) {
        for (ChainExecutor ex : chainExecutors) {
            if (ex.supports(chain)) return ex;
        }
        return null;
    }
}
