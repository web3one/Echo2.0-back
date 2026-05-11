package com.ruoyi.web.controller.bussiness;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.bussiness.domain.dto.RewardQueryDTO;
import com.ruoyi.bussiness.domain.vo.RewardEntryVO;
import com.ruoyi.bussiness.service.IRewardLogQueryService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 金矿奖励流水用户端 Controller（PRD §17 GET /rewards/daily）。
 *
 * 走 POST 而非 GET：复杂筛选参数（rewardType/relatedNodeInstanceId/bizDateFrom/bizDateTo
 * + 分页）走 body 更稳，与 echo2-api 既有 list 接口风格一致。
 *
 * 返回结构（mining-gold useState 直接当列表用 rows 字段）：
 *   { rows: [...RewardEntryVO], total, pageNum, pageSize }
 *
 * @date 2026-05-09
 */
@RestController
@RequestMapping("/api/rewards")
@Slf4j
public class ApiRewardController extends ApiBaseController {

    @Resource
    private IRewardLogQueryService rewardLogQueryService;

    @PostMapping("/daily")
    public AjaxResult daily(@RequestBody(required = false) RewardQueryDTO query) {
        IPage<RewardEntryVO> page = rewardLogQueryService.pageMyRewards(getStpUserId(), query);
        Map<String, Object> resp = new HashMap<>();
        resp.put("rows", page.getRecords());
        resp.put("total", page.getTotal());
        resp.put("pageNum", page.getCurrent());
        resp.put("pageSize", page.getSize());
        return success(resp);
    }
}
