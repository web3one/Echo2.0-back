package com.ruoyi.bussiness.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.TXgtLockPlan;
import com.ruoyi.bussiness.domain.dto.XgtLockPlanCreateDTO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanAdminVO;
import com.ruoyi.bussiness.domain.vo.XgtLockPlanDetailVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Admin 端 XGT 锁仓管理服务（PRD §15.5）。
 *
 * 覆盖 PRD §15.5 全部 10 个能力：
 *   1. 创建锁仓计划
 *   2. 设置锁仓开始时间
 *   3. 默认 30 天
 *   4. 查看锁仓状态
 *   5. 查看预计释放时间
 *   6. 冻结锁仓计划
 *   7. 恢复锁仓计划
 *   8. 手动补发失败释放（调 releaseOnePlan）
 *   9. 查看释放流水
 *  10. 导出释放记录
 *
 * 自动路径（static_reward / founder_seat / credit_unlock）由 cron / 业务流程
 * 自动创建，不通过此 admin service。
 *
 * @date 2026-05-12
 */
public interface IXgtLockAdminService {

    /** 分页列表（多条件筛选） */
    IPage<XgtLockPlanAdminVO> pageList(int pageNum, int pageSize,
                                       Long userId, String sourceType, String status,
                                       Date beginLockedAt, Date endLockedAt,
                                       Date beginReleaseAt, Date endReleaseAt);

    /** 详情（含用户当前 XGT 余额快照） */
    XgtLockPlanDetailVO getDetail(Long id);

    /**
     * Admin 手工创建锁仓计划：写 plan + balance_locked += amount + xgt_log(lock)。
     * sourceType 仅允许 admin 手工类（team_advisor / private_sale / ecosystem_fund / partner）。
     * 撞 UNIQUE(source_type, source_ref_id) 抛业务异常。
     */
    TXgtLockPlan create(XgtLockPlanCreateDTO dto, Long adminId);

    /** 冻结（仅 locked 可冻结，frozen 后 cron 跳过） */
    TXgtLockPlan freeze(Long id, Long adminId, String reason);

    /** 解冻（frozen → locked，cron 下次扫到期会自动释放） */
    TXgtLockPlan unfreeze(Long id, Long adminId, String reason);

    /**
     * 手动补发释放：调 IXgtLockReleaseSettleService.releaseOnePlan。
     * 仅 locked 状态可触发；释放过的 / frozen 的会返回 skipped。
     */
    Map<String, Object> retry(Long id, Long adminId);

    /** 导出列表（同 pageList 查询条件，不分页，cap 5000 防爆） */
    List<XgtLockPlanAdminVO> listForExport(Long userId, String sourceType, String status,
                                           Date beginLockedAt, Date endLockedAt,
                                           Date beginReleaseAt, Date endReleaseAt);
}
