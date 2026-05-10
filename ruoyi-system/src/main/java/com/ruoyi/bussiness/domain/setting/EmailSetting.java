package com.ruoyi.bussiness.domain.setting;

import lombok.Data;

/**
 * 邮箱配置
 */
@Data
public class EmailSetting {

    private String apiId;
    private String apiKey;
    private String senderName;
    private String apiUrl;
    private String verifyUrl;
    private String mailTemplate;
    private String mailAppName;
    private String mailUsername;
    private String mailPassword;
    private String mailHost;
    private String mailPort;
    private String mailFrom;
}
