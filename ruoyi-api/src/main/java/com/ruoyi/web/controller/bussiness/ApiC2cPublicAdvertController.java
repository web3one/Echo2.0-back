package com.ruoyi.web.controller.bussiness;

import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.vo.C2cAdvertVO;
import com.ruoyi.bussiness.service.IC2cAdvertService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.web.controller.common.ApiBaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * C2C公开广告接口
 */
@RestController
@RequestMapping("/api/c2c/public/advert")
public class ApiC2cPublicAdvertController extends ApiBaseController {

    @Resource
    private IC2cAdvertService c2cAdvertService;

    @PostMapping("/list")
    public TableDataInfo list(@RequestBody(required = false) TC2cAdvert query) {
        startPage();
        List<C2cAdvertVO> list = c2cAdvertService.listPublicAdverts(query == null ? new TC2cAdvert() : query);
        return getDataTable(list);
    }

    @PostMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        C2cAdvertVO advert = c2cAdvertService.getPublicAdvertDetail(id);
        if (advert == null) {
            return error("广告不存在");
        }
        return success(advert);
    }
}
