package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.dto.EcosystemCreditUnlockDTO;
import com.ruoyi.bussiness.domain.vo.EcosystemCreditUnlockResultVO;
import com.ruoyi.bussiness.domain.vo.MyEcosystemCreditVO;
import com.ruoyi.bussiness.service.IEcosystemCreditService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.common.ApiBaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * ecosystem_credit 用户端 Controller（PRD §10 + §17 POST /ecosystem-credit/unlock）。
 *
 * @date 2026-05-11 v3.10 C-2
 */
@RestController
@RequestMapping("/api/ecosystem-credit")
@Slf4j
public class ApiEcosystemCreditController extends ApiBaseController {

    @Resource
    private IEcosystemCreditService ecosystemCreditService;

    @GetMapping("/my")
    public AjaxResult my() {
        MyEcosystemCreditVO vo = ecosystemCreditService.getMyCredit(getStpUserId());
        return success(vo);
    }

    @PostMapping("/unlock")
    public AjaxResult unlock(@RequestBody EcosystemCreditUnlockDTO dto) {
        EcosystemCreditUnlockResultVO vo = ecosystemCreditService.submitUnlock(getStpUserId(), dto);
        return success(vo);
    }
}
