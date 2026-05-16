package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TTradfiOrder;
import com.ruoyi.bussiness.domain.TTradfiSymbol;
import com.ruoyi.bussiness.mapper.TTradfiOrderMapper;
import com.ruoyi.bussiness.service.ITAppAssetService;
import com.ruoyi.bussiness.service.ITAppUserService;
import com.ruoyi.bussiness.service.ITAppWalletRecordService;
import com.ruoyi.bussiness.service.ITTradfiOrderService;
import com.ruoyi.bussiness.service.ITTradfiSymbolService;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.CachePrefix;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * TradFi 交易订单业务层
 */
@Service
public class TTradfiOrderServiceImpl extends ServiceImpl<TTradfiOrderMapper, TTradfiOrder>
        implements ITTradfiOrderService {

    private static final String SETTLE_COIN = "usdt";
    private static final BigDecimal FEE_RATE = BigDecimal.ZERO;

    @Resource
    private TTradfiOrderMapper tTradfiOrderMapper;
    @Resource
    private ITTradfiSymbolService tTradfiSymbolService;
    @Resource
    private ITAppAssetService tAppAssetService;
    @Resource
    private ITAppUserService tAppUserService;
    @Resource
    private ITAppWalletRecordService appWalletRecordService;
    @Resource
    private RedisCache redisCache;

    @Override
    public TTradfiOrder selectTTradfiOrderById(Long id) {
        return tTradfiOrderMapper.selectTTradfiOrderById(id);
    }

    @Override
    public List<TTradfiOrder> selectTTradfiOrderList(TTradfiOrder tTradfiOrder) {
        return tTradfiOrderMapper.selectTTradfiOrderList(tTradfiOrder);
    }

    @Override
    public int insertTTradfiOrder(TTradfiOrder tTradfiOrder) {
        tTradfiOrder.setCreateTime(DateUtils.getNowDate());
        return tTradfiOrderMapper.insertTTradfiOrder(tTradfiOrder);
    }

    @Override
    public int updateTTradfiOrder(TTradfiOrder tTradfiOrder) {
        tTradfiOrder.setUpdateTime(DateUtils.getNowDate());
        return tTradfiOrderMapper.updateTTradfiOrder(tTradfiOrder);
    }

    @Override
    public int deleteTTradfiOrderByIds(Long[] ids) {
        return tTradfiOrderMapper.deleteTTradfiOrderByIds(ids);
    }

    @Override
    public int deleteTTradfiOrderById(Long id) {
        return tTradfiOrderMapper.deleteTTradfiOrderById(id);
    }

    @Transactional
    @Override
    public String submitTradfiOrder(TAppUser user, TTradfiOrder order) {
        if (Objects.isNull(user)) {
            return MessageUtils.message("user.notfound");
        }
        String validateResult = validateTradfiOrder(order);
        if (!"success".equals(validateResult)) {
            return validateResult;
        }

        String symbol = order.getSymbol().toLowerCase();
        order.setSymbol(symbol);
        order.setCoin(SETTLE_COIN);

        TTradfiSymbol tradfiSymbol = tTradfiSymbolService.getOne(
                new LambdaQueryWrapper<TTradfiSymbol>()
                        .eq(TTradfiSymbol::getSymbol, symbol.toUpperCase())
                        .eq(TTradfiSymbol::getStatus, 1)
                        .eq(TTradfiSymbol::getShowFlag, 1));
        if (Objects.isNull(tradfiSymbol)) {
            return "TradFi symbol is not ready";
        }

        BigDecimal settlePrice = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + symbol);
        if (Objects.isNull(settlePrice) || settlePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "Price is not ready";
        }

        if (Objects.isNull(order.getDelegateValue())
                && Objects.nonNull(order.getDelegatePrice())
                && Objects.nonNull(order.getDelegateTotal())) {
            order.setDelegateValue(order.getDelegatePrice().multiply(order.getDelegateTotal()));
        }
        if (order.getType() == 0 && order.getDelegateType() == 1) {
            order.setDelegateTotal(order.getDelegateValue().divide(settlePrice, 6, RoundingMode.DOWN));
        }
        if (order.getType() == 1 && Objects.isNull(order.getDelegateValue())) {
            BigDecimal price = order.getDelegateType() == 0 ? order.getDelegatePrice() : settlePrice;
            order.setDelegateValue(price.multiply(order.getDelegateTotal()));
        }

        Long userId = user.getUserId();
        ensureAsset(user, symbol);
        ensureAsset(user, SETTLE_COIN);

        TAppAsset symbolAsset = getAsset(userId, symbol);
        TAppAsset usdtAsset = getAsset(userId, SETTLE_COIN);
        if (order.getType() == 0 && usdtAsset.getAvailableAmount().compareTo(order.getDelegateValue()) < 0) {
            return MessageUtils.message("currency.balance.deficiency", "USDT");
        }
        if (order.getType() == 1 && symbolAsset.getAvailableAmount().compareTo(order.getDelegateTotal()) < 0) {
            return MessageUtils.message("currency.balance.deficiency", symbol.toUpperCase());
        }

        order.setAdminParentIds(user.getAdminParentIds());
        order.setUserId(userId);
        order.setOrderNo("TF" + OrderUtils.generateOrderNum());
        order.setDealPrice(settlePrice);

        if (shouldDeal(order, settlePrice)) {
            dealOrder(user, order, settlePrice, symbolAsset, usdtAsset);
        } else {
            pendingOrder(order, userId);
        }
        return "success";
    }

    private String validateTradfiOrder(TTradfiOrder order) {
        if (Objects.isNull(order)) return "Order data is required";
        if (StringUtils.isEmpty(order.getSymbol())) return "Trading pair is not ready";
        if (Objects.isNull(order.getType()) || (order.getType() != 0 && order.getType() != 1)) {
            return "Order side is required";
        }
        if (Objects.isNull(order.getDelegateType()) || (order.getDelegateType() != 0 && order.getDelegateType() != 1)) {
            return "Order type is required";
        }
        if (order.getDelegateType() == 0 && isNonPositive(order.getDelegatePrice())) {
            return "Order price is required";
        }
        if (order.getType() == 0 && order.getDelegateType() == 1 && isNonPositive(order.getDelegateValue())) {
            return "Order amount is required";
        }
        if ((order.getType() == 1 || order.getDelegateType() == 0) && isNonPositive(order.getDelegateTotal())) {
            return "Order quantity is required";
        }
        return "success";
    }

    private boolean isNonPositive(BigDecimal value) {
        return Objects.isNull(value) || value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private boolean shouldDeal(TTradfiOrder order, BigDecimal settlePrice) {
        if (order.getDelegateType() == 1) return true;
        return order.getType() == 0
                ? order.getDelegatePrice().compareTo(settlePrice) >= 0
                : order.getDelegatePrice().compareTo(settlePrice) <= 0;
    }

    private void dealOrder(TAppUser user, TTradfiOrder order, BigDecimal settlePrice,
                           TAppAsset symbolAsset, TAppAsset usdtAsset) {
        BigDecimal dealNum = order.getType() == 0 && order.getDelegateType() == 1
                ? order.getDelegateValue().divide(settlePrice, 6, RoundingMode.DOWN)
                : order.getDelegateTotal();
        BigDecimal dealValue = settlePrice.multiply(dealNum).setScale(6, RoundingMode.DOWN);
        BigDecimal fee = order.getType() == 0 ? dealNum.multiply(FEE_RATE) : dealValue.multiply(FEE_RATE);

        order.setDelegatePrice(order.getDelegateType() == 1 ? settlePrice : order.getDelegatePrice());
        order.setDelegateTotal(dealNum);
        order.setDelegateValue(dealValue);
        order.setDealNum(dealNum);
        order.setDealValue(dealValue);
        order.setDealPrice(settlePrice);
        order.setFee(fee);
        order.setStatus(1);
        order.setDelegateTime(DateUtils.getNowDate());
        order.setDealTime(DateUtils.getNowDate());
        order.setCreateTime(DateUtils.getNowDate());
        order.setUpdateTime(DateUtils.getNowDate());
        tTradfiOrderMapper.insert(order);

        if (order.getType() == 0) {
            BigDecimal addAmount = dealNum.subtract(fee);
            tAppAssetService.reduceAssetByUserId(user.getUserId(), SETTLE_COIN, dealValue);
            tAppAssetService.addAssetByUserId(user.getUserId(), order.getSymbol(), addAmount);
            appWalletRecordService.generateRecord(user.getUserId(), dealValue,
                    RecordEnum.TRADFI_TRADINGSUB.getCode(), null, order.getOrderNo(), "TradFi交易-",
                    usdtAsset.getAvailableAmount(), usdtAsset.getAvailableAmount().subtract(dealValue),
                    SETTLE_COIN, user.getAdminParentIds());
            appWalletRecordService.generateRecord(user.getUserId(), addAmount,
                    RecordEnum.TRADFI_TRADINGADD.getCode(), null, order.getOrderNo(), "TradFi交易+",
                    symbolAsset.getAvailableAmount(), symbolAsset.getAvailableAmount().add(addAmount),
                    order.getSymbol(), user.getAdminParentIds());
        } else {
            BigDecimal addAmount = dealValue.subtract(fee);
            tAppAssetService.reduceAssetByUserId(user.getUserId(), order.getSymbol(), dealNum);
            tAppAssetService.addAssetByUserId(user.getUserId(), SETTLE_COIN, addAmount);
            appWalletRecordService.generateRecord(user.getUserId(), dealNum,
                    RecordEnum.TRADFI_TRADINGSUB.getCode(), null, order.getOrderNo(), "TradFi交易-",
                    symbolAsset.getAvailableAmount(), symbolAsset.getAvailableAmount().subtract(dealNum),
                    order.getSymbol(), user.getAdminParentIds());
            appWalletRecordService.generateRecord(user.getUserId(), addAmount,
                    RecordEnum.TRADFI_TRADINGADD.getCode(), null, order.getOrderNo(), "TradFi交易+",
                    usdtAsset.getAvailableAmount(), usdtAsset.getAvailableAmount().add(addAmount),
                    SETTLE_COIN, user.getAdminParentIds());
        }
    }

    private void pendingOrder(TTradfiOrder order, Long userId) {
        order.setCreateTime(DateUtils.getNowDate());
        order.setUpdateTime(DateUtils.getNowDate());
        order.setDelegateTime(DateUtils.getNowDate());
        order.setStatus(0);
        order.setFee(FEE_RATE);
        order.setDealNum(BigDecimal.ZERO);
        order.setDealValue(BigDecimal.ZERO);
        order.setDealPrice(BigDecimal.ZERO);
        tTradfiOrderMapper.insert(order);
        tAppAssetService.occupiedAssetByUserId(userId,
                order.getType() == 0 ? SETTLE_COIN : order.getSymbol(),
                order.getType() == 0 ? order.getDelegateValue() : order.getDelegateTotal());
    }

    @Transactional
    @Override
    public int canCelOrder(TTradfiOrder tradfiOrder) {
        TAppAsset asset = getAsset(tradfiOrder.getUserId(),
                tradfiOrder.getType() == 0 ? SETTLE_COIN : tradfiOrder.getSymbol());
        BigDecimal occupied = tradfiOrder.getType() == 0
                ? tradfiOrder.getDelegateValue()
                : tradfiOrder.getDelegateTotal();
        asset.setAvailableAmount(asset.getAvailableAmount().add(occupied));
        asset.setOccupiedAmount(asset.getOccupiedAmount().subtract(occupied));
        tAppAssetService.updateByUserId(asset);
        tradfiOrder.setStatus(3);
        return tTradfiOrderMapper.updateTTradfiOrder(tradfiOrder);
    }

    @Override
    public List<TTradfiOrder> selectOrderList(TTradfiOrder tTradfiOrder) {
        return tTradfiOrderMapper.selectOrderList(tTradfiOrder);
    }

    private void ensureAsset(TAppUser user, String symbol) {
        if (getAsset(user.getUserId(), symbol) == null) {
            tAppAssetService.createAsset(user, symbol, AssetEnum.PLATFORM_ASSETS.getCode());
        }
    }

    private TAppAsset getAsset(Long userId, String symbol) {
        return tAppAssetService.getOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
    }
}
