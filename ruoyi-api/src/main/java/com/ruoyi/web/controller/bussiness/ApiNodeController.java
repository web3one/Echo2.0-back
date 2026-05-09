package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.dto.NodeBuyDTO;
import com.ruoyi.bussiness.domain.vo.NodeBuyResultVO;
import com.ruoyi.bussiness.service.INodePurchaseService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * AI 矿机用户端 Controller（金矿 Phase 1 P0）。
 *
 * 当前接口：
 *   POST /api/nodes/buy  购买矿机
 *
 * @date 2026-05-10
 */
@RestController
@RequestMapping("/api/nodes")
@Slf4j
public class ApiNodeController extends ApiBaseController {

    @Resource
    private INodePurchaseService nodePurchaseService;

    /**
     * 购买 AI 矿机。
     *
     * 请求体：
     *   levelCode     L1 / L2 / L3 / L4
     *   fundPassword  资金密码（明文，HTTPS 传输 + service 内 BCrypt 比对）
     *   idempotentKey 客户端生成的唯一键（UUID 推荐），同 key 重提返回首次结果
     *
     * 错误返回（i18n）：
     *   node.buy.level.invalid       矿机等级无效或未启用
     *   node.buy.idempotent.empty    幂等键缺失
     *   node.buy.duplicate           幂等冲突 / 重复请求
     *   node.buy.usdt.insufficient   USDT 余额不足
     *   user.password_notbind        资金密码未设置
     *   tard_password.error          资金密码错误
     *   c2c.user.not_found           用户不存在
     *   c2c.user.frozen              账户已冻结
     */
    @PostMapping("/buy")
    public AjaxResult buy(@RequestBody NodeBuyDTO dto) {
        try {
            NodeBuyResultVO result = nodePurchaseService.buyNode(getStpUserId(), dto);
            return success(result);
        } catch (ServiceException e) {
            log.warn("node buy rejected: userId={} err={}", getStpUserId(), e.getMessage());
            return error(e.getMessage());
        }
    }
}
