package com.ruoyi.websocket;

import com.ruoyi.bussiness.domain.*;
import com.ruoyi.bussiness.service.*;
import com.ruoyi.socket.manager.BianaceWebSocketClient;
import com.ruoyi.socket.manager.WebSocketUserManager;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.drafts.Draft_6455;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class WebSocketConfigdd {

    @Resource
    private ITSecondCoinConfigService secondCoinConfigService;
    @Resource
    private ITContractCoinService contractCoinService;
    @Resource
    private ITCurrencySymbolService tCurrencySymbolService;
    @Resource
    private ITSymbolManageService tSymbolManageService;
    @Resource
    private IKlineSymbolService klineSymbolService;

    @Resource
    private WebSocketUserManager webSocketUserManager;
    @Resource
    private ITOwnCoinService ownCoinService;
    @Resource
    private ITTradfiSymbolService tTradfiSymbolService;

    public WebSocketSubscriber webSocketSubscriberKline() {
        Set<String> strings = new HashSet<>();
        //秒合约
        TSecondCoinConfig tSecondCoinConfig = new TSecondCoinConfig();
        tSecondCoinConfig.setMarket("binance");
        tSecondCoinConfig.setStatus(1L);
        List<TSecondCoinConfig> tSecondCoinConfigs = secondCoinConfigService.selectTSecondCoinConfigList(tSecondCoinConfig);
        for (TSecondCoinConfig secondCoinConfig : tSecondCoinConfigs) {
            strings.add(secondCoinConfig.getSymbol());
        }
        //U本位
        TContractCoin tContractCoin =new TContractCoin();
        tContractCoin.setEnable(0L);
        tContractCoin.setMarket("binance");
        List<TContractCoin> tContractCoins = contractCoinService.selectTContractCoinList(tContractCoin);
        for (TContractCoin contractCoin : tContractCoins) {
            strings.add(contractCoin.getSymbol());
        }
        //币币
        TCurrencySymbol tCurrencySymbol = new TCurrencySymbol();
        tCurrencySymbol.setEnable("1");
        tCurrencySymbol.setMarket("binance");
        List<TCurrencySymbol> tCurrencySymbols = tCurrencySymbolService.selectTCurrencySymbolList(tCurrencySymbol);
        for (TCurrencySymbol currencySymbol : tCurrencySymbols) {
            strings.add(currencySymbol.getSymbol());
        }

        //兑换
        TSymbolManage tSymbolManage = new TSymbolManage();
        tSymbolManage.setEnable("1");
        tCurrencySymbol.setMarket("binance");
        List<TSymbolManage> tSymbolManages = tSymbolManageService.selectTSymbolManageList(tSymbolManage);
        for (TSymbolManage symbolManage : tSymbolManages) {
            strings.add(symbolManage.getSymbol()+"usdt");
        }
        //发币  和 自发币
        KlineSymbol klineSymbol = new KlineSymbol().setMarket("echo");
        List<KlineSymbol> list = klineSymbolService.selectKlineSymbolList(klineSymbol);
        for (KlineSymbol kline : list) {
            strings.add(kline.getReferCoin()+"usdt");
        }
        StringBuffer  sb = new StringBuffer();
        for (String coin: strings) {
            sb=sb.append(coin).append("@kline_1m/");
        }
        String substring = sb.substring(0, sb.length() - 1);
        System.out.println("substring:"+substring);


        URI socket = URI.create("wss://stream.binance.com:9443/ws/"+substring);
        // URI socket = URI.create("wss://data-stream.binance.vision/ws/"+substring);

        //int port = socket.getPort();
        //System.out.println("Port: " + port);

        return new WebSocketSubscriber(socket, new Draft_6455(),list,webSocketUserManager,strings);
    }

    public WebSocketSubscriber webSocketSubscriberDetail() {
        Set<String> strings = new HashSet<>();
        //秒合约
        TSecondCoinConfig tSecondCoinConfig = new TSecondCoinConfig();
        tSecondCoinConfig.setMarket("binance");
        tSecondCoinConfig.setStatus(1L);
        List<TSecondCoinConfig> tSecondCoinConfigs = secondCoinConfigService.selectTSecondCoinConfigList(tSecondCoinConfig);
        for (TSecondCoinConfig secondCoinConfig : tSecondCoinConfigs) {
            strings.add(secondCoinConfig.getSymbol());
        }
        //U本位
        TContractCoin tContractCoin =new TContractCoin();
        tContractCoin.setEnable(0L);
        tContractCoin.setMarket("binance");
        List<TContractCoin> tContractCoins = contractCoinService.selectTContractCoinList(tContractCoin);
        for (TContractCoin contractCoin : tContractCoins) {
            strings.add(contractCoin.getSymbol());
        }
        //币币
        TCurrencySymbol tCurrencySymbol = new TCurrencySymbol();
        tCurrencySymbol.setEnable("1");
        tCurrencySymbol.setMarket("binance");
        List<TCurrencySymbol> tCurrencySymbols = tCurrencySymbolService.selectTCurrencySymbolList(tCurrencySymbol);
        for (TCurrencySymbol currencySymbol : tCurrencySymbols) {
            strings.add(currencySymbol.getSymbol());
        }

        //兑换
        TSymbolManage tSymbolManage = new TSymbolManage();
        tSymbolManage.setEnable("1");
        tCurrencySymbol.setMarket("binance");
        List<TSymbolManage> tSymbolManages = tSymbolManageService.selectTSymbolManageList(tSymbolManage);
        for (TSymbolManage symbolManage : tSymbolManages) {
            strings.add(symbolManage.getSymbol()+"usdt");
        }
        //发币  和 自发币
        KlineSymbol klineSymbol = new KlineSymbol().setMarket("echo");
        List<KlineSymbol> list = klineSymbolService.selectKlineSymbolList(klineSymbol);
        for (KlineSymbol kline : list) {
            strings.add(kline.getReferCoin()+"usdt");
        }

        URI socket =URI.create("wss://stream.binance.com:9443/ws/!ticker@arr");
        // URI socket =URI.create("wss://data-stream.binance.vision/ws/!ticker@arr");

        return new WebSocketSubscriber(socket, new Draft_6455(),list,webSocketUserManager,strings);
    }

    /**
     * Gate.io TradFi WebSocket — 股票/股指/外汇/贵金属/大宗商品
     * 订阅 t_tradfi_symbol 中 status=1 且 market='gate' 的标的
     * 公开免鉴权，订阅 1m + 1d candlesticks。
     *
     * @return null 表示无可用 TradFi 标的，调用方应跳过启动
     */
    public GateTradfiWebSocketSubscriber gateTradfiSubscriber() {
        List<TTradfiSymbol> tradfiSymbols = tTradfiSymbolService.selectActiveForPolling("gate");
        if (tradfiSymbols == null || tradfiSymbols.isEmpty()) {
            log.info("[Gate-TradFi-WS] 无启用的 gate 数据源标的，跳过订阅");
            return null;
        }
        URI socket = URI.create("wss://fx-ws.gateio.ws/v4/ws/tradfi");
        log.info("[Gate-TradFi-WS] 准备订阅 {} 个 TradFi 标的", tradfiSymbols.size());
        return new GateTradfiWebSocketSubscriber(socket, new Draft_6455(), tradfiSymbols, webSocketUserManager);
    }

    /**
     * Gate.io 现货 WebSocket — 加密币（替代被屏蔽的 Binance）
     * 订阅 t_currency_symbol / t_contract_coin / t_second_coin_config 里
     * market='gate' 的所有加密币种 + t_kline_symbol 里 echo 自发币的 referCoin。
     *
     * @return null 表示无可用 gate 加密币，调用方应跳过启动
     */
    public GateSpotWebSocketSubscriber gateSpotSubscriber() {
        Set<String> symbols = new HashSet<>();

        // 秒合约
        TSecondCoinConfig sec = new TSecondCoinConfig();
        sec.setMarket("gate");
        sec.setStatus(1L);
        for (TSecondCoinConfig s : secondCoinConfigService.selectTSecondCoinConfigList(sec)) {
            if (s.getSymbol() != null) symbols.add(s.getSymbol().toLowerCase());
        }
        // U 本位
        TContractCoin con = new TContractCoin();
        con.setMarket("gate");
        con.setEnable(0L);
        for (TContractCoin s : contractCoinService.selectTContractCoinList(con)) {
            if (s.getSymbol() != null) symbols.add(s.getSymbol().toLowerCase());
        }
        // 币币
        TCurrencySymbol cur = new TCurrencySymbol();
        cur.setMarket("gate");
        cur.setEnable("1");
        for (TCurrencySymbol s : tCurrencySymbolService.selectTCurrencySymbolList(cur)) {
            if (s.getSymbol() != null) symbols.add(s.getSymbol().toLowerCase());
        }
        // echo 自发币：把 referCoin+usdt 加入订阅，让 echo 价格随 Gate 现货走
        KlineSymbol klineSymbol = new KlineSymbol().setMarket("echo");
        List<KlineSymbol> echoList = klineSymbolService.selectKlineSymbolList(klineSymbol);
        for (KlineSymbol e : echoList) {
            if (e.getReferCoin() != null && !e.getReferCoin().isEmpty()) {
                symbols.add((e.getReferCoin() + "usdt").toLowerCase());
            }
        }
        // 仅保留以 usdt 结尾的（Gate 现货按 BASE_USDT 订阅）
        Set<String> usdtPairs = new HashSet<>();
        for (String s : symbols) {
            if (s.endsWith("usdt") && s.length() > 4) usdtPairs.add(s);
        }

        if (usdtPairs.isEmpty()) {
            log.info("[Gate-Spot-WS] 无启用的 gate 加密币，跳过订阅");
            return null;
        }
        URI socket = URI.create("wss://api.gateio.ws/ws/v4/");
        log.info("[Gate-Spot-WS] 准备订阅 {} 个加密币（含 echo 关联）", usdtPairs.size());
        return new GateSpotWebSocketSubscriber(socket, new Draft_6455(), usdtPairs, echoList, webSocketUserManager);
    }
}
