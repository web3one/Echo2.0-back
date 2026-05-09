package com.ruoyi.web.controller.bussiness;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.dto.C2cAdvertCreateDTO;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import com.ruoyi.bussiness.service.IC2cAdvertService;
import com.ruoyi.web.controller.common.ApiBaseController;

/**
 * C2C广告 - 用户端Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/c2c/advert")
public class ApiC2cAdvertController extends ApiBaseController {

    @Resource
    private IC2cAdvertService c2cAdvertService;

    /**
     * 查询公开广告列表（支持adType/cryptoCurrency筛选）
     */
    @PostMapping("/list")
    public TableDataInfo list(@RequestBody TC2cAdvert query) {
        startPage();
        List<C2cAdvertVO> list = c2cAdvertService.listPublicAdverts(query);
        return getDataTable(list);
    }

    /**
     * 获取广告详情
     */
    @PostMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        TC2cAdvert advert = c2cAdvertService.getAdvertDetail(id);
        if (advert == null) {
            return error("广告不存在");
        }
        return success(advert);
    }

    /**
     * 发布广告（商家专属）
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody C2cAdvertCreateDTO dto) {
        String error = c2cAdvertService.createAdvert(getStpUserId(), dto);
        return error == null ? success() : error(error);
    }

    /**
     * 查询我的广告列表
     */
    @PostMapping("/myList")
    public TableDataInfo myList() {
        startPage();
        List<TC2cAdvert> list = c2cAdvertService.listMyAdverts(getStpUserId());
        return getDataTable(list);
    }

    /**
     * 下架广告
     */
    @PostMapping("/offline/{id}")
    public AjaxResult offline(@PathVariable Long id) {
        int rows = c2cAdvertService.offlineAdvert(id, getStpUserId());
        return rows > 0 ? success() : error("操作失败");
    }

    /**
     * 上架广告
     */
    @PostMapping("/online/{id}")
    public AjaxResult online(@PathVariable Long id) {
        int rows = c2cAdvertService.onlineAdvert(id, getStpUserId());
        return rows > 0 ? success() : error("操作失败");
    }
}
