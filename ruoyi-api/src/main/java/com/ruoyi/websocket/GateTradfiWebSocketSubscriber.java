package com.ruoyi.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.bussiness.domain.TTradfiSymbol;
import com.ruoyi.socket.manager.WebSocketUserManager;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gate.io TradFi WebSocket subscriber.
 *
 * 端点 wss://fx-ws.gateio.ws/v4/ws/tradfi 公开免鉴权。订阅两条频道：
 *   - tradfi.candlesticks 1m  → 实时 ticker + 1m kline，推 DETAIL + KLINE
 *   - tradfi.candlesticks 1d  → 24h 开盘价，仅写 KLoader.OPEN_PRICE 不推 H5
 *
 * Gate 推送格式 → 转 Binance 24hrTicker / kline JSON → 复用 webSocketUserManager 既有方法，
 * H5 不需要任何前端改动。
 */
@Slf4j
public class GateTradfiWebSocketSubscriber extends WebSocketClient {

    private static final long RECONNECT_INTERVAL_MS = 5000;
    /**
     * 每条订阅消息之间的最小间隔。Gate WS 实测 50ms (20 req/s) 仍触发 42901 ratelimit，
     * 前 30 条 OK 之后剩余 fail。改用 250ms (4 req/s) + 失败重试机制兜底。
     * 38 条订阅总耗时约 9.5 秒，影响可忽略。
     */
    private static final long SUBSCRIBE_INTERVAL_MS = 250;
    /** 首轮订阅完成后等待回执的时间，之后开始重试 */
    private static final long RETRY_DELAY_MS = 3000;
    /** 重试时每条间隔（更慢，避免再次 ratelimit） */
    private static final long RETRY_INTERVAL_MS = 500;

    private final WebSocketUserManager webSocketUserManager;
    private final List<TTradfiSymbol> symbols;
    /** Gate symbol -> internal symbol，把 "1d_XAUUSD" 解析回 TTradfiSymbol */
    private final Map<String, TTradfiSymbol> gateContractIndex = new HashMap<>();
    /** Gate symbol(大写) -> 当日 1d 开盘价，用于算 24h 涨跌 */
    private final Map<String, BigDecimal> dailyOpenPrice = new ConcurrentHashMap<>();

    /** 订阅成功 / 失败计数，到达 expectedSubs 时打一条 summary 日志 */
    private final AtomicInteger subscribeOk = new AtomicInteger(0);
    private final AtomicInteger subscribeFail = new AtomicInteger(0);
    private volatile int expectedSubs = 0;
    /** ratelimit 失败的 payload，sender 完成后等 RETRY_DELAY_MS 重试一次 */
    private final List<JSONArray> failedPayloads = Collections.synchronizedList(new ArrayList<>());

    private final Timer reconnectTimer = new Timer("gate-tradfi-ws-reconnect", true);

    public GateTradfiWebSocketSubscriber(URI serverUri,
                                         Draft draft,
                                         List<TTradfiSymbol> symbols,
                                         WebSocketUserManager webSocketUserManager) {
        super(serverUri, draft);
        this.symbols = symbols;
        this.webSocketUserManager = webSocketUserManager;
        for (TTradfiSymbol s : symbols) {
            gateContractIndex.put(s.getGateContract().toUpperCase(), s);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("[Gate-TradFi-WS] connected, subscribing {} symbols (1m + 1d) with {}ms throttle",
                symbols.size(), SUBSCRIBE_INTERVAL_MS);
        expectedSubs = symbols.size() * 2;
        subscribeOk.set(0);
        subscribeFail.set(0);
        failedPayloads.clear();
        // 异步发送，避免阻塞 WS 读线程；每条间隔 SUBSCRIBE_INTERVAL_MS 防 Gate ratelimit drop。
        // 每条订阅都用最新 ts，避免靠后的订阅 time 字段落后被 Gate 时钟校验拒绝。
        Thread sender = new Thread(() -> {
            int sent = 0;
            for (TTradfiSymbol s : symbols) {
                try {
                    // 1m 用于 DETAIL + KLINE
                    send(buildSubscribe(System.currentTimeMillis() / 1000, "tradfi.candlesticks", "1m", s.getGateContract()));
                    sent++;
                    Thread.sleep(SUBSCRIBE_INTERVAL_MS);
                    // 1d 用于 24h 开盘价
                    send(buildSubscribe(System.currentTimeMillis() / 1000, "tradfi.candlesticks", "1d", s.getGateContract()));
                    sent++;
                    Thread.sleep(SUBSCRIBE_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.info("[Gate-TradFi-WS] subscribe sender interrupted at sent={}", sent);
                    return;
                } catch (Exception e) {
                    log.info("[Gate-TradFi-WS] subscribe send failed for {}: {}",
                            s.getGateContract(), e.getMessage());
                }
            }
            log.info("[Gate-TradFi-WS] all {} subscribe messages sent, waiting {}ms for acks",
                    sent, RETRY_DELAY_MS);
            // 等回执到齐后，对 ratelimit fail 的 payload 重试一次（更慢的间隔）
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            List<JSONArray> retry;
            synchronized (failedPayloads) {
                retry = new ArrayList<>(failedPayloads);
                failedPayloads.clear();
            }
            if (retry.isEmpty()) return;
            log.info("[Gate-TradFi-WS] retrying {} failed subscriptions ({}ms interval)",
                    retry.size(), RETRY_INTERVAL_MS);
            for (JSONArray p : retry) {
                try {
                    send(buildSubscribeRaw(System.currentTimeMillis() / 1000, "tradfi.candlesticks", p));
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.info("[Gate-TradFi-WS] retry send failed payload={}: {}", p, e.getMessage());
                }
            }
            log.info("[Gate-TradFi-WS] retry done; final ok={} fail={}",
                    subscribeOk.get(), subscribeFail.get());
        }, "gate-tradfi-ws-subscriber");
        sender.setDaemon(true);
        sender.start();
    }

    private static String buildSubscribe(long ts, String channel, String interval, String symbol) {
        JSONArray payload = new JSONArray();
        payload.add(interval);
        payload.add(symbol);
        return buildSubscribeRaw(ts, channel, payload);
    }

    /** 直接接受现成 payload（用于 fail 重试场景） */
    private static String buildSubscribeRaw(long ts, String channel, JSONArray payload) {
        JSONObject msg = new JSONObject();
        msg.put("time", ts);
        msg.put("channel", channel);
        msg.put("event", "subscribe");
        msg.put("payload", payload);
        return msg.toJSONString();
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) return;
        if ("pong".equalsIgnoreCase(message) || "ping".equalsIgnoreCase(message)) {
            if ("ping".equalsIgnoreCase(message)) send("pong");
            return;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(message);
        } catch (Exception e) {
            log.warn("[Gate-TradFi-WS] non-json message: {}", message);
            return;
        }
        String channel = obj.getString("channel");
        String event   = obj.getString("event");
        if (!"update".equals(event) || !"tradfi.candlesticks".equals(channel)) {
            // 订阅回执：累加 ok/fail 计数，到达 expectedSubs 时打 summary
            // 注意 RuoYi logback 用 LevelFilter 严格过滤，WARN 级被吞，所以 fail 详情用 INFO
            if ("subscribe".equals(event)) {
                JSONObject err = obj.getJSONObject("error");
                if (err != null) {
                    int fail = subscribeFail.incrementAndGet();
                    JSONArray failedPayload = obj.getJSONArray("payload");
                    log.info("[Gate-TradFi-WS] subscribe failed (#{}): code={} msg={} payload={}",
                            fail, err.get("code"), err.getString("message"), failedPayload);
                    // ratelimit (42901) 类的失败入队等重试
                    Object code = err.get("code");
                    if (failedPayload != null && code != null && "42901".equals(String.valueOf(code))) {
                        failedPayloads.add(failedPayload);
                    }
                } else {
                    subscribeOk.incrementAndGet();
                }
                int total = subscribeOk.get() + subscribeFail.get();
                if (expectedSubs > 0 && total == expectedSubs) {
                    log.info("[Gate-TradFi-WS] subscribe summary: ok={} fail={} expected={}",
                            subscribeOk.get(), subscribeFail.get(), expectedSubs);
                }
            }
            return;
        }
        JSONArray result = obj.getJSONArray("result");
        if (result == null) return;
        for (int i = 0; i < result.size(); i++) {
            JSONObject k = result.getJSONObject(i);
            handleCandlestick(k);
        }
    }

    /**
     * Gate 1m / 1d candlestick 处理：
     *   {"t":1777448160,"n":"1m_XAUUSD","v":"0","c":"4576.37","h":"4577.09","l":"4576.36","o":"4577.04","a":"0","w":false}
     */
    private void handleCandlestick(JSONObject k) {
        String n = k.getString("n");
        if (n == null) return;
        int sep = n.indexOf('_');
        if (sep <= 0) return;
        String interval     = n.substring(0, sep);
        String gateContract = n.substring(sep + 1).toUpperCase();
        TTradfiSymbol meta = gateContractIndex.get(gateContract);
        if (meta == null) return;

        BigDecimal o = parseBig(k.getString("o"));
        BigDecimal h = parseBig(k.getString("h"));
        BigDecimal l = parseBig(k.getString("l"));
        BigDecimal c = parseBig(k.getString("c"));
        BigDecimal v = parseBig(k.getString("v"));
        Long       t = k.getLong("t");

        if ("1d".equals(interval)) {
            // 1d kline 只为 24h 开盘价；缓存供 1m 推 DETAIL 时用
            dailyOpenPrice.put(gateContract, o);
            return;
        }

        // 1m kline → 推 KLINE + DETAIL
        BigDecimal open24h = dailyOpenPrice.getOrDefault(gateContract, o);
        long nowMs = System.currentTimeMillis();
        long klineOpenMs = (t == null ? nowMs : t * 1000L);
        long klineCloseMs = klineOpenMs + 60_000L - 1L;

        String binanceSymbol = meta.getSymbol().toUpperCase();   // 直接用内部 symbol，不加 USDT 后缀

        // -------- 推 24hrTicker (DETAIL) --------
        // 用 1m 的 c/h/l 作当前价 + 1d 的 o 作 24h 开盘价
        JSONObject detail = new JSONObject();
        detail.put("e", "24hrTicker");
        detail.put("E", nowMs);
        detail.put("s", binanceSymbol);
        detail.put("c", str(c));
        detail.put("o", str(open24h));
        detail.put("h", str(h));
        detail.put("l", str(l));
        detail.put("v", "0");
        detail.put("q", "0");
        detail.put("n", "0");
        BigDecimal pct = (open24h.signum() != 0)
                ? c.subtract(open24h).divide(open24h, 6, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        detail.put("P", str(pct));
        detail.put("p", str(c.subtract(open24h)));

        try {
            webSocketUserManager.savePriceRdies(detail.toJSONString());
            webSocketUserManager.binanceDETAILSendMeg(detail.toJSONString());
        } catch (Exception e) {
            log.warn("[Gate-TradFi-WS] DETAIL push failed sym={}", meta.getSymbol(), e);
        }

        // -------- 推 KLINE --------
        JSONObject klineMsg = new JSONObject();
        klineMsg.put("e", "kline");
        klineMsg.put("E", nowMs);
        klineMsg.put("s", binanceSymbol);
        JSONObject kBlock = new JSONObject();
        kBlock.put("t", klineOpenMs);
        kBlock.put("T", klineCloseMs);
        kBlock.put("s", binanceSymbol);
        kBlock.put("i", interval);
        kBlock.put("o", str(o));
        kBlock.put("c", str(c));
        kBlock.put("h", str(h));
        kBlock.put("l", str(l));
        kBlock.put("v", "0");
        kBlock.put("n", "0");
        kBlock.put("q", "0");
        klineMsg.put("k", kBlock);
        try {
            webSocketUserManager.binanceKlineSendMeg(klineMsg.toJSONString());
        } catch (Exception e) {
            log.warn("[Gate-TradFi-WS] KLINE push failed sym={}", meta.getSymbol(), e);
        }
    }

    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static String str(BigDecimal b) {
        return b == null ? "0" : b.toPlainString();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("[Gate-TradFi-WS] closed code={} reason={} remote={}", code, reason, remote);
        if (code != -1) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("[Gate-TradFi-WS] error: {}", ex.getMessage());
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectTimer.schedule(new TimerTask() {
            @Override public void run() {
                try {
                    log.info("[Gate-TradFi-WS] reconnecting...");
                    reconnect();
                } catch (Exception e) {
                    log.warn("[Gate-TradFi-WS] reconnect failed: {}", e.getMessage());
                }
            }
        }, RECONNECT_INTERVAL_MS);
    }
}
