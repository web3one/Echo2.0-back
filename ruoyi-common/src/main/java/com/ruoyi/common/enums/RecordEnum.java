package com.ruoyi.common.enums;

import java.util.Arrays;
import java.util.LinkedHashMap;

public enum RecordEnum {
    RECHARGE(1, "充值+"),
    WITHDRAW(2, "提现-"),
    SEND_BONUS(3, "赠送彩金+"),
    SUB_BONUS(50, "扣减彩金-"),
    SUB_AMOUNT(4, "人工下分-"),
    SEND_AMOUNT(51, "人工上分+"),
/*    BTC_MANUAL_SCORING(16, "BTC人工上分+"),
    BTC_MANUAL_SUBDIVISION(17, "BTC人工下分-"),
    ETH_MANUAL_SCORING(18, "ETH人工上分+"),
    ETH_MANUAL_SUBDIVISION(19, "ETH人工下分-"),*/
    OPTION_BETTING(5, "秒合约下注-"),
    FINANCIAL_PURCHASE(6, "理财购买-"),
    FINANCIAL_SETTLEMENT(7, "理财结算+"),
    WITHDRAWAL_FAILED(8, "提现失败+"),
    OPTION_SETTLEMENT(9, "秒合约结算+"),
    MINING_TO_BUY(12, "挖矿购买"),
    MINING_REBATE(13, "挖矿返息"),
    MINING_REDEMPTION(14, "挖矿赎回"),
    MINING_SETTLEMENT(15, "挖矿结算"),
    NON_STAKING_MINING_INCOME(20, "矿池收益"),
    CURRENCY_TRADINGADD(21, "币币交易+"),
    CURRENCY_TRADINGSUB(22, "币币交易-"),
    ASSET_ACCOUNTADD(23, "资产账户+"),
    ASSET_ACCOUNTSUB(24, "资产账户-"),
    TRANSACTION_ACCOUNTADD(25, "交易账户+"),
    TRANSACTION_ACCOUNTSUB(26, "交易账户-"),
    CONTRACT_TRANSACTIONSUB(27, "合约交易-"),
    CONTRACT_TRANSACTION_CLOSING(28, "合约交易平仓"),
    CONTRACT_TRADING_ADJUSTMENT_MARGIN(29, "合约交易调整保证金"),
    CONTRACT_TRADING_LIQUIDATION(30, "合约交易强平"),
    AIRDROP_EVENT_REWARDS(31, "空投活动奖励"),
    SUBORDINATE_RECHARGE_REBATE(32, "下级充值返利"),
    SUBORDINATE_MINING_REBATE(33, "下级挖矿返利"),
    FINANCIAL_REBATE(34, "下级理财返利"),
    FUND_TRANSFER(35, "资金划转"),
    DEFI_ACTIVITY(36, "空投活动"),
    DEFI_ORDER(37, "defi挖矿"),
    OWN_COIN_BUY(52, "新币申购"),
    CONTRACT_ADD(53,"追加合约本金"),
    CONTRACT_ADD_AMOUT(54,"追加保证金"),
    FINANCIAL_REDEMPTION(55, "理财赎回"),
    C2C_ESCROW_FREEZE(60, "C2C托管冻结-"),
    C2C_ESCROW_RELEASE(61, "C2C托管放行+"),
    C2C_ESCROW_UNFREEZE(62, "C2C托管解冻+"),
    C2C_ESCROW_DEDUCT(63, "C2C托管扣减-"),
    C2C_MERCHANT_DEPOSIT(64, "C2C商家保证金-"),
    C2C_MERCHANT_DEPOSIT_REFUND(65, "C2C商家保证金退还+"),
    GOLD_NODE_PURCHASE(70, "金矿矿机购买-"),
    GOLD_STATIC_REWARD(71, "金矿静态分红+"),
    GOLD_REFERRAL_REWARD(72, "金矿直推奖+"),
    GOLD_TEAM_REWARD(73, "金矿团队代理奖+"),
    GOLD_AGENCY_FEE(74, "金矿全网手续费分红+"),
    GOLD_FOUNDER_FEE(75, "金矿创世合伙人分红+"),
    GOLD_XGT_RELEASE(76, "金矿XGT锁仓释放+"),
    GOLD_ECO_CREDIT_UNLOCK(77, "金矿生态额度解锁+"),
    GOLD_FOUNDER_PURCHASE(78, "金矿创世席位购买-"),
    GOLD_TO_SPOT_IN(79, "金矿转入现货+"),
    ;
    private final Integer code;
    private final String desc;

    RecordEnum(Integer code, String info) {
        this.code = code;
        this.desc = info;
    }

    public Integer getCode() {
        return code;
    }

    public String getInfo() {
        return desc;
    }


    public static LinkedHashMap<Integer, String> getMap() {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        Arrays.stream(RecordEnum.values()).forEach(recordEnum -> {
            map.put(recordEnum.getCode(), recordEnum.getInfo());
        });
        return map;

    }

    public static void main(String[] args) {
        LinkedHashMap<Integer, String> map = getMap();
        System.out.println(map);
    }
}
