package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.vo.MyXgtLocksVO;
import com.ruoyi.bussiness.service.IXgtQueryService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * XGT 站内积分用户端 Controller（PRD §13 + §17 GET /xgt/locks/my）。
 *
 * 当前接口：
 *   GET /api/xgt/locks/my  我的锁仓计划（含余额汇总 + 计划列表）
 *
 * @date 2026-05-09
 */
@RestController
@RequestMapping("/api/xgt")
@Slf4j
public class ApiXgtController extends ApiBaseController {

    @Resource
    private IXgtQueryService xgtQueryService;

    @GetMapping("/locks/my")
    public AjaxResult myLocks() {
        MyXgtLocksVO vo = xgtQueryService.myLocks(getStpUserId());
        return success(vo);
    }
}
