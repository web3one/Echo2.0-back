package com.ruoyi.bussiness.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.bussiness.domain.TAppAsset;
import com.ruoyi.bussiness.domain.TAppUser;
import com.ruoyi.bussiness.domain.TAppUserDetail;
import com.ruoyi.bussiness.domain.TC2cAdvert;
import com.ruoyi.bussiness.domain.TC2cMerchant;
import com.ruoyi.bussiness.domain.dto.C2cMerchantApplyDTO;
import com.ruoyi.bussiness.mapper.TAppAssetMapper;
import com.ruoyi.bussiness.mapper.TC2cMerchantMapper;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.common.enums.AssetEnum;
import com.ruoyi.common.enums.RecordEnum;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.OrderUtils;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * C2C商家Service业务层处理
 */
@Service
public class C2cMerchantServiceImpl extends ServiceImpl<TC2cMerchantMapper, TC2cMerchant> implements IC2cMerchantService {

    @Resource
    private TC2cMerchantMapper c2cMerchantMapper;

    @Resource
    private ITAppUserService tAppUserService;

    @Resource
    private ITAppUserDetailService tAppUserDetailService;

    @Resource
    private IC2cConfigService c2cConfigService;

    @Resource
    private IC2cEscrowService c2cEscrowService;

    @Resource
    private ITAppAssetService tAppAssetService;

    @Resource
    private ITAppWalletRecordService tAppWalletRecordService;

    @Resource
    private TAppAssetMapper tAppAssetMapper;

    @Resource
    private IC2cAdvertService c2cAdvertService;

    @Override
    @Transactional
    public String applyMerchant(Long userId, C2cMerchantApplyDTO dto) {
        // Check if already applied
        TC2cMerchant existing = c2cMerchantMapper.selectByUserId(userId);
        if (existing != null && existing.getStatus() != 2) {
            return MessageUtils.message("c2c.merchant.applied_or_active");
        }

        // Get user info
        TAppUser user = tAppUserService.selectTAppUserByUserId(userId);
        if (user == null) {
            return MessageUtils.message("c2c.user.not_found");
        }

        // Check KYC：仅校验实名认证（advanced）通过；状态码 "1" = EXAMINATION_PASSED
        TAppUserDetail detail = tAppUserService.selectUserDetailByUserId(userId);
        if (detail == null) {
            return MessageUtils.message("c2c.merchant.kyc_required");
        }
        boolean kycPassed = "1".equals(detail.getAuditStatusAdvanced());
        if (!kycPassed) {
            return MessageUtils.message("c2c.merchant.kyc_required");
        }

        // Check fund password
        if (dto.getFundPassword() == null || detail.getUserTardPwd() == null) {
            return MessageUtils.message("c2c.merchant.fund_password_required");
        }
        if (!SecurityUtils.matchesPassword(dto.getFundPassword(), detail.getUserTardPwd())) {
            return MessageUtils.message("c2c.merchant.fund_password_incorrect");
        }

        // Get deposit amount from config
        String depositStr = c2cConfigService.getConfigValue("min_merchant_deposit");
        if (depositStr == null) {
            depositStr = c2cConfigService.getConfigValue("merchant_deposit_amount");
        }
        BigDecimal depositAmount = new BigDecimal(depositStr != null ? depositStr : "100");

        // Check balance
        TAppAsset asset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                .eq(TAppAsset::getUserId, userId)
                .eq(TAppAsset::getSymbol, "usdt")
                .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
        if (asset == null || asset.getAvailableAmount().compareTo(depositAmount) < 0) {
            return MessageUtils.message("c2c.merchant.insufficient_balance");
        }

        // Create or update merchant record
        String serialId = "P2P" + OrderUtils.generateOrderNum();
        BigDecimal beforeAvailable = asset.getAvailableAmount();

        if (existing != null && existing.getStatus() == 2) {
            // Reapply after rejection
            existing.setNickname(dto.getNickname());
            existing.setStatus(0);
            existing.setDepositAmount(depositAmount);
            existing.setRejectReason(null);
            existing.setAdminParentIds(user.getAdminParentIds());
            c2cMerchantMapper.updateById(existing);
        } else {
            TC2cMerchant merchant = new TC2cMerchant();
            merchant.setUserId(userId);
            merchant.setNickname(dto.getNickname());
            merchant.setStatus(0);
            merchant.setTotalOrders(0);
            merchant.setTotalVolume(BigDecimal.ZERO);
            merchant.setCompletionRate(BigDecimal.ZERO);
            merchant.setAvgReleaseTime(0);
            merchant.setPositiveRate(BigDecimal.ZERO);
            merchant.setDepositAmount(depositAmount);
            merchant.setAdminParentIds(user.getAdminParentIds());
            merchant.setCreateTime(new Date());
            c2cMerchantMapper.insert(merchant);
        }

        // Freeze deposit: use occupiedAssetByUserId + manual wallet record with C2C_MERCHANT_DEPOSIT type
        tAppAssetService.occupiedAssetByUserId(userId, "usdt", depositAmount);
        tAppWalletRecordService.generateRecord(userId, depositAmount,
                RecordEnum.C2C_MERCHANT_DEPOSIT.getCode(), "", serialId,
                RecordEnum.C2C_MERCHANT_DEPOSIT.getInfo(),
                beforeAvailable, beforeAvailable.subtract(depositAmount),
                "usdt", user.getAdminParentIds());

        return null;
    }

    @Override
    public int approveMerchant(Long id) {
        TC2cMerchant merchant = c2cMerchantMapper.selectById(id);
        if (merchant == null || merchant.getStatus() != 0) {
            return 0;
        }
        merchant.setStatus(1);
        merchant.setUpdateTime(new Date());
        return c2cMerchantMapper.updateById(merchant);
    }

    @Override
    @Transactional
    public int rejectMerchant(Long id, String reason) {
        TC2cMerchant merchant = c2cMerchantMapper.selectById(id);
        if (merchant == null || merchant.getStatus() != 0) {
            return 0;
        }
        merchant.setStatus(2);
        merchant.setRejectReason(reason);
        merchant.setUpdateTime(new Date());
        c2cMerchantMapper.updateById(merchant);

        // Unfreeze deposit
        if (merchant.getDepositAmount() != null && merchant.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            TAppUser user = tAppUserService.selectTAppUserByUserId(merchant.getUserId());
            String serialId = "P2P" + OrderUtils.generateOrderNum();

            TAppAsset asset = tAppAssetMapper.selectOne(new LambdaQueryWrapper<TAppAsset>()
                    .eq(TAppAsset::getUserId, merchant.getUserId())
                    .eq(TAppAsset::getSymbol, "usdt")
                    .eq(TAppAsset::getType, AssetEnum.PLATFORM_ASSETS.getCode()));
            BigDecimal beforeAvailable = asset != null ? asset.getAvailableAmount() : BigDecimal.ZERO;

            // Reverse the freeze: occupied -= amount, available += amount
            if (asset != null) {
                asset.setOccupiedAmount(asset.getOccupiedAmount().subtract(merchant.getDepositAmount()));
                asset.setAvailableAmount(asset.getAvailableAmount().add(merchant.getDepositAmount()));
                tAppAssetMapper.updateByUserId(asset);
            }

            tAppWalletRecordService.generateRecord(merchant.getUserId(), merchant.getDepositAmount(),
                    RecordEnum.C2C_MERCHANT_DEPOSIT_REFUND.getCode(), "", serialId,
                    RecordEnum.C2C_MERCHANT_DEPOSIT_REFUND.getInfo(),
                    beforeAvailable, beforeAvailable.add(merchant.getDepositAmount()),
                    "usdt", user != null ? user.getAdminParentIds() : "");
        }
        return 1;
    }

    @Override
    @Transactional
    public int disableMerchant(Long id) {
        TC2cMerchant merchant = c2cMerchantMapper.selectById(id);
        if (merchant == null) {
            return 0;
        }
        merchant.setStatus(3);
        merchant.setUpdateTime(new Date());
        c2cMerchantMapper.updateById(merchant);

        // Offline all their adverts
        List<TC2cAdvert> adverts = c2cAdvertService.listMyAdverts(merchant.getUserId());
        if (adverts != null) {
            for (TC2cAdvert advert : adverts) {
                if (advert.getStatus() == 1) {
                    c2cAdvertService.offlineAdvert(advert.getId(), merchant.getUserId());
                }
            }
        }
        return 1;
    }

    @Override
    public int enableMerchant(Long id) {
        TC2cMerchant merchant = c2cMerchantMapper.selectById(id);
        if (merchant == null || merchant.getStatus() != 3) {
            return 0;
        }
        merchant.setStatus(1);
        merchant.setUpdateTime(new Date());
        return c2cMerchantMapper.updateById(merchant);
    }

    @Override
    public TC2cMerchant getMerchantByUserId(Long userId) {
        return c2cMerchantMapper.selectByUserId(userId);
    }

    @Override
    public List<TC2cMerchant> selectMerchantList(TC2cMerchant merchant) {
        return c2cMerchantMapper.selectMerchantList(merchant);
    }
}
