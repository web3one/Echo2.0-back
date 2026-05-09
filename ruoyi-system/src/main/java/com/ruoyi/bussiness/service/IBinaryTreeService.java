package com.ruoyi.bussiness.service;

import java.math.BigDecimal;

/**
 * 双轨树服务（金矿邀请体系 Phase 0.4 + Phase 1）
 */
public interface IBinaryTreeService {

    /**
     * 新用户注册时把用户挂到双轨树里。
     * 算法（PM 选定的 C 方案：混合 placement）：
     *   1. 如果 sponsor 在树里没有节点（老用户首次发展下线），先给 sponsor 建一个根节点
     *   2. 决定方向 direction：
     *      - placementSide = 'auto' 或为空：取 sponsor.left_count vs right_count，塞数量少的一边
     *      - placementSide = 'left' / 'right'：直接采用
     *   3. 从 sponsor 沿 direction 方向静态滑落，找到第一个该方向为空的祖先节点 P
     *   4. 把 newUser 作为 P 在 direction 上的子节点写入 t_binary_tree
     *   5. 沿 P 一路向上回溯，每个祖先在它包含 newUser 的那一区计数 +1
     *
     * @param newUserId      新用户 ID（已写入 t_app_user）
     * @param sponsorId      邀请人 ID（active_code 对应的用户）
     * @param placementSide  用户偏好：'auto' / 'left' / 'right'，默认 'auto'
     */
    void placeNewUser(Long newUserId, Long sponsorId, String placementSide);

    /**
     * 把 buyer 的购买金额沿双轨树向上累计到所有祖先（PRD §7）。
     * 同时累加：
     *   - t_binary_volume_daily（含 buyer 一侧；biz_date = UTC 今日）
     *   - t_binary_volume_total（含 buyer 一侧；永久累计代理升级用）
     *
     * 业绩 = 矿机金额，不是奖励金额（PRD §7 第 5 条）。
     * 如果 buyer 不在双轨树（极少数情况：注册时 sponsor 异常），方法 no-op 返回。
     *
     * @param buyerUserId 购买者用户ID
     * @param amountUsdt  矿机价格（USDT）
     */
    void accumulateVolume(Long buyerUserId, BigDecimal amountUsdt);
}
