package com.ruoyi.bussiness.service.impl;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import com.ruoyi.bussiness.domain.*;
import com.ruoyi.bussiness.domain.setting.AddMosaicSetting;
import com.ruoyi.bussiness.domain.setting.Setting;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TAppUserMapper;
import com.ruoyi.bussiness.mapper.TSecondCoinConfigMapper;
import com.ruoyi.bussiness.mapper.TSecondContractOrderMapper;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.CachePrefix;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.CommonEnum;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.enums.SettingEnum;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.RedisUtil;
import com.ruoyi.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;


/**
 * 秒合约订单Service业务层处理
 * 
 * @author ruoyi
 * @date 2023-07-13
 */
@Service
@Slf4j
public class TSecondContractOrderServiceImpl extends ServiceImpl<TSecondContractOrderMapper, TSecondContractOrder> implements ITSecondContractOrderService
{
    @Resource
    private TSecondContractOrderMapper tSecondContractOrderMapper;
    @Resource
    private TSecondCoinConfigMapper tSecondCoinConfigMapper;
    @Resource
    private ITSecondPeriodConfigService itSecondPeriodConfigService;
    @Resource
    private TAppUserMapper appUserMapper;
    @Resource
    private ITAppAssetService assetService;
    @Resource
    private ITAppWalletRecordService appWalletRecordService;
    @Resource
    private RedisCache redisCache;
    @Resource
    private SettingService settingService;
    @Resource
    private ITAppUserDetailService appUserDetailService;
    @Resource
    private RedisUtil redisUtil;
    @Value("${api-redis-stream.names:}")
    private String redisStreamNames;


    /**
     * 查询秒合约订单
     * 
     * @param id 秒合约订单主键
     * @return 秒合约订单
     */
    @Override
    public TSecondContractOrder selectTSecondContractOrderById(Long id)
    {
        return tSecondContractOrderMapper.selectTSecondContractOrderById(id);
    }

    /**
     * 查询秒合约订单列表
     * 
     * @param tSecondContractOrder 秒合约订单
     * @return 秒合约订单
     */
    @Override
    public List<TSecondContractOrder> selectTSecondContractOrderList(TSecondContractOrder tSecondContractOrder)
    {
        List<TSecondContractOrder> tSecondContractOrders = tSecondContractOrderMapper.selectTSecondContractOrderList(tSecondContractOrder);
        for (TSecondContractOrder secondContractOrder : tSecondContractOrders) {
            if(Objects.equals(CommonEnum.ZERO.getCode(), secondContractOrder.getStatus())){
                long time = (secondContractOrder.getCloseTime() - new Date().getTime()) / 1000;
                secondContractOrder.setTime((int) time);
            }else {
                secondContractOrder.setTime(0);
            }
        }
        return tSecondContractOrders;
    }

    /**
     * 新增秒合约订单
     * 
     * @param tSecondContractOrder 秒合约订单
     * @return 结果
     */
    @Override
    public int insertTSecondContractOrder(TSecondContractOrder tSecondContractOrder)
    {
        tSecondContractOrder.setCreateTime(DateUtils.getNowDate());
        return tSecondContractOrderMapper.insertTSecondContractOrder(tSecondContractOrder);
    }

    /**
     * 修改秒合约订单
     * 
     * @param tSecondContractOrder 秒合约订单
     * @return 结果
     */
    @Override
    public int updateTSecondContractOrder(TSecondContractOrder tSecondContractOrder)
    {
        return tSecondContractOrderMapper.updateTSecondContractOrder(tSecondContractOrder);
    }

    /**
     * 批量删除秒合约订单
     * 
     * @param ids 需要删除的秒合约订单主键
     * @return 结果
     */
    @Override
    public int deleteTSecondContractOrderByIds(Long[] ids)
    {
        return tSecondContractOrderMapper.deleteTSecondContractOrderByIds(ids);
    }

    /**
     * 删除秒合约订单信息
     * 
     * @param id 秒合约订单主键
     * @return 结果
     */
    @Override
    public int deleteTSecondContractOrderById(Long id)
    {
        return tSecondContractOrderMapper.deleteTSecondContractOrderById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleExpiredOrders(Long userId) {
        LambdaQueryWrapper<TSecondContractOrder> wrapper = new LambdaQueryWrapper<TSecondContractOrder>()
                .eq(TSecondContractOrder::getStatus, CommonEnum.ZERO.getCode())
                .le(TSecondContractOrder::getCloseTime, new Date().getTime());
        if (Objects.nonNull(userId)) {
            wrapper.eq(TSecondContractOrder::getUserId, userId);
        }
        List<TSecondContractOrder> list = this.list(wrapper);
        for (TSecondContractOrder order : list) {
            String lockKey = CachePrefix.ORDER_SECOND_CONTRACT.getPrefix() + "settle:" + order.getId();
            if (!redisCache.tryLock(lockKey, order.getId(), 30000)) {
                continue;
            }
            TSecondContractOrder latestOrder = this.getById(order.getId());
            if (Objects.isNull(latestOrder) || !CommonEnum.ZERO.getCode().equals(latestOrder.getStatus())) {
                continue;
            }
            TAppUser user = appUserMapper.selectTAppUserByUserId(latestOrder.getUserId());
            if (Objects.nonNull(user)) {
                settleSecondContractOrder(latestOrder, user);
            }
        }
    }

    @Override
    public String createSecondContractOrder(TSecondContractOrder order) {
        log.info("下单"+ JSONObject.toJSONString(order));
        String validateResult = validateSecondContractOrder(order);
        if (!"success".equals(validateResult)) {
            return validateResult;
        }
        try{
            String serialId = "R" + OrderUtils.generateOrderNum();
            long loginIdAsLong = StpUtil.getLoginIdAsLong();
            BigDecimal amount = order.getBetAmount();
            TAppUser user = appUserMapper.selectTAppUserByUserId(loginIdAsLong);
            if (Objects.isNull(user)) {
                return MessageUtils.message("user.notfound");
            }
            TSecondPeriodConfig secondPeriodConfig = itSecondPeriodConfigService.getById(order.getPeriodId());
            if (Objects.isNull(secondPeriodConfig)) {
                return "No contract period available";
            }
            //判断金额上下限
            if((Objects.nonNull(secondPeriodConfig.getMaxAmount()) && secondPeriodConfig.getMaxAmount().compareTo(amount)<0)
                    || (Objects.nonNull(secondPeriodConfig.getMinAmount()) && secondPeriodConfig.getMinAmount().compareTo(amount) > 0)){
                return MessageUtils.message("order_amount_limit");
            }
            //1. 时间判断， 不能太频繁

            // 秒合约使用合约资产，与U本位共用合约账户
            TAppAsset asset = assetService.getOne(new LambdaQueryWrapper<TAppAsset>()
                    .eq(TAppAsset::getUserId, user.getUserId())
                    .eq(TAppAsset::getSymbol, order.getBaseSymbol().toLowerCase())
                    .eq(TAppAsset::getType, AssetEnum.CONTRACT_ASSETS.getCode()));
            if (Objects.isNull(asset) || asset.getAvailableAmount().compareTo(amount) < 0) {
                return MessageUtils.message("order_amount_error");
            }
            //根据ID 查看时间 周期

            //下单
            Date date = new Date();
            order.setRate(secondPeriodConfig.getOdds());
            order.setType(secondPeriodConfig.getPeriod().intValue());
            order.setRateFlag(secondPeriodConfig.getFlag());
            order.setUserId(user.getUserId());
            order.setOrderNo(serialId);
            order.setCreateTime(date);
            order.setCloseTime(date.getTime()+(order.getType()-2)* 1000L);//60秒后的时间)
            order.setUserAddress(user.getAddress());
            order.setStatus(0);
            order.setBetAmount(order.getBetAmount());
            order.setRewardAmount(BigDecimal.ZERO);
            //当前币种最新价格
            BigDecimal price = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + order.getCoinSymbol().toLowerCase());
            TSecondCoinConfig one = tSecondCoinConfigMapper.selectOne(new LambdaQueryWrapper<TSecondCoinConfig>().eq(TSecondCoinConfig::getCoin, order.getCoinSymbol().toUpperCase()));
            if (Objects.nonNull(one) && !Integer.valueOf(2).equals(one.getType())) {
                price=redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + order.getCoinSymbol().toUpperCase());
            }
            if (Objects.isNull(price) || price.compareTo(BigDecimal.ZERO) <= 0) {
                return "Price is not ready";
            }
            order.setOpenPrice(price);
            order.setSign(0);
            order.setManualIntervention(1);
            order.setAdminParentIds(user.getAdminParentIds());
            //先扣钱，再下单
            if (redisCache.tryLock(CachePrefix.USER_WALLET.getPrefix() + user.getUserId(), user.getUserId(), 1000)) {
                //成功的情况下才进行加锁限制
                if (!redisCache.hasKey(CachePrefix.ORDER_SECOND_CONTRACT.getPrefix() + user.getUserId())) {
                    redisCache.setCacheObject(CachePrefix.ORDER_SECOND_CONTRACT.getPrefix() + user.getUserId(), serialId, 10000, TimeUnit.MILLISECONDS);
                } else {
                    return MessageUtils.message("order_10s_retry");
                }
                BigDecimal availableAmount = asset.getAvailableAmount();
                asset.setAmout(asset.getAmout().subtract(amount));
                asset.setAvailableAmount(availableAmount.subtract(amount));
                assetService.updateByUserId(asset);
                appWalletRecordService.generateRecord(user.getUserId(), amount, RecordEnum.OPTION_BETTING.getCode(), user.getLoginName(), serialId, RecordEnum.OPTION_BETTING.getInfo(), availableAmount, availableAmount.subtract(amount),order.getBaseSymbol(),user.getAdminParentIds());
                this.insertTSecondContractOrder(order);
                log.debug("下注提交成功, userId:{}, orderId:{}, money:{}", user.getUserId(), serialId, amount);

                //秒合约打码
                Setting setting = settingService.get(SettingEnum.ADD_MOSAIC_SETTING.name());
                if (Objects.nonNull(setting)){
                    AddMosaicSetting addMosaic = JSONUtil.toBean(setting.getSettingValue(), AddMosaicSetting.class);
                    if (Objects.nonNull(addMosaic) && Objects.nonNull(addMosaic.getIsOpen()) && addMosaic.getIsOpen() && Objects.nonNull(addMosaic.getSencordIsOpen()) && addMosaic.getSencordIsOpen()){
                        user.setTotleAmont(user.getTotleAmont().add(order.getBetAmount()));
                        appUserMapper.updateTotleAmont(user);
                    }
                }

                return String.valueOf(order.getId());
            }else {
                return MessageUtils.message("withdraw.refresh");
            }
        }catch (Exception e){
            log.error("create second contract order failed", e);
        }
        return  MessageUtils.message("withdraw.refresh");
    }

    private String validateSecondContractOrder(TSecondContractOrder order) {
        if (Objects.isNull(order)) {
            return "Order data is required";
        }
        if (StringUtils.isEmpty(order.getCoinSymbol()) || StringUtils.isEmpty(order.getBaseSymbol()) || StringUtils.isEmpty(order.getSymbol())) {
            return "Trading pair is not ready";
        }
        if (Objects.isNull(order.getPeriodId())) {
            return "No contract period available";
        }
        if (Objects.isNull(order.getBetAmount()) || order.getBetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Order amount is required";
        }
        return "success";
    }

    private void settleSecondContractOrder(TSecondContractOrder order, TAppUser user) {
        try {
            BigDecimal betAmount = order.getBetAmount();
            BigDecimal rate = Objects.isNull(order.getRate()) ? BigDecimal.ZERO : order.getRate();
            BigDecimal openPrice = order.getOpenPrice();
            Integer sign = Objects.isNull(order.getSign()) ? CommonEnum.ZERO.getCode() : order.getSign();
            Integer type = Integer.parseInt(order.getBetContent());
            BigDecimal closePrice = getSecondContractPrice(order.getCoinSymbol());
            if (Objects.isNull(closePrice) || closePrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("second contract close price is not ready, orderId:{}, coin:{}", order.getId(), order.getCoinSymbol());
                return;
            }

            TAppUserDetail userDetail = appUserDetailService.getOne(
                    new LambdaQueryWrapper<TAppUserDetail>().eq(TAppUserDetail::getUserId, user.getUserId()));
            int winNum = Objects.isNull(userDetail) || Objects.isNull(userDetail.getWinNum()) ? 0 : userDetail.getWinNum();
            int loseNum = Objects.isNull(userDetail) || Objects.isNull(userDetail.getLoseNum()) ? 0 : userDetail.getLoseNum();
            Integer buff = Objects.isNull(user.getBuff()) ? CommonEnum.ZERO.getCode() : user.getBuff();

            if (CommonEnum.ZERO.getCode().equals(buff)) {
                if (winNum > 0 && CommonEnum.ZERO.getCode().equals(sign)) {
                    userDetail.setWinNum(winNum - 1);
                    appUserDetailService.updateById(userDetail);
                    closePrice = getClosePrice(openPrice, closePrice, CommonEnum.TRUE.getCode(), type);
                    sign = CommonEnum.TRUE.getCode();
                }
                if (loseNum > 0 && CommonEnum.ZERO.getCode().equals(sign)) {
                    userDetail.setLoseNum(loseNum - 1);
                    appUserDetailService.updateById(userDetail);
                    closePrice = getClosePrice(openPrice, closePrice, 2, type);
                    sign = 2;
                }
            }
            if (CommonEnum.TRUE.getCode().equals(buff)) {
                sign = CommonEnum.TRUE.getCode();
                closePrice = getClosePrice(openPrice, closePrice, CommonEnum.TRUE.getCode(), type);
            }
            if (Integer.valueOf(2).equals(buff)) {
                sign = 2;
                closePrice = getClosePrice(openPrice, closePrice, 2, type);
            }

            String openResult = "1";
            BigDecimal returnAmount = BigDecimal.ZERO;
            if (CommonEnum.TRUE.getCode().equals(type)) {
                if (openPrice.compareTo(closePrice) > 0) {
                    openResult = "2";
                    if (rate.compareTo(BigDecimal.ONE) < 0) {
                        returnAmount = betAmount.multiply(BigDecimal.ONE.subtract(rate));
                    }
                    if (rate.compareTo(BigDecimal.ONE) >= 0) {
                        returnAmount = BigDecimal.ZERO;
                    }
                    if (Boolean.TRUE.equals(order.getRateFlag())) {
                        returnAmount = BigDecimal.ZERO;
                    }
                }
                if (openPrice.compareTo(closePrice) < 0) {
                    if (rate.compareTo(BigDecimal.ONE) < 0) {
                        returnAmount = betAmount.multiply(BigDecimal.ONE.add(rate));
                    }
                    if (rate.compareTo(BigDecimal.ONE) >= 0) {
                        returnAmount = betAmount.add(betAmount.multiply(rate));
                    }
                }
                if (openPrice.compareTo(closePrice) == 0) {
                    openResult = "3";
                    returnAmount = betAmount;
                }
            } else {
                if (openPrice.compareTo(closePrice) > 0) {
                    if (rate.compareTo(BigDecimal.ONE) < 0) {
                        returnAmount = betAmount.multiply(BigDecimal.ONE.add(rate));
                    }
                    if (rate.compareTo(BigDecimal.ONE) >= 0) {
                        returnAmount = betAmount.add(betAmount.multiply(rate));
                    }
                }
                if (openPrice.compareTo(closePrice) < 0) {
                    openResult = "2";
                    if (rate.compareTo(BigDecimal.ONE) < 0) {
                        returnAmount = betAmount.multiply(BigDecimal.ONE.subtract(rate));
                    }
                    if (rate.compareTo(BigDecimal.ONE) >= 0) {
                        returnAmount = BigDecimal.ZERO;
                    }
                    if (Boolean.TRUE.equals(order.getRateFlag())) {
                        returnAmount = BigDecimal.ZERO;
                    }
                }
                if (openPrice.compareTo(closePrice) == 0) {
                    openResult = "3";
                    returnAmount = betAmount;
                }
            }

            if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                TAppAsset appAsset = assetService.getOne(new LambdaQueryWrapper<TAppAsset>()
                        .eq(TAppAsset::getUserId, order.getUserId())
                        .eq(TAppAsset::getSymbol, order.getBaseSymbol().toLowerCase())
                        .eq(TAppAsset::getType, AssetEnum.CONTRACT_ASSETS.getCode()));
                if (Objects.isNull(appAsset)) {
                    assetService.createAsset(user, order.getBaseSymbol().toLowerCase(), AssetEnum.CONTRACT_ASSETS.getCode());
                    appAsset = assetService.getOne(new LambdaQueryWrapper<TAppAsset>()
                            .eq(TAppAsset::getUserId, order.getUserId())
                            .eq(TAppAsset::getSymbol, order.getBaseSymbol().toLowerCase())
                            .eq(TAppAsset::getType, AssetEnum.CONTRACT_ASSETS.getCode()));
                }
                BigDecimal availableAmount = appAsset.getAvailableAmount();
                appAsset.setAmout(appAsset.getAmout().add(returnAmount));
                appAsset.setAvailableAmount(availableAmount.add(returnAmount));
                assetService.updateTAppAsset(appAsset);
                appWalletRecordService.generateRecord(order.getUserId(), returnAmount, RecordEnum.OPTION_SETTLEMENT.getCode(), "", order.getOrderNo(), RecordEnum.OPTION_SETTLEMENT.getInfo(), availableAmount, availableAmount.add(returnAmount), order.getBaseSymbol(), user.getAdminParentIds());
            }

            order.setOpenResult(openResult);
            order.setClosePrice(closePrice);
            order.setStatus(CommonEnum.TRUE.getCode());
            order.setRewardAmount(returnAmount);
            order.setSign(sign);
            this.updateById(order);
            if (StringUtils.isNotEmpty(redisStreamNames)) {
                HashMap<String, Object> object = new HashMap<>();
                object.put("settlement", "3");
                redisUtil.addStream(redisStreamNames, object);
            }
        } catch (Exception e) {
            log.error("settle second contract order failed, orderId:{}", order.getId(), e);
        }
    }

    private BigDecimal getSecondContractPrice(String coinSymbol) {
        if (StringUtils.isEmpty(coinSymbol)) {
            return null;
        }
        BigDecimal price = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + coinSymbol.toLowerCase());
        TSecondCoinConfig config = tSecondCoinConfigMapper.selectOne(
                new LambdaQueryWrapper<TSecondCoinConfig>().eq(TSecondCoinConfig::getCoin, coinSymbol.toUpperCase()));
        if (Objects.nonNull(config) && !Integer.valueOf(2).equals(config.getType())) {
            BigDecimal upperPrice = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + coinSymbol.toUpperCase());
            if (Objects.nonNull(upperPrice)) {
                price = upperPrice;
            }
        }
        if (Objects.isNull(price)) {
            price = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + coinSymbol.toUpperCase());
        }
        return price;
    }

    private static BigDecimal getClosePrice(BigDecimal openPrice, BigDecimal closePrice, Integer sign, Integer type) {
        if (sign == 1) {
            if ((1 == type && closePrice.compareTo(openPrice) > 0) || (0 == type && closePrice.compareTo(openPrice) < 0)) {
                return closePrice;
            }
        }
        if (sign == 2) {
            if ((1 == type && closePrice.compareTo(openPrice) < 0) || (0 == type && closePrice.compareTo(openPrice) > 0)) {
                return closePrice;
            }
        }

        BigDecimal diff;
        int digits = getNumberDecimalDigits(openPrice.stripTrailingZeros().toPlainString());
        if (digits == 0) {
            diff = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble());
        } else {
            diff = BigDecimal.valueOf((double) 1 / Math.pow(10, digits) * (ThreadLocalRandom.current().nextInt(10) + 1));
        }

        if (sign == 1) {
            if (1 == type) {
                closePrice = openPrice.add(diff);
            } else if (0 == type) {
                closePrice = openPrice.subtract(diff);
            }
        } else {
            if (1 == type) {
                closePrice = openPrice.subtract(diff);
            } else if (0 == type) {
                closePrice = openPrice.add(diff);
            }
        }
        return closePrice;
    }

    private static int getNumberDecimalDigits(String number) {
        if (!number.contains(".")) {
            return 0;
        }
        return number.length() - (number.indexOf(".") + 1);
    }
}
