package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.MyMinerVO;

import java.util.List;

/**
 * 矿机查询（用户端）。与 INodeInstanceService（admin 端）拆开避免相互污染。
 *
 * @date 2026-05-09
 */
public interface INodeQueryService {

    /**
     * 我的矿机列表。
     *
     * 排序：active 优先 → 同 status 按 levelCode desc → 同 level 按 activatedAt asc。
     * isCurrentActive：active 状态下 levelCode 优先级最高（L4>L3>L2>L1），
     * 同级取 activatedAt 最早一台标记为 true（PRD §22 第 6 条）。
     *
     * 返回包含已 expired / frozen / cancelled 的矿机，让用户能看到状态历史。
     */
    List<MyMinerVO> listMyMiners(Long userId);
}
