package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.dto.FounderPurchaseDTO;
import com.ruoyi.bussiness.domain.vo.FounderPurchaseResultVO;
import com.ruoyi.bussiness.domain.vo.FounderStatusVO;
import com.ruoyi.bussiness.service.IFounderPurchaseService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 创世合伙人用户端 Controller（金矿 Phase 2 A 路线第二组）。
 *
 * GET  /api/founder/status     49 席矩阵 + 我的席位 + 当前价格 + 剩余
 * POST /api/founder/purchase   购买席位（资金密码二验 + 现货 USDT 扣款）
 *
 * @date 2026-05-11
 */
@RestController
@RequestMapping("/api/founder")
@Slf4j
public class ApiFounderController extends ApiBaseController {

    @Resource
    private IFounderPurchaseService founderPurchaseService;

    @GetMapping("/status")
    public AjaxResult status() {
        FounderStatusVO vo = founderPurchaseService.getStatus(getStpUserId());
        return success(vo);
    }

    @PostMapping("/purchase")
    public AjaxResult purchase(@RequestBody FounderPurchaseDTO dto, HttpServletRequest request) {
        try {
            FounderPurchaseResultVO result = founderPurchaseService.buySeat(
                    getStpUserId(), dto, getClientIp(request), getUserAgent(request));
            return success(result);
        } catch (ServiceException e) {
            log.warn("founder purchase rejected: userId={} err={}", getStpUserId(), e.getMessage());
            return error(e.getMessage());
        }
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
