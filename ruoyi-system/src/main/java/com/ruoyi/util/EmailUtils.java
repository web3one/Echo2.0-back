package com.ruoyi.util;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruoyi.bussiness.domain.setting.EmailSetting;
import com.ruoyi.bussiness.domain.setting.Setting;
import com.ruoyi.bussiness.service.SettingService;
import com.ruoyi.bussiness.service.impl.TAppUserServiceImpl;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.CachePrefix;
import com.ruoyi.common.enums.SettingEnum;
import com.ruoyi.common.enums.UserCodeTypeEnum;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.SpringContextUtil;
import com.ruoyi.common.utils.sms.SmsSenderUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @packageName:com.zebra.common.utils
 * @className:EmailUtils
 * @description:邮箱工具类
 * @version:1.0.0
 * @author:xuanyihun
 * @createDate:2022-05-06 11:07
 */
public class EmailUtils {

    private static final Logger log = LoggerFactory.getLogger(EmailUtils.class);
    private static final String APIHZ_SEND_URL = "https://cn.apihz.cn/api/mail/yzm1.php";
    private static final String APIHZ_VERIFY_URL = "https://cn.apihz.cn/api/mail/yzm2.php";
    private static final String APIHZ_CODE_MARKER = "__APIHZ_EMAIL_CODE__";

    /**
     * @description 验证邮箱
     * @param email(邮箱)
     * @version 1.0.0
     * @author xuanyihun
     * @createDate 2022-05-06 11:23
     * @return boolean
    */
    public static boolean checkEmail(String email) {

        boolean flag = false;
        try {
            String check = "^([a-z0-9A-Z]+[-|_|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$";
            Pattern regex = Pattern.compile(check);
            Matcher matcher = regex.matcher(email);
            flag = matcher.matches();
        } catch (Exception exception) {
            flag = false;
        }
        return flag;

    }
    /**
     * 组成邮箱发送公共方法
     * @return
     */
//    public static void formMail(String email,String type) {
//        RedisCache redisCache = SpringContextUtil.getBean(RedisCache.class);
//        SettingService settingService = SpringContextUtil.getBean(SettingService.class);
//        TAppUserServiceImpl bean = SpringContextUtil.getBean(TAppUserServiceImpl.class);
//        String randomCode = String.valueOf(SmsSenderUtil.getRandomNumber(100000, 999999));
//        Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
//        EmailSetting emailSetting = JSONUtil.toBean(setting.getSettingValue(), EmailSetting.class);
//        String appName = emailSetting.getMailAppName();
//        String host = emailSetting.getMailHost();
//        int port = Integer.parseInt(emailSetting.getMailPort());
//        String username = emailSetting.getMailUsername();
//        String password = emailSetting.getMailPassword();
//        String templateCode = emailSetting.getMailTemplate();
//        JavaMailSenderImpl jms = new JavaMailSenderImpl();
//        jms.setHost(host);
//        jms.setPort(port);
//        jms.setUsername(username);
//        jms.setPassword(password);
//        jms.setDefaultEncoding("Utf-8");
//        Properties p = new Properties();
//        p.setProperty("mail.smtp.auth", "true");
//        p.setProperty("mail.smtp.starttls.enable", "true");
//        p.setProperty("mail.smtp.starttls.required", "true");
//        p.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
//        p.setProperty("mail.smtp.ssl.enable", "true");
//        jms.setJavaMailProperties(p);
//        MimeMessage mimeMessage = jms.createMimeMessage();
//        MimeMessageHelper helper = null;
//        try {
//            helper = new MimeMessageHelper(mimeMessage, true);
//            helper.setFrom(username);
//            helper.setTo(email);
//            helper.setSubject(appName);
//            Map<String, Object> model = new HashMap<>(16);
//            model.put("code", randomCode);
//            Configuration cfg = new Configuration(Configuration.VERSION_2_3_26);
//            cfg.setClassForTemplateLoading(bean.getClass(), "/templates");
//            Template template = cfg.getTemplate(templateCode);
//            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
//            helper.setText(html, true);
//            //发送邮件
//            jms.send(mimeMessage);
//            //绑定邮箱
//        } catch (Exception  e) {
//            e.printStackTrace();
//        }
//        redisCache.setCacheObject(CachePrefix.EMAIL_CODE.getPrefix()+ UserCodeTypeEnum.valueOf(type)+ email, randomCode, CacheConstants.REGISTER_CODE_TIME, TimeUnit.SECONDS);
//
//    }


    public static String formMail(String email,String type) {
        RedisCache redisCache = SpringContextUtil.getBean(RedisCache.class);
        SettingService settingService = SpringContextUtil.getBean(SettingService.class);
        String randomCode = String.valueOf(SmsSenderUtil.getRandomNumber(100000, 999999));
        TAppUserServiceImpl bean = SpringContextUtil.getBean(TAppUserServiceImpl.class);
        Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
        if (setting == null || StringUtils.isBlank(setting.getSettingValue())) {
            if (isLocalEmailFallbackEnabled()) {
                cacheLocalEmailCode(redisCache, email, type, randomCode, "EMAIL_SETTING is empty");
                return randomCode;
            }
            throw new IllegalStateException("EMAIL_SETTING is empty");
        }
        EmailSetting emailSetting = JSONUtil.toBean(setting.getSettingValue(), EmailSetting.class);
        if (isApiHzEmailSetting(emailSetting)) {
            sendApiHzEmailCode(redisCache, email, type, randomCode, emailSetting);
            return "sent";
        }
        String appName = trimToEmpty(emailSetting.getMailAppName());
        String host = trimToEmpty(emailSetting.getMailHost());
        String port = trimToEmpty(emailSetting.getMailPort());
        String username = trimToEmpty(emailSetting.getMailUsername());
        String password = trimToEmpty(emailSetting.getMailPassword());
        String from = StringUtils.isNotBlank(emailSetting.getMailFrom()) ? emailSetting.getMailFrom().trim() : username;
        String templateCode = trimToEmpty(emailSetting.getMailTemplate());
        String missing = missingSmtpConfig(host, port, username, password, templateCode);
        if (StringUtils.isNotBlank(missing)) {
            if (isLocalEmailFallbackEnabled()) {
                cacheLocalEmailCode(redisCache, email, type, randomCode, "EMAIL_SETTING missing " + missing);
                return randomCode;
            }
            throw new IllegalStateException("EMAIL_SETTING missing " + missing);
        }
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            if (isLocalEmailFallbackEnabled()) {
                cacheLocalEmailCode(redisCache, email, type, randomCode, "EMAIL_SETTING invalid mailPort=" + port);
                return randomCode;
            }
            throw new IllegalStateException("EMAIL_SETTING invalid mailPort=" + port, e);
        }

        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.transport.protocol","smtp");
        properties.setProperty("mail.smtp.port", port);
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.setProperty("mail.smtp.ssl.trust", host);
        if (portNumber == 465) {
            properties.setProperty("mail.smtp.ssl.enable", "true");
        } else {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        }

        Transport transport = null;
        try {
            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Map<String, Object> model = new HashMap<>(16);
            model.put("code", randomCode);
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_26);
            cfg.setClassForTemplateLoading(bean.getClass(), "/templates");
            Template template = cfg.getTemplate(templateCode);
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            MimeMessage message = new MimeMessage(session);
            String title = StringUtils.isNotBlank(appName) ? appName : from;
            message.setFrom(new InternetAddress(from, title, "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            message.setSubject(title, "UTF-8");
            message.setSentDate(new Date());
            message.setContent(html, "text/html;charset=UTF-8");

            transport = session.getTransport("smtp");
            transport.connect(host, portNumber, username, password);
            transport.sendMessage(message,message.getAllRecipients());
        } catch (Exception e) {
            if (isLocalEmailFallbackEnabled()) {
                cacheLocalEmailCode(redisCache, email, type, randomCode,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
                return randomCode;
            }
            throw new RuntimeException("Send email failed via host=" + host
                    + ", port=" + port
                    + ", username=" + mask(username)
                    + ", from=" + from
                    + ": " + e.getClass().getSimpleName()
                    + ": " + e.getMessage(), e);
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException ignored) {
                }
            }
        }
        cacheEmailCode(redisCache, email, type, randomCode);
        return randomCode;

    }

    public static boolean verifyEmailCode(RedisCache redisCache, String email, String type, String code) {
        if (redisCache == null || StringUtils.isBlank(email) || StringUtils.isBlank(type) || StringUtils.isBlank(code)) {
            return false;
        }
        String emailCodeKey = CachePrefix.EMAIL_CODE.getPrefix() + UserCodeTypeEnum.valueOf(type) + email;
        if (!Boolean.TRUE.equals(redisCache.hasKey(emailCodeKey))) {
            return false;
        }
        Object cacheValue = redisCache.getCacheObject(emailCodeKey);
        String validCode = cacheValue == null ? "" : cacheValue.toString();
        boolean verified;
        if (APIHZ_CODE_MARKER.equals(validCode)) {
            verified = verifyApiHzEmailCode(email, code);
        } else {
            verified = code.equalsIgnoreCase(validCode);
        }
        if (verified) {
            redisCache.deleteObject(emailCodeKey);
        }
        return verified;
    }

    private static void cacheEmailCode(RedisCache redisCache, String email, String type, String randomCode) {
        redisCache.setCacheObject(CachePrefix.EMAIL_CODE.getPrefix() + UserCodeTypeEnum.valueOf(type) + email,
                randomCode, CacheConstants.REGISTER_CODE_TIME, TimeUnit.SECONDS);
    }

    private static void cacheApiHzEmailCodeMarker(RedisCache redisCache, String email, String type) {
        redisCache.setCacheObject(CachePrefix.EMAIL_CODE.getPrefix() + UserCodeTypeEnum.valueOf(type) + email,
                APIHZ_CODE_MARKER, CacheConstants.REGISTER_CODE_TIME, TimeUnit.SECONDS);
    }

    private static void cacheLocalEmailCode(RedisCache redisCache, String email, String type, String randomCode, String reason) {
        cacheEmailCode(redisCache, email, type, randomCode);
        log.warn("Local email code fallback enabled, type={}, email={}, code={}, reason={}",
                type, email, randomCode, reason);
    }

    private static boolean isLocalEmailFallbackEnabled() {
        try {
            Environment environment = SpringContextUtil.getBean(Environment.class);
            Boolean enabled = environment.getProperty("email.local-fallback.enabled", Boolean.class, false);
            return Boolean.TRUE.equals(enabled) || environment.acceptsProfiles(Profiles.of("dev", "local"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isApiHzEmailSetting(EmailSetting emailSetting) {
        return emailSetting != null
                && StringUtils.isNotBlank(emailSetting.getApiId())
                && StringUtils.isNotBlank(emailSetting.getApiKey());
    }

    private static void sendApiHzEmailCode(RedisCache redisCache, String email, String type, String randomCode, EmailSetting emailSetting) {
        String apiUrl = StringUtils.isNotBlank(emailSetting.getApiUrl()) ? emailSetting.getApiUrl().trim() : APIHZ_SEND_URL;
        String name = StringUtils.isNotBlank(emailSetting.getSenderName()) ? emailSetting.getSenderName().trim() : "XAgent";
        String title = name + " verification code";
        Map<String, Object> params = new HashMap<>(16);
        params.put("id", emailSetting.getApiId().trim());
        params.put("key", emailSetting.getApiKey().trim());
        params.put("name", name);
        params.put("tomail", email);
        params.put("title", title);
        params.put("headtitle", title);
        params.put("text", "You are requesting an XAgent verification code. It is valid for five minutes.");
        params.put("foot1", name);
        params.put("foot2", "Security verification");
        try {
            String body = HttpUtil.createPost(apiUrl).form(params).timeout(10000).execute().body();
            JSONObject response = JSONUtil.parseObj(body);
            Integer code = response.getInt("code");
            if (code != null && code == 200) {
                cacheApiHzEmailCodeMarker(redisCache, email, type);
                return;
            }
            String msg = response.getStr("msg", body);
            throw new RuntimeException("APIHZ send failed: " + msg);
        } catch (Exception e) {
            if (isLocalEmailFallbackEnabled()) {
                cacheLocalEmailCode(redisCache, email, type, randomCode,
                        "APIHZ send failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return;
            }
            throw new RuntimeException("APIHZ send email failed: " + e.getMessage(), e);
        }
    }

    private static boolean verifyApiHzEmailCode(String email, String code) {
        try {
            SettingService settingService = SpringContextUtil.getBean(SettingService.class);
            Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
            if (setting == null || StringUtils.isBlank(setting.getSettingValue())) {
                return false;
            }
            EmailSetting emailSetting = JSONUtil.toBean(setting.getSettingValue(), EmailSetting.class);
            if (!isApiHzEmailSetting(emailSetting)) {
                return false;
            }
            String verifyUrl = StringUtils.isNotBlank(emailSetting.getVerifyUrl()) ? emailSetting.getVerifyUrl().trim() : APIHZ_VERIFY_URL;
            Map<String, Object> params = new HashMap<>(8);
            params.put("id", emailSetting.getApiId().trim());
            params.put("key", emailSetting.getApiKey().trim());
            params.put("mail", email);
            params.put("code", code);
            String body = HttpUtil.createPost(verifyUrl).form(params).timeout(10000).execute().body();
            JSONObject response = JSONUtil.parseObj(body);
            Integer responseCode = response.getInt("code");
            return responseCode != null && responseCode == 200;
        } catch (Exception e) {
            log.warn("APIHZ verify email code failed, email={}, reason={}", email, e.getMessage());
            return false;
        }
    }

    private static String missingSmtpConfig(String host, String port, String username, String password, String templateCode) {
        StringBuilder missing = new StringBuilder();
        appendMissing(missing, host, "mailHost");
        appendMissing(missing, port, "mailPort");
        appendMissing(missing, username, "mailUsername");
        appendMissing(missing, password, "mailPassword");
        appendMissing(missing, templateCode, "mailTemplate");
        return missing.toString();
    }

    private static void appendMissing(StringBuilder missing, String value, String name) {
        if (StringUtils.isBlank(value)) {
            if (missing.length() > 0) {
                missing.append(",");
            }
            missing.append(name);
        }
    }

    private static String mask(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String trimmedValue = value.trim();
        if (trimmedValue.length() <= 6) {
            return "***";
        }
        return trimmedValue.substring(0, 3) + "***" + trimmedValue.substring(trimmedValue.length() - 3);
    }
}
