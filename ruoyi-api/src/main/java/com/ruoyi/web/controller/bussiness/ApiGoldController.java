package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TGoldWithdrawOrder;
import com.ruoyi.bussiness.domain.dto.GoldWithdrawDTO;
import com.ruoyi.bussiness.domain.vo.GoldWalletVO;
import com.ruoyi.bussiness.domain.vo.GoldWithdrawResultVO;
import com.ruoyi.bussiness.service.IGoldWithdrawService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 金矿子钱包 + 提现用户端 Controller（金矿 B 路线第一组）。
 *
 * GET  /api/gold/wallet         我的金矿余额 + 当前提现费率
 * POST /api/gold/withdraw       提现到现货 USDT（扣 5% 手续费）
 * GET  /api/gold/withdrawals    提现历史分页
 *
 * @date 2026-05-15
 */
@RestController
@RequestMapping("/api/gold")
@Slf4j
public class ApiGoldController extends ApiBaseController {

    @Resource
    private IGoldWithdrawService goldWithdrawService;

    @GetMapping("/wallet")
    public AjaxResult getMyWallet() {
        GoldWalletVO vo = goldWithdrawService.getMyWallet(getStpUserId());
        return success(vo);
    }

    @PostMapping("/withdraw")
    public AjaxResult withdraw(@RequestBody GoldWithdrawDTO dto, HttpServletRequest request) {
        try {
            GoldWithdrawResultVO result = goldWithdrawService.submitWithdraw(
                    getStpUserId(), dto, getClientIp(request), getUserAgent(request));
            return success(result);
        } catch (ServiceException e) {
            log.warn("gold withdraw rejected: userId={} err={}", getStpUserId(), e.getMessage());
            return error(e.getMessage());
        }
    }

    @GetMapping("/withdrawals")
    public AjaxResult myWithdrawals(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                    @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        IPage<TGoldWithdrawOrder> page = goldWithdrawService.pageMyWithdrawals(getStpUserId(), pageNum, pageSize);
        return success(page);
    }

    private String getClientIp(HttpServletRequest req) {
        if (req == null) return null;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            int comma = ip.indexOf(',');
            if (comma > 0) ip = ip.substring(0, comma);
            return ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return req.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest req) {
        return req == null ? null : req.getHeader("User-Agent");
    }
}
