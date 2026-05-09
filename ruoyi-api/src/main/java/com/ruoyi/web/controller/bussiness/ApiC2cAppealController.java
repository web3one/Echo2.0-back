package com.ruoyi.web.controller.bussiness;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.bussiness.domain.TC2cOrderAppeal;
import com.ruoyi.bussiness.domain.TC2cAppealMessage;
import com.ruoyi.bussiness.domain.dto.C2cAppealCreateDTO;
import com.ruoyi.bussiness.service.IC2cAppealService;
import com.ruoyi.web.controller.common.ApiBaseController;

/**
 * C2C申诉 - 用户端Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/c2c/appeal")
public class ApiC2cAppealController extends ApiBaseController {

    @Resource
    private IC2cAppealService c2cAppealService;

    /**
     * 创建申诉
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody C2cAppealCreateDTO dto) {
        String error = c2cAppealService.createAppeal(getStpUserId(), dto);
        return error == null ? success() : error(error);
    }

    /**
     * 添加申诉消息
     */
    @PostMapping("/message")
    public AjaxResult addMessage(@RequestBody Map<String, Object> params) {
        Long appealId = Long.valueOf(params.get("appealId").toString());
        Integer contentType = params.get("contentType") == null ? 1 : Integer.valueOf(params.get("contentType").toString());
        String content = params.get("content").toString();
        // senderType=1 表示用户
        int rows = c2cAppealService.addMessage(appealId, getStpUserId(), 1, contentType, content);
        return rows > 0 ? success() : error("发送失败");
    }

    /**
     * 根据订单ID获取申诉信息
     */
    @PostMapping("/{orderId}")
    public AjaxResult getByOrderId(@PathVariable Long orderId) {
        TC2cOrderAppeal appeal = c2cAppealService.getAppealByOrderId(orderId, getStpUserId());
        if (appeal == null) {
            return error("该订单暂无申诉记录");
        }
        return success(appeal);
    }

    /**
     * 获取申诉消息列表
     */
    @PostMapping("/messages/{appealId}")
    public AjaxResult getMessages(@PathVariable Long appealId) {
        List<TC2cAppealMessage> messages = c2cAppealService.getAppealMessages(appealId, getStpUserId());
        return success(messages);
    }
}
