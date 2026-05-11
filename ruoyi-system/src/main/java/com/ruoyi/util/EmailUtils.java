package com.ruoyi.util;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.bussiness.domain.setting.EmailSetting;
import com.ruoyi.bussiness.domain.setting.Setting;
import com.ruoyi.bussiness.service.SettingService;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.SettingEnum;
import com.ruoyi.common.enums.UserCodeTypeEnum;
import com.ruoyi.common.utils.SpringContextUtil;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮箱验证码工具类。
 *
 * 走 apihz.cn mailyzm1（发送）/ mailyzm2（验证）。验证码由 apihz 生成与校验，
 * 我们只在 Redis 写一个 type 上下文标记，防止跨场景误用。
 */
public class EmailUtils {

    private static final Logger log = LoggerFactory.getLogger(EmailUtils.class);

    private static final String SEND_URL = "https://cn.apihz.cn/api/mail/yzm1.php";
    private static final String VERIFY_URL = "https://cn.apihz.cn/api/mail/yzm2.php";
    /** apihz 验证码有效期 5 分钟，与对方一致 */
    private static final int CODE_TTL_SECONDS = 300;
    private static final String CTX_KEY_PREFIX = "{EMAIL_CODE}_:CTX:";

    private static final Map<UserCodeTypeEnum, String> SCENE_TEXT = new EnumMap<>(UserCodeTypeEnum.class);
    static {
        SCENE_TEXT.put(UserCodeTypeEnum.REGISTER,
                "You are registering for an xagent account. Use the verification code below to complete your registration.");
        SCENE_TEXT.put(UserCodeTypeEnum.LOGIN,
                "You are signing in to your xagent account. Use the verification code below to complete your sign-in.");
        SCENE_TEXT.put(UserCodeTypeEnum.FIND_USER,
                "You are recovering your xagent account. Use the verification code below to continue.");
        SCENE_TEXT.put(UserCodeTypeEnum.FIND_PASSWORD,
                "You are resetting your xagent password. Use the verification code below to continue.");
        SCENE_TEXT.put(UserCodeTypeEnum.UPD_PASSWORD,
                "You are updating your xagent password. Use the verification code below to confirm the change.");
        SCENE_TEXT.put(UserCodeTypeEnum.BIND,
                "You are binding this email address to your xagent account. Use the verification code below to complete the binding.");
        SCENE_TEXT.put(UserCodeTypeEnum.WITHDRAW,
                "You are confirming a withdrawal from your xagent account. Use the verification code below to authorize this operation.");
    }

    public static boolean checkEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        try {
            String regex = "^([a-z0-9A-Z]+[-|_|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(email);
            return matcher.matches();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送邮箱验证码。type 为 {@link UserCodeTypeEnum} 名。
     * 调用 apihz mailyzm1，并在 Redis 写入 type 上下文，校验时核对 type 防跨场景。
     */
    public static void formMail(String email, String type) {
        UserCodeTypeEnum codeType;
        try {
            codeType = UserCodeTypeEnum.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown email code type: " + type, ex);
        }
        String mail = email == null ? "" : email.trim();
        if (!checkEmail(mail)) {
            throw new IllegalArgumentException("Invalid email: " + mail);
        }

        EmailSetting setting = loadSetting();
        String senderName = StringUtils.isNotBlank(setting.getSenderName())
                ? setting.getSenderName().trim()
                : "xagent";

        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", setting.getApiId());
        params.put("key", setting.getApiKey());
        params.put("name", senderName);
        params.put("tomail", mail);
        params.put("title", senderName + " verification code");
        params.put("headtitle", senderName);
        params.put("text", SCENE_TEXT.getOrDefault(codeType, SCENE_TEXT.get(UserCodeTypeEnum.LOGIN)));
        params.put("foot1", "This is an automated message from " + senderName + ".");
        params.put("foot2", "If you did not request this code, please ignore this email.");
        params.put("foot3", "For your security, never share this code with anyone.");
        params.put("foot4", "Thank you for using " + senderName + ".");

        String resp = HttpUtils.sendGet(SEND_URL, encodeForm(params));
        JSONObject json = parseJson(resp);
        Integer code = json == null ? null : json.getInteger("code");
        if (code == null || code != 200) {
            String msg = json == null ? resp : json.getString("msg");
            log.error("apihz mailyzm1 send failed, type={}, email={}, resp={}", codeType, mailMask(mail), resp);
            throw new RuntimeException("Send email code failed: " + msg);
        }
        log.info("apihz mailyzm1 send ok, type={}, email={}", codeType, mailMask(mail));

        RedisCache redisCache = SpringContextUtil.getBean(RedisCache.class);
        redisCache.setCacheObject(ctxKey(mail), codeType.name(), CODE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 校验邮箱验证码。先核对 Redis type 上下文，再调 apihz mailyzm2。
     * 通过后清掉本地上下文标记（防本地重放）。apihz 那边每个码也只能用一次。
     */
    public static boolean verifyCode(String email, UserCodeTypeEnum codeType, String code) {
        if (codeType == null || StringUtils.isBlank(email) || StringUtils.isBlank(code)) {
            return false;
        }
        String mail = email.trim();

        RedisCache redisCache = SpringContextUtil.getBean(RedisCache.class);
        String ctx = redisCache.getCacheObject(ctxKey(mail));
        if (ctx == null || !ctx.equals(codeType.name())) {
            log.debug("verify reject: ctx mismatch, type={}, email={}, ctx={}", codeType, mailMask(mail), ctx);
            return false;
        }

        EmailSetting setting = loadSetting();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", setting.getApiId());
        params.put("key", setting.getApiKey());
        params.put("mail", mail);
        params.put("code", code.trim());

        String resp = HttpUtils.sendGet(VERIFY_URL, encodeForm(params));
        JSONObject json = parseJson(resp);
        Integer respCode = json == null ? null : json.getInteger("code");
        if (respCode == null || respCode != 200) {
            log.info("apihz mailyzm2 verify failed, type={}, email={}, resp={}", codeType, mailMask(mail), resp);
            return false;
        }
        redisCache.deleteObject(ctxKey(mail));
        log.info("apihz mailyzm2 verify ok, type={}, email={}", codeType, mailMask(mail));
        return true;
    }

    private static EmailSetting loadSetting() {
        SettingService settingService = SpringContextUtil.getBean(SettingService.class);
        Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
        if (setting == null || StringUtils.isBlank(setting.getSettingValue())) {
            throw new IllegalStateException("EMAIL_SETTING is empty");
        }
        EmailSetting emailSetting = JSONUtil.toBean(setting.getSettingValue(), EmailSetting.class);
        if (emailSetting == null
                || StringUtils.isBlank(emailSetting.getApiId())
                || StringUtils.isBlank(emailSetting.getApiKey())) {
            throw new IllegalStateException("EMAIL_SETTING missing apiId/apiKey");
        }
        return emailSetting;
    }

    private static String ctxKey(String email) {
        return CTX_KEY_PREFIX + email;
    }

    private static String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static JSONObject parseJson(String resp) {
        if (StringUtils.isBlank(resp)) return null;
        try {
            return JSON.parseObject(resp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String mailMask(String email) {
        if (StringUtils.isBlank(email)) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }
}
