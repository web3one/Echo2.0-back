package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.bussiness.domain.TAppUser;
import lombok.Data;

import java.io.Serializable;

/**
 * 金矿 H5 当前登录用户基础资料。
 */
@Data
public class GoldProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String loginName;
    private String email;
    private String activeCode;

    public static GoldProfileVO from(TAppUser user) {
        GoldProfileVO vo = new GoldProfileVO();
        if (user == null) {
            return vo;
        }
        vo.setUserId(user.getUserId());
        vo.setLoginName(user.getLoginName());
        vo.setEmail(user.getEmail());
        vo.setActiveCode(user.getActiveCode());
        return vo;
    }
}
