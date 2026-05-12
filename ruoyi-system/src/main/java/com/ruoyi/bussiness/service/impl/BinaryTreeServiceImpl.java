package com.ruoyi.bussiness.service.impl;

import com.ruoyi.bussiness.domain.TBinaryTree;
import com.ruoyi.bussiness.domain.TBinaryVolumeTotal;
import com.ruoyi.bussiness.mapper.TBinaryTreeMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeDailyMapper;
import com.ruoyi.bussiness.mapper.TBinaryVolumeTotalMapper;
import com.ruoyi.bussiness.service.IBinaryTreeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 双轨树服务实现（金矿邀请体系 Phase 0.4）
 */
@Service
@Slf4j
public class BinaryTreeServiceImpl implements IBinaryTreeService {

    @Resource
    private TBinaryTreeMapper binaryTreeMapper;

    @Resource
    private TBinaryVolumeDailyMapper binaryVolumeDailyMapper;

    @Resource
    private TBinaryVolumeTotalMapper binaryVolumeTotalMapper;

    @Override
    @Transactional
    public void placeNewUser(Long newUserId, Long sponsorId, String placementSide) {
        // placementSide 入参保留只为兼容老调用方；2026-05-09 起强制 auto 弱区，不再接受用户偏好
        if (newUserId == null || sponsorId == null) {
            log.warn("placeNewUser skipped: newUserId={} sponsorId={}", newUserId, sponsorId);
            return;
        }

        // 步骤 1：sponsor 不在树里 → 建根节点（老用户首次发展下线时触发）
        TBinaryTree sponsorNode = binaryTreeMapper.selectByUserId(sponsorId);
        if (sponsorNode == null) {
            sponsorNode = new TBinaryTree();
            sponsorNode.setUserId(sponsorId);
            sponsorNode.setSponsorId(null);
            sponsorNode.setParentId(null);
            sponsorNode.setDirection(null);
            sponsorNode.setPlacementSide(TBinaryTree.PLACEMENT_AUTO);
            sponsorNode.setLeftCount(0);
            sponsorNode.setRightCount(0);
            binaryTreeMapper.insert(sponsorNode);
        }

        // 步骤 2：强制按 sponsor 累计业绩弱区放置（PRD §7.3）
        String direction = pickWeakSideByVolume(sponsorId);

        // 步骤 3：从 sponsor 沿 direction 静态滑落，找到第一个该方向为空的祖先 P
        Long placementParentId = sponsorId;
        while (true) {
            TBinaryTree child = binaryTreeMapper.findChild(placementParentId, direction);
            if (child == null) {
                break; // P = placementParentId 该方向是空的，挂这里
            }
            placementParentId = child.getUserId();
        }

        // 步骤 4：写入 newUser 节点
        TBinaryTree node = new TBinaryTree();
        node.setUserId(newUserId);
        node.setSponsorId(sponsorId);
        node.setParentId(placementParentId);
        node.setDirection(direction);
        node.setPlacementSide(TBinaryTree.PLACEMENT_AUTO);
        node.setLeftCount(0);
        node.setRightCount(0);
        binaryTreeMapper.insert(node);

        // 步骤 5：沿 placementParent 一路向上回溯，每个祖先在含 newUser 的那一区 +1
        bumpAncestorCount(placementParentId, direction);
    }

    /**
     * PRD §7.3：sponsor 左区累计业绩 <= 右区累计业绩 → 放左区，否则放右区。
     * 没有业绩记录（新代理 / 全 0）时同样落左区。
     */
    private String pickWeakSideByVolume(Long sponsorId) {
        TBinaryVolumeTotal vol = binaryVolumeTotalMapper.selectByUserId(sponsorId);
        BigDecimal left = vol == null || vol.getLeftVolumeTotal() == null
                ? BigDecimal.ZERO : vol.getLeftVolumeTotal();
        BigDecimal right = vol == null || vol.getRightVolumeTotal() == null
                ? BigDecimal.ZERO : vol.getRightVolumeTotal();
        return left.compareTo(right) <= 0
                ? TBinaryTree.DIRECTION_LEFT
                : TBinaryTree.DIRECTION_RIGHT;
    }

    /**
     * 从 startUserId 开始向上回溯，每经过一个祖先就给"含 newUser 的那一区"计数 +1。
     *
     * 第一次：startUserId 是 newUser 的直接 parent，含 newUser 的方向 = startDirection
     * 之后每一层：含 newUser 的方向 = 当前节点在它 parent 下的方向（child.direction）
     */
    private void bumpAncestorCount(Long startUserId, String startDirection) {
        Long curUserId = startUserId;
        String curDirection = startDirection;
        // 防御：限制最大向上回溯深度，避免数据脏导致死循环
        int maxDepth = 10000;
        while (curUserId != null && maxDepth-- > 0) {
            if (TBinaryTree.DIRECTION_LEFT.equals(curDirection)) {
                binaryTreeMapper.incrementLeftCount(curUserId);
            } else if (TBinaryTree.DIRECTION_RIGHT.equals(curDirection)) {
                binaryTreeMapper.incrementRightCount(curUserId);
            }
            // 上一级
            TBinaryTree node = binaryTreeMapper.selectByUserId(curUserId);
            if (node == null || node.getParentId() == null) {
                break;
            }
            curDirection = node.getDirection(); // 当前节点在它 parent 下的方向
            curUserId = node.getParentId();
        }
        if (maxDepth <= 0) {
            log.error("bumpAncestorCount hit maxDepth, possible cycle. startUserId={}", startUserId);
        }
    }

    /**
     * 沿 buyer 双轨链向上累计业绩。
     * 第一层：buyer 直接 parent，含 buyer 的方向 = buyer.direction
     * 后续层：当前节点在它 parent 下的方向 = node.direction
     */
    @Override
    @Transactional
    public void accumulateVolume(Long buyerUserId, BigDecimal amountUsdt) {
        if (buyerUserId == null || amountUsdt == null
                || amountUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TBinaryTree buyerNode = binaryTreeMapper.selectByUserId(buyerUserId);
        if (buyerNode == null) {
            log.warn("accumulateVolume skipped: buyer not in binary tree. userId={}", buyerUserId);
            return;
        }
        if (buyerNode.getParentId() == null) {
            // buyer 自己就是根节点，无祖先可累计
            return;
        }

        Date bizDate = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC));

        Long curUserId = buyerNode.getParentId();
        String curDirection = buyerNode.getDirection();
        int maxDepth = 10000;
        while (curUserId != null && maxDepth-- > 0) {
            applyVolumeBump(curUserId, curDirection, bizDate, amountUsdt);

            TBinaryTree ancestor = binaryTreeMapper.selectByUserId(curUserId);
            if (ancestor == null || ancestor.getParentId() == null) {
                break;
            }
            curDirection = ancestor.getDirection();
            curUserId = ancestor.getParentId();
        }
        if (maxDepth <= 0) {
            log.error("accumulateVolume hit maxDepth, possible cycle. buyerUserId={}", buyerUserId);
        }
    }

    private void applyVolumeBump(Long userId, String direction, Date bizDate, BigDecimal amount) {
        if (TBinaryTree.DIRECTION_LEFT.equals(direction)) {
            binaryVolumeDailyMapper.upsertLeftVolume(userId, bizDate, amount);
            binaryVolumeTotalMapper.upsertLeftTotal(userId, amount);
        } else if (TBinaryTree.DIRECTION_RIGHT.equals(direction)) {
            binaryVolumeDailyMapper.upsertRightVolume(userId, bizDate, amount);
            binaryVolumeTotalMapper.upsertRightTotal(userId, amount);
        }
    }
}
