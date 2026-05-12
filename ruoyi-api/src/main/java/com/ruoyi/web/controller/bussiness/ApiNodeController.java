package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TNodeLevel;
import com.ruoyi.bussiness.domain.dto.NodeBuyDTO;
import com.ruoyi.bussiness.domain.vo.MyMinerVO;
import com.ruoyi.bussiness.domain.vo.NodeBuyResultVO;
import com.ruoyi.bussiness.service.INodePurchaseService;
import com.ruoyi.bussiness.service.INodeQueryService;
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
import java.util.List;

/**
 * AI 矿机用户端 Controller（金矿 Phase 1 P0）。
 *
 * 当前接口：
 *   POST /api/nodes/buy  购买矿机
 *   GET  /api/nodes/my   我的矿机列表
 *
 * @date 2026-05-10
 */
@RestController
@RequestMapping("/api/nodes")
@Slf4j
public class ApiNodeController extends ApiBaseController {

    @Resource
    private INodePurchaseService nodePurchaseService;

    @Resource
    private INodeQueryService nodeQueryService;

    /**
     * 我的矿机列表（mining-gold 子站 swap MOCK_MY_MINERS）。
     *
     * 字段对齐 mining-gold App.tsx interface MyMiner（line 671）。
     * 排序 active 优先，同 status 按 level 高 → 低，同 level 按 activatedAt 旧 → 新。
     * isCurrentActive 标识当前有效权益矿机（仅 active 中 levelCode 最高 + 同级最早一台）。
     */
    @GetMapping("/my")
    public AjaxResult listMyMiners() {
        List<MyMinerVO> list = nodeQueryService.listMyMiners(getStpUserId());
        return success(list);
    }

    /**
     * H5 商城 L1-L4 配置列表（GET /api/nodes/levels）。
     *
     * 返回 enabled=1 的等级，按 sort asc 排序。admin 后台改 t_node_level
     * 表（价格 / 每日收益率 / 团队日封顶 / 启停）后，前端刷新即生效。
     */
    @GetMapping("/levels")
    public AjaxResult listLevels() {
        List<TNodeLevel> list = nodeQueryService.listEnabledLevels();
        return success(list);
    }

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
