package com.ruoyi.web.controller.bussiness;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.bussiness.domain.TC2cOrderAppeal;
import com.ruoyi.bussiness.domain.TC2cAppealMessage;
import com.ruoyi.bussiness.domain.dto.C2cAppealResolveDTO;
import com.ruoyi.bussiness.domain.vo.C2cOrderVO;
import com.ruoyi.bussiness.service.IC2cAppealService;
import com.ruoyi.bussiness.service.IC2cOrderService;

/**
 * C2C申诉管理Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/bussiness/c2c/appeal")
public class C2cAppealController extends BaseController {

    @Autowired
    private IC2cAppealService c2cAppealService;

    @Autowired
    private IC2cOrderService c2cOrderService;

    /**
     * 查询C2C申诉列表
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:appeal:list')")
    @GetMapping("/list")
    public TableDataInfo list(TC2cOrderAppeal appeal) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        if (!user.isAdmin()) {
            appeal.setAdminParentIds(String.valueOf(user.getUserId()));
        }
        startPage();
        List<TC2cOrderAppeal> list = c2cAppealService.selectAppealList(appeal);
        return getDataTable(list);
    }

    /**
     * 获取C2C申诉详细信息（含消息记录）
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:appeal:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        TC2cOrderAppeal appeal = c2cAppealService.getById(id);
        if (appeal == null) {
            return AjaxResult.error("申诉记录不存在");
        }
        List<TC2cAppealMessage> messages = c2cAppealService.getAppealMessages(id);
        C2cOrderVO order = c2cOrderService.getOrderDetail(appeal.getOrderId(), null);
        AjaxResult result = AjaxResult.success(appeal);
        result.put("messages", messages);
        result.put("order", order);
        return result;
    }

    /**
     * 管理员处理申诉
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:appeal:edit')")
    @Log(title = "C2C申诉管理", businessType = BusinessType.UPDATE)
    @PostMapping("/resolve")
    public AjaxResult resolve(@RequestBody C2cAppealResolveDTO dto) {
        Long adminId = SecurityUtils.getUserId();
        String error = c2cAppealService.resolveAppeal(dto, adminId);
        return error == null ? AjaxResult.success() : AjaxResult.error(error);
    }

    /**
     * 管理员发送申诉消息
     */
    @PreAuthorize("@ss.hasPermi('bussiness:c2c:appeal:edit')")
    @Log(title = "C2C申诉消息", businessType = BusinessType.INSERT)
    @PostMapping("/message")
    public AjaxResult sendMessage(@RequestBody Map<String, Object> params) {
        Long appealId = Long.valueOf(params.get("appealId").toString());
        Integer contentType = params.get("contentType") == null ? 1 : Integer.valueOf(params.get("contentType").toString());
        String content = params.get("content").toString();
        Long adminId = SecurityUtils.getUserId();
        // senderType=2 表示管理员
        return toAjax(c2cAppealService.addMessage(appealId, adminId, 2, contentType, content));
    }
}
