package com.ruoyi.websocket;

import com.ruoyi.common.utils.DateUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Configuration
public class WebSocketRunner implements CommandLineRunner {

    @Resource
    private WebSocketConfigdd webSocketConfigdd;

    /**
     * 加密币行情数据源开关：
     *   true  → 启动 Binance WebSocket（默认在 IP 未被屏蔽的环境下使用）
     *   false → 跳过 Binance，全部加密币改走 Gate 现货 WS（国内云推荐）
     */
    @Value("${crypto.binance.enabled:false}")
    private boolean binanceEnabled;

    /**
     * 是否启用 Gate 现货 WebSocket（默认开启）。设为 false 表示完全不接 Gate 现货。
     */
    @Value("${crypto.gate-spot.enabled:true}")
    private boolean gateSpotEnabled;

    private Map<String,Object> map=new HashMap<>();
    @Override
    public void run(String... args) {
        //System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

        // ==== 加密币：Binance（可选，国内云通常不可用） ====
        if (binanceEnabled) {
            try {
                WebSocketSubscriber webSocketSubscriberKline = webSocketConfigdd.webSocketSubscriberKline();
                WebSocketSubscriber webSocketSubscriberDetail = webSocketConfigdd.webSocketSubscriberDetail();
                webSocketSubscriberKline.connect();
                webSocketSubscriberDetail.connect();
                map.put("kline", webSocketSubscriberKline);
                map.put("detaul", webSocketSubscriberDetail);
                log.info("[Binance-WS] 已启动");
            } catch (Exception e) {
                log.error("[Binance-WS] 启动失败: {}", e.getMessage(), e);
            }
        } else {
            log.info("[Binance-WS] 已通过配置禁用（crypto.binance.enabled=false）");
        }

        // ==== 加密币：Gate 现货（默认主数据源） ====
        if (gateSpotEnabled) {
            try {
                GateSpotWebSocketSubscriber gateSpot = webSocketConfigdd.gateSpotSubscriber();
                if (gateSpot != null) {
                    gateSpot.connect();
                    map.put("gateSpot", gateSpot);
                    log.info("[Gate-Spot-WS] 已启动");
                }
            } catch (Exception e) {
                log.error("[Gate-Spot-WS] 启动失败: {}", e.getMessage(), e);
            }
        }

        // ==== TradFi：股票/股指/外汇/贵金属/大宗商品 ====
        try {
            GateTradfiWebSocketSubscriber gateTradfi = webSocketConfigdd.gateTradfiSubscriber();
            if (gateTradfi != null) {
                gateTradfi.connect();
                map.put("gateTradfi", gateTradfi);
                log.info("[Gate-TradFi-WS] 已启动");
            }
        } catch (Exception e) {
            log.error("[Gate-TradFi-WS] 启动失败: {}", e.getMessage(), e);
        }
    }

    public void reStart(String... args) throws InterruptedException {
        // Binance（如启用）
        if (binanceEnabled) {
            try {
                WebSocketSubscriber webSocketSubscriberKline = (WebSocketSubscriber) map.get("kline");
                WebSocketSubscriber webSocketSubscriberDetail = (WebSocketSubscriber) map.get("detaul");
                if (webSocketSubscriberKline != null) webSocketSubscriberKline.onClose(-1, null, true);
                if (webSocketSubscriberDetail != null) webSocketSubscriberDetail.onClose(-1, null, true);
                WebSocketSubscriber kline = webSocketConfigdd.webSocketSubscriberKline();
                WebSocketSubscriber detail = webSocketConfigdd.webSocketSubscriberDetail();
                map.put("kline", kline);
                map.put("detaul", detail);
                kline.connect();
                detail.connect();
            } catch (Exception e) {
                log.error("[Binance-WS] 重启失败: {}", e.getMessage(), e);
            }
        }

        // Gate 现货
        if (gateSpotEnabled) {
            try {
                GateSpotWebSocketSubscriber oldSpot = (GateSpotWebSocketSubscriber) map.get("gateSpot");
                if (oldSpot != null) oldSpot.onClose(-1, null, true);
                GateSpotWebSocketSubscriber gateSpot = webSocketConfigdd.gateSpotSubscriber();
                if (gateSpot != null) {
                    gateSpot.connect();
                    map.put("gateSpot", gateSpot);
                }
            } catch (Exception e) {
                log.error("[Gate-Spot-WS] 重启失败: {}", e.getMessage(), e);
            }
        }

        // Gate TradFi
        try {
            GateTradfiWebSocketSubscriber oldTradfi = (GateTradfiWebSocketSubscriber) map.get("gateTradfi");
            if (oldTradfi != null) oldTradfi.onClose(-1, null, true);
            GateTradfiWebSocketSubscriber gateTradfi = webSocketConfigdd.gateTradfiSubscriber();
            if (gateTradfi != null) {
                gateTradfi.connect();
                map.put("gateTradfi", gateTradfi);
            }
        } catch (Exception e) {
            log.error("[Gate-TradFi-WS] 重启失败: {}", e.getMessage(), e);
        }
    }
}
