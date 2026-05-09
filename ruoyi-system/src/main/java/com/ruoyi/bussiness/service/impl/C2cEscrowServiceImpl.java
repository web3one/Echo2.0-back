package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.service.IC2cEscrowService;
import com.ruoyi.bussiness.service.ITAppAssetService;
import com.ruoyi.bussiness.service.ITAppWalletRecordService;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.RecordEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * C2C托管服务业务层处理
 */
@Service
public class C2cEscrowServiceImpl implements IC2cEscrowService {

    @Resource
    private ITAppAssetService tAppAssetService;

    @Resource
    private ITAppWalletRecordService tAppWalletRecordService;

    @Resource
    private TAppAssetMapper tAppAssetMapper;

    @Override
    public void freezeAsset(Long userId, String symbol, BigDecimal amount, String serialId, String adminParentIds) {
        symbol = normalizeSymbol(symbol);
        validateAmount(amount);
        // Get asset before freeze
        TAppAsset asset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        BigDecimal availableAmount = asset != null && asset.getAvailableAmount() != null
                ? asset.getAvailableAmount() : BigDecimal.ZERO;
        if (asset == null || availableAmount.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }
        BigDecimal beforeAmount = availableAmount;
        // available -= amount, occupied += amount
        int updated = tAppAssetService.occupiedAssetByUserId(userId, symbol, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
        // Generate wallet record
        tAppWalletRecordService.generateRecord(userId, amount,
                RecordEnum.C2C_ESCROW_FREEZE.getCode(), "", serialId,
                RecordEnum.C2C_ESCROW_FREEZE.getInfo(),
                beforeAmount, beforeAmount.subtract(amount),
                symbol, adminParentIds);
    }

    @Override
    public void releaseAsset(Long sellerUserId, Long buyerUserId, String symbol, BigDecimal amount,
                             String serialId, String sellerAdminParentIds, String buyerAdminParentIds) {
        symbol = normalizeSymbol(symbol);
        validateAmount(amount);
        // --- Seller side: amout -= amount, occupied_amount -= amount ---
        TAppAsset sellerAsset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, sellerUserId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        BigDecimal sellerTotal = sellerAsset != null && sellerAsset.getAmout() != null ? sellerAsset.getAmout() : BigDecimal.ZERO;
        BigDecimal sellerOccupied = sellerAsset != null && sellerAsset.getOccupiedAmount() != null ? sellerAsset.getOccupiedAmount() : BigDecimal.ZERO;
        if (sellerAsset == null || sellerOccupied.compareTo(amount) < 0 || sellerTotal.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient escrow balance");
        }
        BigDecimal sellerBefore = sellerTotal;
        sellerAsset.setAmout(sellerTotal.subtract(amount));
        sellerAsset.setOccupiedAmount(sellerOccupied.subtract(amount));
        tAppAssetMapper.updateByUserId(sellerAsset);
        // Seller wallet record
        tAppWalletRecordService.generateRecord(sellerUserId, amount,
                RecordEnum.C2C_ESCROW_DEDUCT.getCode(), "", serialId,
                RecordEnum.C2C_ESCROW_DEDUCT.getInfo(),
                sellerBefore, sellerBefore.subtract(amount),
                symbol, sellerAdminParentIds);

        // --- Buyer side: amout += amount, available += amount ---
        TAppAsset buyerAsset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, buyerUserId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        BigDecimal buyerBefore = buyerAsset != null && buyerAsset.getAvailableAmount() != null
                ? buyerAsset.getAvailableAmount() : BigDecimal.ZERO;
        int buyerUpdated = tAppAssetService.addAssetByUserId(buyerUserId, symbol, amount);
        if (buyerUpdated == 0) {
            throw new RuntimeException("Buyer asset account not found");
        }
        // Buyer wallet record
        tAppWalletRecordService.generateRecord(buyerUserId, amount,
                RecordEnum.C2C_ESCROW_RELEASE.getCode(), "", serialId,
                RecordEnum.C2C_ESCROW_RELEASE.getInfo(),
                buyerBefore, buyerBefore.add(amount),
                symbol, buyerAdminParentIds);
    }

    @Override
    public void unfreezeAsset(Long userId, String symbol, BigDecimal amount, String serialId, String adminParentIds) {
        symbol = normalizeSymbol(symbol);
        validateAmount(amount);
        // Get asset before unfreeze
        TAppAsset asset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, symbol)
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        if (asset == null || asset.getOccupiedAmount() == null || asset.getOccupiedAmount().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient escrow balance");
        }
        BigDecimal beforeAvailable = asset.getAvailableAmount() != null ? asset.getAvailableAmount() : BigDecimal.ZERO;
        BigDecimal occupiedAmount = asset.getOccupiedAmount() != null ? asset.getOccupiedAmount() : BigDecimal.ZERO;
        // occupied -= amount, available += amount
        asset.setOccupiedAmount(occupiedAmount.subtract(amount));
        asset.setAvailableAmount(beforeAvailable.add(amount));
        tAppAssetMapper.updateByUserId(asset);
        // Generate wallet record
        tAppWalletRecordService.generateRecord(userId, amount,
                RecordEnum.C2C_ESCROW_UNFREEZE.getCode(), "", serialId,
                RecordEnum.C2C_ESCROW_UNFREEZE.getInfo(),
                beforeAvailable, beforeAvailable.add(amount),
                symbol, adminParentIds);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "usdt";
        }
        return symbol.trim().toLowerCase();
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }
    }
}
