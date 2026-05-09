package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.dto.NodeBuyDTO;
import com.ruoyi.bussiness.domain.vo.NodeBuyResultVO;

/**
 * AI 矿机购买服务（金矿 Phase 1 P0 入口）。
 *
 * 完整流程见 NodePurchaseServiceImpl#buyNode 注释。
 *
 * @date 2026-05-10
 */
public interface INodePurchaseService {

    /**
     * 购买一台 AI 矿机。
     *
     * 1. 校验幂等键（命中已有 → 返回首次结果，不重复扣款）
     * 2. 校验等级 enabled=1
     * 3. 校验用户存在 + 未冻结
     * 4. 校验资金密码（BCrypt 比对 TAppUserDetail.userTardPwd）
     * 5. 校验 USDT 现货余额 ≥ 矿机价格
     * 6. 扣 USDT（updateByUserId 原子扣减 amout / availableAmount）
     * 7. 写资产流水 t_app_wallet_record（type=GOLD_NODE_PURCHASE）
     * 8. 创建 t_node_instance（status=active，快照价格/收益率/封顶/出局目标）
     * 9. 写 t_node_purchase_log（含 idempotent_key 防重）
     * 10. 沿双轨树向上累计业绩到所有祖先（含当日 + 累计）
     *
     * 全流程 @Transactional 包裹；任意步骤异常整体回滚。
     */
    NodeBuyResultVO buyNode(Long userId, NodeBuyDTO dto);
}
