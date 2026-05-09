package com.ruoyi.util;

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


    public static void formMail(String email,String type) {
        RedisCache redisCache = SpringContextUtil.getBean(RedisCache.class);
        SettingService settingService = SpringContextUtil.getBean(SettingService.class);
        String randomCode = String.valueOf(SmsSenderUtil.getRandomNumber(100000, 999999));
        TAppUserServiceImpl bean = SpringContextUtil.getBean(TAppUserServiceImpl.class);
        Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
        if (setting == null || StringUtils.isBlank(setting.getSettingValue())) {
            throw new IllegalStateException("EMAIL_SETTING is empty");
        }
        EmailSetting emailSetting = JSONUtil.toBean(setting.getSettingValue(), EmailSetting.class);
        String appName = emailSetting.getMailAppName();
        String host = emailSetting.getMailHost();
        String port = emailSetting.getMailPort();
        String username = emailSetting.getMailUsername();
        String password = emailSetting.getMailPassword();
        String from = StringUtils.isNotBlank(emailSetting.getMailFrom()) ? emailSetting.getMailFrom().trim() : username;
        String templateCode = emailSetting.getMailTemplate();
        int portNumber = Integer.parseInt(port);

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
        redisCache.setCacheObject(CachePrefix.EMAIL_CODE.getPrefix()+ UserCodeTypeEnum.valueOf(type)+ email, randomCode, CacheConstants.REGISTER_CODE_TIME, TimeUnit.SECONDS);

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
