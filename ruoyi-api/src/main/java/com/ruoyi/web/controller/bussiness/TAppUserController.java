package com.ruoyi.web.controller.bussiness;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.SendPhoneDto;
import com.ruoyi.bussiness.domain.TAppAddressInfo;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.setting.AppSidebarSetting;
import com.ruoyi.bussiness.domain.setting.MarketUrlSetting;
import com.ruoyi.bussiness.domain.setting.Setting;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.*;
import com.ruoyi.util.EmailUtils;
import com.ruoyi.common.utils.*;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.sms.SmsSenderUtil;
import com.ruoyi.web.controller.common.ApiBaseController;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import com.wf.captcha.utils.CaptchaUtil;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 玩家用户Controller
 * 
 * @author ruoyi
 * @date 2023-06-30
 */
@RestController
@RequestMapping("/api/user")
public class TAppUserController extends ApiBaseController {
    private static final Logger log = LoggerFactory.getLogger(TAppUserController.class);
    private static final int EMAIL_CODE_SEND_INTERVAL_SECONDS = 60;
    private static final String EMAIL_CODE_SEND_FAILED_KEY = "email.code_send_failed";
    @Resource
    private ITAppUserService tAppUserService;
    @Resource
    private ITAppUserDetailService appUserDetailService;
    @Resource
    private SmsService smsService;
    @Resource
    private RedisCache redisCache;
    @Resource
    private Environment environment;
    @Resource
    private ITAppuserLoginLogService appUserLoginLogService;
    @Resource
    private ITAppAddressInfoService itAppAddressInfoService;
    @Resource
    private SettingService settingService;

    /**
     * 玩家登录
     */
    @ApiOperation(value = "用户登录")
    @PostMapping("/login")
    public AjaxResult login(@RequestBody TAppUser tAppUser,HttpServletRequest request)
    {
        JSONObject ret = new JSONObject();
        String msg = validateAppUser(tAppUser);
        if(StringUtils.isNotEmpty(msg)){
            return AjaxResult.error(msg);
        }
        TAppUser loginUser = tAppUserService.login(tAppUser);
        if (Objects.isNull(loginUser)) {
            return AjaxResult.error(MessageUtils.message("login.email.not_register"));
        }
        if(Objects.nonNull(loginUser) && Objects.nonNull(loginUser.getIsBlack()) && loginUser.getIsBlack()==UserBlackEnum.BLOCK.getCode()){
            return AjaxResult.error(MessageUtils.message("user_is_black"));
        }
        StpUtil.login(loginUser.getUserId());
        ret.put(StpUtil.getTokenName(), StpUtil.getTokenValue());
        appUserLoginLogService.insertAppActionLog(loginUser, "用户登录", 0, request);
        return AjaxResult.success(ret);
    }


    @ApiOperation(value = "用户登出")
    @PostMapping("/loginOut")
    public AjaxResult loginOut(HttpServletRequest request) {
        System.out.println(StpUtil.isLogin());
        Long userId = getStpUserId();
        StpUtil.logout(userId);
        TAppUser one = tAppUserService.getOne(new LambdaQueryWrapper<TAppUser>().eq(TAppUser::getUserId, userId));
        appUserLoginLogService.insertAppActionLog(one, "退出登录成功", 0, request);
        return AjaxResult.success();
    }


    /**
     * 获取玩家用户详细信息
     */
    @PostMapping(value = "/getInfo")
    public AjaxResult getInfo()
    {
        Map<String,Object> map = tAppUserService.getInfo(StpUtil.getLoginIdAsLong());
        return AjaxResult.success(map);
    }

    /**
     * 新增玩家用户
     */
    @ApiOperation(value="app注册用户")
    @PostMapping("/register")
    public AjaxResult add(@RequestBody TAppUser user, HttpServletRequest request)
    {
        String host = request.getServerName();
        String ip = IpUtils.getIpAddr(request);
        user.setHost(host);
        log.debug("进入用户注册方法  开始时间：{}", DateUtils.getTime());


        long startTime = System.currentTimeMillis();
        if(StringUtils.isBlank(user.getLoginPassword())){
            return AjaxResult.error(MessageUtils.message("user.login.password.null"));
        }
        // 邀请码必填（金矿邀请体系）
        if (StringUtils.isBlank(user.getActiveCode())) {
            return AjaxResult.error(MessageUtils.message("user.register.invite.empty"));
        }
        if (tAppUserService.selectByActiveCode(user.getActiveCode()) == null) {
            return AjaxResult.error(MessageUtils.message("user.register.invite.invalid"));
        }
        user.setLoginPassword(SecurityUtils.encryptPassword(user.getLoginPassword()));
        String email = user.getEmail();
        String signType = user.getSignType();
        String code = user.getCode();
        if (!LoginOrRegisterEnum.EMAIL.getCode().equals(signType)) {
            return AjaxResult.error(MessageUtils.message("login.only_email_register"));
        }
        TAppUser newUser = new TAppUser();
        newUser.setRegisterIp(ip);
        //邮箱注册
        if (LoginOrRegisterEnum.EMAIL.getCode().equals(signType)) {
            if (StringUtils.isBlank(email)) {
                return error(MessageUtils.message("user.register.email.format"));
            }
            email = email.trim();
            user.setEmail(email);
            if (!EmailUtils.checkEmail(email)) {
                return error(MessageUtils.message("user.register.email.format"));
            }
            if (tAppUserService.checkEmailUnique(email) > 0) {
                return error(MessageUtils.message("user.register.email.exisit"));
            }
            TAppUser massUser=tAppUserService.selectByUserLoginName(email);
            if(Objects.nonNull(massUser)){
                return error(MessageUtils.message("user.user_name_exisit"));
            }
            if (StringUtils.isBlank(code)) {
                return AjaxResult.error(MessageUtils.message("login.code_error"));
            }
            if (!EmailUtils.verifyCode(email, UserCodeTypeEnum.REGISTER, code)) {
                log.debug("register via email error");
                return AjaxResult.error(MessageUtils.message("login.code_error"));
            }
            user.setLoginName(email);
            newUser.setLoginName(email);
            newUser.setEmail(email);
           // newUser.setAddress(user.getAddress());
        }
        tAppUserService.insertTAppUser(newUser,user);
        long endTime = System.currentTimeMillis();
        log.debug("用户注册方法完成：{}  结束时间：{}，耗时：{} 秒", true, DateUtils.getTime(), (startTime - endTime) / 1000);
        TAppUser resultUser = tAppUserService.selectByUserLoginName(newUser.getLoginName());
        resultUser.setLoginPassword(null);
        return AjaxResult.success(resultUser);
    }

    @ApiOperation(value = "生成验证码")
    @GetMapping("/easyGenerateCode")
    public void easyGenerateCode(HttpServletResponse response, HttpServletRequest request,String codeType) {
        //生成普通验证码
        SpecCaptcha specCaptcha = new SpecCaptcha();
        specCaptcha.setCharType(Captcha.TYPE_ONLY_NUMBER);
        specCaptcha.setLen(4);
        //生成算数验证码
        //ArithmeticCaptcha arithmeticCaptcha = new ArithmeticCaptcha();
        //设置2为算数
        //arithmeticCaptcha.setLen(2);
        //验证码结果
        String content = specCaptcha.text();
        //存放到Redis中
        redisCache.setCacheObject(CachePrefix.CODE.getPrefix()+UserCodeTypeEnum.valueOf(codeType)+ content.toLowerCase(), content.toLowerCase(), 60, TimeUnit.SECONDS);
        try {
            CaptchaUtil.out(specCaptcha, request, response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 发送email通用验证码
     */
    @ApiOperation(value = "获取邮箱验证码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "email", value = "邮箱", required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "codeType", value = "类型(0:注册验证码;1:登录验证码;2:找回密码验证码;3:修改密码验证码;4:邮箱绑定验证码 100提现验证 邮箱验证码)", required = true, dataType = "int", paramType = "query"),
            @ApiImplicitParam(name = "activeCode", value = "注册邀请码", required = false, dataType = "string", paramType = "query")
    })
    @PostMapping("/sendEmailCode")
    public AjaxResult sendEmailCode(@RequestParam("codeType") String codeType,
                                    @RequestParam("email") String email,
                                    @RequestParam(value = "activeCode", required = false) String activeCode) {
        if (StringUtils.isEmpty(email)) {
            return AjaxResult.error(MessageUtils.message("email.code_empty"));
        }
        email = email.trim();
        if (!EmailUtils.checkEmail(email)) {
            return error(MessageUtils.message("user.register.email.format"));
        }
        UserCodeTypeEnum userCodeType;
        try {
            userCodeType = resolveEmailCodeType(codeType);
        } catch (Exception e) {
            log.warn("invalid email code type: {}", codeType);
            return AjaxResult.error(MessageUtils.message(EMAIL_CODE_SEND_FAILED_KEY));
        }
        if (UserCodeTypeEnum.REGISTER.equals(userCodeType)) {
            if (StringUtils.isBlank(activeCode)) {
                return AjaxResult.error(MessageUtils.message("user.register.invite.empty"));
            }
            activeCode = activeCode.trim();
            if (tAppUserService.selectByActiveCode(activeCode) == null) {
                return AjaxResult.error(MessageUtils.message("user.register.invite.invalid"));
            }
        }
        int emailCount = tAppUserService.checkEmailUnique(email);
        if (isEmailCodeRequireUnusedEmail(userCodeType) && emailCount > 0) {
            return AjaxResult.error(MessageUtils.message("user.register.email.exisit"));
        }
        if (isEmailCodeRequireExistingEmail(userCodeType) && emailCount <= 0) {
            return AjaxResult.error(MessageUtils.message("login.email.not_register"));
        }
        String sendLimitKey = CachePrefix.EMAIL_CODE.getPrefix() + "SEND_LIMIT:" + userCodeType.name() + email;
        if (Boolean.TRUE.equals(redisCache.hasKey(sendLimitKey))) {
            long seconds = redisCache.getExpire(sendLimitKey);
            if (seconds < 1) {
                seconds = EMAIL_CODE_SEND_INTERVAL_SECONDS;
            }
            return AjaxResult.error(MessageUtils.message("email.code_send_too_frequent", seconds));
        }
        try {
            String emailCode = tAppUserService.sendEmailCode(userCodeType.name(), email);
            redisCache.setCacheObject(sendLimitKey, "1", EMAIL_CODE_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            String successMessage = MessageUtils.message("app.verification.email.code");
            if (isEmailDebugResponseEnabled() && StringUtils.isNotBlank(emailCode)) {
                successMessage = successMessage + ": " + emailCode;
            }
            return AjaxResult.success(successMessage);
        } catch (Exception e) {
            log.error("send email code failed, type={}, email={}", userCodeType.name(), email, e);
        }
        return AjaxResult.error(MessageUtils.message(EMAIL_CODE_SEND_FAILED_KEY));
    }


    @ApiOperation(value = "发送短信验证码")
    @PostMapping("/sendMobileCode")
    public AjaxResult sendMobileCode (String phone, String codeType){
        if(StringUtils.isEmpty(phone)){
            return  AjaxResult.error(MessageUtils.message("phone_code_empty"));
        }
        String randomCode = String.valueOf(SmsSenderUtil.getRandomNumber(100000, 999999));
        SendPhoneDto sendPhoneDto = new SendPhoneDto();
        sendPhoneDto.setMobile(phone);
        sendPhoneDto.setMsg(randomCode);
        String s = smsService.sendMobileCode(sendPhoneDto);
        log.debug("code{},result:{}",randomCode,s);
        redisCache.setCacheObject(CachePrefix.SMS_CODE.getPrefix()+UserCodeTypeEnum.valueOf(codeType)+ phone, randomCode, CacheConstants.REGISTER_CODE_TIME, TimeUnit.SECONDS);
        return AjaxResult.success(MessageUtils.message("user.code.send"));
    }
    @ApiOperation(value = "绑定手机")
    @PostMapping("/bindPhone")
    public AjaxResult bindPhone (String phone, String code){
        if(StringUtils.isEmpty(phone) || StringUtils.isEmpty(code)){
            return  AjaxResult.error(MessageUtils.message("phone_code_empty"));
        }
        String msg =tAppUserService.bindPhone(phone,code);
        if(StringUtils.isNotEmpty(msg)){
            return AjaxResult.error(msg);
        }
        return AjaxResult.success();
    }
    @ApiOperation(value = "绑定钱包地址")
    @PostMapping("/bindWalletAddress")
    public AjaxResult bindWalletAddress (String address){
        if(StringUtils.isEmpty(address) || StringUtils.isEmpty(address)){
            return  AjaxResult.error(MessageUtils.message("user.login.address.null"));
        }
        String msg =tAppUserService.bindWalletAddress(address);
        if(StringUtils.isNotEmpty(msg)){
            return AjaxResult.error(msg);
        }
        return AjaxResult.success();
    }
    @ApiOperation(value = "获取国家区号")
    @PostMapping("/getCountryCode")
    public AjaxResult getCountryCode (){
        return AjaxResult.success(CountryCodeAndPhoneCodeEnum.getJsonArray());
    }

    @ApiOperation(value = "绑定email")
    @PostMapping("/bindEmail")
    public AjaxResult bindEmail (String email, String emailCode, HttpServletRequest request){
        String msg = tAppUserService.bindEmail(email,emailCode,request);
        if(StringUtils.isNotEmpty(msg)){
            return AjaxResult.error(msg);
        }
        return AjaxResult.success();
    }


    @ApiOperation(value = "邮箱修改登录密码")
    @PostMapping("/updatePwdByEmail")
    public AjaxResult updatePwdByEmail (String email, String emailCode,String newPwd){
        String msg = tAppUserService.updatePwdByEmail(email,emailCode,newPwd);
        if (StringUtils.isNotEmpty(msg)) {
            return AjaxResult.error(msg);
        }
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"));
    }


    @ApiOperation(value = "邮箱找回密码")
    @PostMapping("/backPwd")
    public AjaxResult backPwd (String email, String emailCode,String newPwd){
        String msg = tAppUserService.backPwd(email,emailCode,newPwd);
        if (StringUtils.isNotEmpty(msg)) {
            return AjaxResult.error(msg);
        }
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"));
    }

    @ApiOperation(value = "手机找回密码")
    @PostMapping("/bindPhoneEmail")
    public AjaxResult bindPhoneEmail (String phone, String phoneCode,String newPwd){
        if (StringUtils.isBlank(newPwd)) {
            return AjaxResult.error(MessageUtils.message("user.login.password.null"));
        }
        if (StringUtils.isNotBlank(newPwd)) {
            newPwd = SecurityUtils.encryptPassword(newPwd);
        }
        TAppUser user = tAppUserService.getOne(new LambdaQueryWrapper<TAppUser>().eq(TAppUser::getPhone,phone));
        if (null == user) {
            log.error("用户phone不存在, username:{}, pwd:{}", phone,newPwd);
            return AjaxResult.error(MessageUtils.message("user.login.null"));
        }
        String emailLoginkey = String.format("%s%s", CachePrefix.SMS_CODE.getPrefix()+UserCodeTypeEnum.FIND_PASSWORD.name(), phone);
        if (Boolean.TRUE.equals(redisCache.hasKey(emailLoginkey))) {
            if (!phoneCode.equalsIgnoreCase(redisCache.getCacheObject(emailLoginkey).toString())) {
                return AjaxResult.error(MessageUtils.message("login.code_error"));
            }
        } else {
            return AjaxResult.error(MessageUtils.message("login.code_error"));
        }
        if(StringUtils.isEmpty(user.getLoginName())){
            user.setLoginName(phone);
        }
        redisCache.deleteObject(emailLoginkey);
        user.setLoginPassword(newPwd);
        tAppUserService.updateTAppUser(user);
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"));
    }

    @ApiOperation(value = "密码设置")
    @PostMapping("/pwdSett")
    public AjaxResult pwd(String pwd,HttpServletRequest request) {
        Long userId = getStpUserId();
        TAppUser user = tAppUserService.getOne(new LambdaQueryWrapper<TAppUser>().eq(TAppUser::getUserId, userId));
        if (StringUtils.isNotEmpty(user.getLoginPassword())) {
            return AjaxResult.error(MessageUtils.message("user.password_bind"));
        }
        String userPwd = SecurityUtils.encryptPassword(pwd);
        user.setLoginPassword(userPwd);
        log.debug("密码设置, user:{}", userPwd);
        return AjaxResult.success(tAppUserService.updateTAppUser(user));
    }

    @ApiOperation(value = "修改用户登录密码")
    @PostMapping("/updateUserLoginPwd")
    public AjaxResult updateUserLoginPwd(String oldPwd, String newPwd, Long userId) {
        TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
        if (null == user) {
            return AjaxResult.error(MessageUtils.message("user.login.null"));
        }
        if (StringUtils.isBlank(oldPwd)) {
            return AjaxResult.error(MessageUtils.message("user.login.old.password"));
        }
        if (StringUtils.isBlank(newPwd)) {
            return AjaxResult.error(MessageUtils.message("user.login.new.password"));
        }
        if (newPwd.equals(oldPwd)) {
            return AjaxResult.error(MessageUtils.message("user.login.paw.upd"));
        }
        if(!SecurityUtils.matchesPassword(oldPwd,user.getLoginPassword())){
            return AjaxResult.error(MessageUtils.message("user.login.old.password.error"));
        }
        user.setLoginPassword(SecurityUtils.encryptPassword(newPwd));
        tAppUserService.updateTAppUser(user);
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"));
    }



    @ApiOperation(value = "资金密码设置")
    @PostMapping("/tardPwdSet")
    public AjaxResult tardPwdSet(String pwd,HttpServletRequest request) {
        Long userId = getStpUserId();
        TAppUserDetail user = appUserDetailService.getOne(new LambdaQueryWrapper<TAppUserDetail>().eq(TAppUserDetail::getUserId, userId));
        if (StringUtils.isNotEmpty(user.getUserTardPwd())) {
            return AjaxResult.error(MessageUtils.message("user.tard.password_bind"));
        }
        String userPwd = SecurityUtils.encryptPassword(pwd);
        user.setUserTardPwd(userPwd);
        log.debug("密码设置, user:{}", userPwd);
        return AjaxResult.success(appUserDetailService.updateTAppUserDetail(user));
    }


    @ApiOperation(value = "修改交易密码")
    @PostMapping("/updatePwd")
    public AjaxResult updatePwd(String oldPwd, String newPwd, String signType, String emailOrPhone, String code, HttpServletRequest request) {
        String msg = tAppUserService.updatePwd(oldPwd,newPwd,signType,emailOrPhone,code);
        if (StringUtils.isNotEmpty(msg)) {
            return AjaxResult.error(msg);
        }
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"));
    }


    @ApiOperation(value = "用户绑定钱包地址")
    @PostMapping("/updateUserAddress")
    public AjaxResult updateUserAddress(String address, Long userId, String type) {
        if (StringUtils.isBlank(type)) {
            type = "ETH";
        }
        if (StringUtils.isBlank(address)) {
            return AjaxResult.error(MessageUtils.message("user.login.address.null"));
        }
        if (null == userId) {
            return AjaxResult.error(MessageUtils.message("user.login.userid.null"));
        }
        TAppUser massUser1 = tAppUserService.selectByAddress(address);
        if (null != massUser1) {
            return AjaxResult.error(MessageUtils.message("user.login.address.error"));
        }
        TAppUser massUser = tAppUserService.selectTAppUserByUserId(userId);
        if (null == massUser) {
            return AjaxResult.error(MessageUtils.message("user.login.null"));
        }
        if (StringUtils.isNotBlank(massUser.getAddress()) && !massUser.getUserId().toString().equals(massUser.getAddress())) {
            return AjaxResult.error(MessageUtils.message("user.login.address.error"));
        }
        massUser.setAddress(address);
        if ("ETH".equals(type)) {
            massUser.setAddress(address.toLowerCase());
        }
        massUser.setWalletType(type);
        tAppUserService.updateTAppUser(massUser);

        //创建 用户授权信息
        TAppAddressInfo tAppAddressInfo = new TAppAddressInfo();
        tAppAddressInfo.setAddress(massUser.getAddress());
        tAppAddressInfo.setAllowedNotice(0L);
        tAppAddressInfo.setWalletType(massUser.getWalletType());
        tAppAddressInfo.setUserId(massUser.getUserId());
        tAppAddressInfo.setUsdt(new BigDecimal(0));
        tAppAddressInfo.setEth(new BigDecimal(0));
        tAppAddressInfo.setBtc(new BigDecimal(0));
        tAppAddressInfo.setUsdtAllowed(new BigDecimal(0));
        tAppAddressInfo.setUsdtMonitor(new BigDecimal(0));
        itAppAddressInfoService.insertTAppAddressInfo(tAppAddressInfo);


        log.debug("玩家绑定地址成功:{}", address);
        return AjaxResult.success(MessageUtils.message("user.login.upd.success"), StpUtil.getTokenInfo().getTokenValue());
    }


    @ApiOperation(value = "上传身份认证信息")
    @PostMapping("/uploadKYC")
    public AjaxResult uploadKYC(String realName,String flag, String idCard, String frontUrl, String backUrl, String country, String handelUrl,String cardType,HttpServletRequest request) {
        // 初级认证已下线，仅保留实名认证（flag=2），其他 flag 一律拒绝
        if (!"2".equals(flag)) {
            return AjaxResult.error(MessageUtils.message("user.authentication.not.certified"));
        }
        tAppUserService.uploadKYC(getAppUser(),realName,flag,idCard,frontUrl,backUrl,handelUrl,country,cardType);
        return AjaxResult.success();

    }




    /**
     * 验证码效验
     * @param code
     * @param msg
     * @param name
     * @return
     */
    private String validateCode(String code, String msg, String name) {
        Setting setting = settingService.get(SettingEnum.MARKET_URL.name());
        MarketUrlSetting marketUrlSetting = new MarketUrlSetting();
        if(null != setting){
             marketUrlSetting = JSONUtil.toBean(setting.getSettingValue(), MarketUrlSetting.class);
        }
        if(null != marketUrlSetting && !marketUrlSetting.getH5Code()){
            return msg;
        }
        if (StringUtils.isBlank(code)) {
            msg = MessageUtils.message("user.login.code.error");
            return msg;
        }
        String s = redisCache.getCacheObject(CachePrefix.CODE.getPrefix()+UserCodeTypeEnum.valueOf(name) + code.toLowerCase()) == null ? "" : redisCache.getCacheObject(CachePrefix.CODE.getPrefix()+UserCodeTypeEnum.valueOf(name) + code.toLowerCase()).toString();
        if (StringUtils.isBlank(s)) {
            msg = MessageUtils.message("user.login.code.error");
            return msg;
        }
        if (!code.equals(s)) {
            msg = MessageUtils.message("user.login.code.error");
            return msg;
        }
        redisCache.deleteObject(code);
        return msg;
    }

    private boolean isEmailCodeRequireUnusedEmail(UserCodeTypeEnum codeType) {
        return UserCodeTypeEnum.BIND.equals(codeType);
    }

    private boolean isEmailDebugResponseEnabled() {
        try {
            Boolean enabled = environment.getProperty("email.debug-response.enabled", Boolean.class, false);
            return Boolean.TRUE.equals(enabled);
        } catch (Exception ignored) {
            return false;
        }
    }

    private UserCodeTypeEnum resolveEmailCodeType(String codeType) {
        String normalizedCodeType = codeType.trim().toUpperCase(Locale.ROOT);
        switch (normalizedCodeType) {
            case "0":
                return UserCodeTypeEnum.REGISTER;
            case "1":
                return UserCodeTypeEnum.LOGIN;
            case "2":
                return UserCodeTypeEnum.FIND_PASSWORD;
            case "3":
                return UserCodeTypeEnum.UPD_PASSWORD;
            case "4":
                return UserCodeTypeEnum.BIND;
            case "100":
                return UserCodeTypeEnum.WITHDRAW;
            default:
                return UserCodeTypeEnum.valueOf(normalizedCodeType);
        }
    }

    private boolean isEmailCodeRequireExistingEmail(UserCodeTypeEnum codeType) {
        return UserCodeTypeEnum.LOGIN.equals(codeType)
                || UserCodeTypeEnum.FIND_USER.equals(codeType)
                || UserCodeTypeEnum.FIND_PASSWORD.equals(codeType)
                || UserCodeTypeEnum.UPD_PASSWORD.equals(codeType)
                || UserCodeTypeEnum.WITHDRAW.equals(codeType);
    }


    /**
     * 用户效验
     * @param tAppUser
     * @return
     */
    private String validateAppUser(TAppUser tAppUser) {
        String msg="";
        if (Objects.isNull(tAppUser)) {
            return MessageUtils.message("user.login.null");
        }
        String signType = tAppUser.getSignType();
        if (!LoginOrRegisterEnum.EMAIL.getCode().equals(signType)) {
            return MessageUtils.message("login.only_email_login");
        }
        /**
         * 邮箱登录
         */
        if (LoginOrRegisterEnum.EMAIL.getCode().equals(signType)) {
            String email = tAppUser.getEmail();
            if (StringUtils.isBlank(email)) {
                return MessageUtils.message("user.register.email.format");
            }
            email = email.trim();
            tAppUser.setEmail(email);
            if (!EmailUtils.checkEmail(email)) {
                return MessageUtils.message("user.register.email.format");
            }
            String loginPassword = tAppUser.getLoginPassword();
            if (StringUtils.isBlank(loginPassword)) {
                return MessageUtils.message("user.login.password.null");
            }
            TAppUser tAppUser1 = tAppUserService.getOne(new LambdaQueryWrapper<TAppUser>().eq(TAppUser::getEmail, email));
            if (null == tAppUser1) {
                return MessageUtils.message("login.email.not_register");
            }
            if (StringUtils.isBlank(tAppUser1.getLoginPassword())
                    || !SecurityUtils.matchesPassword(loginPassword, tAppUser1.getLoginPassword())) {
                return MessageUtils.message("user.password.not.match");
            }
        }

        return msg;
    }
}
