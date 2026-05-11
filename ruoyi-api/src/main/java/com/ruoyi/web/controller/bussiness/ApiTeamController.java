package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.vo.BinaryTeamVO;
import com.ruoyi.bussiness.service.IBinaryTeamQueryService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 双轨团队用户端 Controller（PRD §17.4 GET /team/binary）。
 *
 * 返回字段对齐 mining-gold 子站 renderNetwork：
 * - 当日 / 累计 业绩双栏
 * - 当前代理等级 + 匹配率 + 全网分红率
 * - 升级目标 + 是否已满足条件
 *
 * @date 2026-05-09
 */
@RestController
@RequestMapping("/api/team")
@Slf4j
public class ApiTeamController extends ApiBaseController {

    @Resource
    private IBinaryTeamQueryService binaryTeamQueryService;

    @GetMapping("/binary")
    public AjaxResult binary() {
        BinaryTeamVO vo = binaryTeamQueryService.queryMyTeam(getStpUserId());
        return success(vo);
    }
}
