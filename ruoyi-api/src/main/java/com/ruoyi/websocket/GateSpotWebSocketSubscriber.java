package com.ruoyi.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.bussiness.domain.KlineSymbol;
import com.ruoyi.socket.manager.WebSocketUserManager;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Gate.io 现货 WebSocket 订阅器（替代被屏蔽的 Binance）。
 *
 * 端点 wss://api.gateio.ws/ws/v4/ 公开免鉴权。订阅两条频道：
 *   - spot.candlesticks 1m → 实时 kline + 合成 trade
 *   - spot.tickers          → 24h ticker (DETAIL)
 *
 * Gate 推送格式 → 转 Binance 兼容 JSON → 复用 webSocketUserManager 既有方法。
 * 兼容现有所有控盘逻辑（BOT_MAP / con- prefix）— 因为输出格式与 Binance 完全一致。
 *
 * 符号转换：
 *   内部存储：btcusdt（小写、连写）
 *   Gate 订阅：BTC_USDT（大写、下划线）
 *   推回前端：BTCUSDT（大写、连写，兼容 Binance）
 */
@Slf4j
public class GateSpotWebSocketSubscriber extends WebSocketClient {

    private static final long RECONNECT_INTERVAL_MS = 5000;
    private static final Random RAND = new Random();

    private final WebSocketUserManager webSocketUserManager;
    private final Set<String> internalSymbols;        // {"btcusdt", "ethusdt", ...}
    private final List<KlineSymbol> coinList;         // echo 自发币列表
    /** Gate 大写 pair（BTC_USDT） -> 内部小写连写（btcusdt） */
    private final Map<String, String> gatePairIndex = new HashMap<>();

    private final Timer reconnectTimer = new Timer("gate-spot-ws-reconnect", true);

    public GateSpotWebSocketSubscriber(URI serverUri,
                                       Draft draft,
                                       Set<String> internalSymbols,
                                       List<KlineSymbol> coinList,
                                       WebSocketUserManager webSocketUserManager) {
        super(serverUri, draft);
        this.internalSymbols = internalSymbols;
        this.coinList = coinList;
        this.webSocketUserManager = webSocketUserManager;
        for (String s : internalSymbols) {
            String gatePair = toGatePair(s);
            if (gatePair != null) gatePairIndex.put(gatePair, s.toLowerCase());
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("[Gate-Spot-WS] connected, subscribing {} symbols (1m candle + ticker)", internalSymbols.size());
        long ts = System.currentTimeMillis() / 1000;
        for (String s : internalSymbols) {
            String pair = toGatePair(s);
            if (pair == null) continue;
            // 1m candlestick → 推 KLINE + TRADE
            send(buildSubscribe(ts, "spot.candlesticks", "1m", pair));
            // ticker → 推 DETAIL（含 24h 高低开收）
            send(buildSubscribeTicker(ts, "spot.tickers", pair));
        }
    }

    private static String buildSubscribe(long ts, String channel, String interval, String pair) {
        JSONObject msg = new JSONObject();
        msg.put("time", ts);
        msg.put("channel", channel);
        msg.put("event", "subscribe");
        JSONArray payload = new JSONArray();
        payload.add(interval);
        payload.add(pair);
        msg.put("payload", payload);
        return msg.toJSONString();
    }

    private static String buildSubscribeTicker(long ts, String channel, String pair) {
        JSONObject msg = new JSONObject();
        msg.put("time", ts);
        msg.put("channel", channel);
        msg.put("event", "subscribe");
        JSONArray payload = new JSONArray();
        payload.add(pair);
        msg.put("payload", payload);
        return msg.toJSONString();
    }

    /** 内部 "btcusdt" → Gate "BTC_USDT" */
    private static String toGatePair(String internalLower) {
        if (internalLower == null || internalLower.isEmpty()) return null;
        String lower = internalLower.toLowerCase();
        if (lower.endsWith("usdt") && lower.length() > 4) {
            return lower.substring(0, lower.length() - 4).toUpperCase() + "_USDT";
        }
        return null;
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) return;
        if ("ping".equalsIgnoreCase(message)) { send("pong"); return; }
        JSONObject obj;
        try {
            obj = JSON.parseObject(message);
        } catch (Exception e) {
            log.warn("[Gate-Spot-WS] non-json: {}", message);
            return;
        }
        String channel = obj.getString("channel");
        String event   = obj.getString("event");
        if ("subscribe".equals(event)) {
            JSONObject err = obj.getJSONObject("error");
            if (err != null) {
                log.warn("[Gate-Spot-WS] subscribe failed: {} payload={}",
                        err.getString("message"), obj.get("payload"));
            }
            return;
        }
        if (!"update".equals(event)) return;

        if ("spot.candlesticks".equals(channel)) {
            JSONObject k = obj.getJSONObject("result");
            if (k == null) return;
            handleCandlestick(k);
        } else if ("spot.tickers".equals(channel)) {
            JSONObject t = obj.getJSONObject("result");
            if (t == null) return;
            handleTicker(t);
        }
    }

    /**
     * spot.candlesticks 1m 推送：
     *   {"t":"1777455540","v":"55408.7","c":"2330.62","h":"2331","l":"2330.39","o":"2330.79","n":"1m_ETH_USDT","a":"23.77","w":false}
     * → 转 Binance kline JSON → binanceKlineSendMeg
     * → 合成 TRADE → binanceTRADESendMeg
     * → 对每个匹配的 echo 自发币，按 proportion 倍率再推一遍（仿现有 ownKline / ownTrade）
     */
    private void handleCandlestick(JSONObject k) {
        String n = k.getString("n");
        if (n == null) return;
        int sep = n.indexOf('_');
        if (sep <= 0) return;
        String interval = n.substring(0, sep);
        String gatePair = n.substring(sep + 1).toUpperCase();
        String internal = gatePairIndex.get(gatePair);
        if (internal == null) return;
        if (!"1m".equals(interval)) return;

        BigDecimal o = parseBig(k.getString("o"));
        BigDecimal h = parseBig(k.getString("h"));
        BigDecimal l = parseBig(k.getString("l"));
        BigDecimal c = parseBig(k.getString("c"));
        BigDecimal v = parseBig(k.getString("v"));
        BigDecimal q = parseBig(k.getString("a"));   // a = quote volume
        long t  = parseLongSafe(k.getString("t")) * 1000L;
        long T  = t + 60_000L - 1L;
        long now = System.currentTimeMillis();

        String binanceSymbol = internal.toUpperCase();   // e.g. "BTCUSDT"

        // --- 构建 Binance kline JSON ---
        JSONObject klineMsg = buildBinanceKline(binanceSymbol, "1m", t, T, o, h, l, c, v, q, now);
        try {
            webSocketUserManager.binanceKlineSendMeg(klineMsg.toJSONString());
        } catch (Exception e) {
            log.warn("[Gate-Spot-WS] kline push failed sym={}", internal, e);
        }

        // --- 合成 TRADE 消息 ---
        String tradeMsg = createTradeFromKline(binanceSymbol, c, now);
        try {
            webSocketUserManager.binanceTRADESendMeg(tradeMsg);
        } catch (Exception e) {
            log.warn("[Gate-Spot-WS] trade push failed sym={}", internal, e);
        }

        // --- echo 自发币：按 proportion 倍率再推 ---
        String coinNoUsdt = internal.replace("usdt", "");
        for (KlineSymbol kSym : coinList) {
            String referLower = kSym.getReferCoin() == null ? "" : kSym.getReferCoin().toLowerCase();
            if (referLower.equals(coinNoUsdt)) {
                String klineEcho = applyKlineProportion(klineMsg, kSym);
                String tradeEcho = applyTradeProportion(tradeMsg, kSym);
                try {
                    webSocketUserManager.binanceKlineSendMeg(klineEcho);
                    webSocketUserManager.binanceTRADESendMeg(tradeEcho);
                } catch (Exception ex) {
                    log.warn("[Gate-Spot-WS] echo push failed sym={}", kSym.getSymbol(), ex);
                }
            }
        }
    }

    /**
     * spot.tickers 推送：
     *   {"currency_pair":"BTC_USDT","last":"77254.1","change_percentage":"0.7143",
     *    "high_24h":"77446.7","low_24h":"75666.7","base_volume":"6088","quote_volume":"4.65e8"}
     * → 转 Binance 24hrTicker JSON → savePriceRdies + binanceDETAILSendMeg
     */
    private void handleTicker(JSONObject t) {
        String pair = t.getString("currency_pair");
        if (pair == null) return;
        String internal = gatePairIndex.get(pair.toUpperCase());
        if (internal == null) return;

        BigDecimal last = parseBig(t.getString("last"));
        BigDecimal h24  = parseBig(t.getString("high_24h"));
        BigDecimal l24  = parseBig(t.getString("low_24h"));
        BigDecimal pct  = parseBig(t.getString("change_percentage"));
        BigDecimal v    = parseBig(t.getString("base_volume"));
        BigDecimal q    = parseBig(t.getString("quote_volume"));

        // 由 last 和 change_percentage 反算 24h 开盘价
        BigDecimal open24h;
        BigDecimal pctAbsCheck = pct == null ? BigDecimal.ZERO : pct;
        BigDecimal denom = BigDecimal.ONE.add(pctAbsCheck.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
        if (denom.signum() != 0) {
            open24h = last.divide(denom, 10, RoundingMode.HALF_UP);
        } else {
            open24h = last;
        }

        String binanceSymbol = internal.toUpperCase();
        long now = System.currentTimeMillis();

        JSONObject detail = new JSONObject();
        detail.put("e", "24hrTicker");
        detail.put("E", now);
        detail.put("s", binanceSymbol);
        detail.put("c", str(last));
        detail.put("o", str(open24h));
        detail.put("h", str(h24));
        detail.put("l", str(l24));
        detail.put("v", str(v));
        detail.put("q", str(q));
        detail.put("n", "0");
        detail.put("P", str(pct));
        detail.put("p", str(last.subtract(open24h)));

        try {
            webSocketUserManager.savePriceRdies(detail.toJSONString());
            webSocketUserManager.binanceDETAILSendMeg(detail.toJSONString());
        } catch (Exception e) {
            log.warn("[Gate-Spot-WS] DETAIL push failed sym={}", internal, e);
        }

        // echo 自发币 detail
        String coinNoUsdt = internal.replace("usdt", "");
        for (KlineSymbol kSym : coinList) {
            String referLower = kSym.getReferCoin() == null ? "" : kSym.getReferCoin().toLowerCase();
            if (referLower.equals(coinNoUsdt)) {
                String detailEcho = applyDetailProportion(detail, kSym);
                try {
                    webSocketUserManager.savePriceRdies(detailEcho);
                    webSocketUserManager.binanceDETAILSendMeg(detailEcho);
                } catch (Exception ex) {
                    log.warn("[Gate-Spot-WS] echo detail push failed sym={}", kSym.getSymbol(), ex);
                }
            }
        }
    }

    // ============== Binance JSON 构造 / 转换 ==============

    private static JSONObject buildBinanceKline(String s, String interval, long t, long T,
                                                BigDecimal o, BigDecimal h, BigDecimal l,
                                                BigDecimal c, BigDecimal v, BigDecimal q, long nowE) {
        JSONObject msg = new JSONObject();
        msg.put("e", "kline");
        msg.put("E", nowE);
        msg.put("s", s);
        JSONObject k = new JSONObject();
        k.put("t", t);
        k.put("T", T);
        k.put("s", s);
        k.put("i", interval);
        k.put("o", str(o));
        k.put("c", str(c));
        k.put("h", str(h));
        k.put("l", str(l));
        k.put("v", str(v));
        k.put("q", str(q));
        k.put("n", "0");
        msg.put("k", k);
        return msg;
    }

    /** 仿 WebSocketSubscriber.createEvent — 由 close 价合成一条 aggTrade 给前端。 */
    private static String createTradeFromKline(String s, BigDecimal p, long now) {
        BigDecimal q;
        if (p.compareTo(BigDecimal.ONE) < 0) {
            q = new BigDecimal(50 + RAND.nextInt(1950));
        } else if (p.compareTo(new BigDecimal("10")) < 0) {
            q = new BigDecimal(RAND.nextInt(2000));
        } else if (p.compareTo(new BigDecimal("50")) < 0) {
            q = new BigDecimal(RAND.nextInt(20));
        } else if (p.compareTo(new BigDecimal("5000")) < 0) {
            q = new BigDecimal(50 + RAND.nextInt(4950)).divide(new BigDecimal("1000000"), 8, RoundingMode.HALF_UP);
        } else {
            q = new BigDecimal(50 + RAND.nextInt(4950)).divide(new BigDecimal("10000000"), 8, RoundingMode.HALF_UP);
        }
        boolean m = RAND.nextInt(2) > 0;
        JSONObject msg = new JSONObject();
        msg.put("e", "aggTrade");
        msg.put("E", now);
        msg.put("s", s);
        msg.put("p", str(p));
        msg.put("q", str(q));
        msg.put("T", now);
        msg.put("m", m);
        return msg.toJSONString();
    }

    /** 把 BTCUSDT 的 kline 应用 proportion 转换为 echo 自发币 kline */
    private static String applyKlineProportion(JSONObject src, KlineSymbol kSym) {
        BigDecimal proportion = (kSym.getProportion() == null || kSym.getProportion().compareTo(BigDecimal.ZERO) == 0)
                ? new BigDecimal("100")
                : kSym.getProportion();
        JSONObject out = new JSONObject(src);
        JSONObject k = out.getJSONObject("k");
        JSONObject newK = new JSONObject(k);
        BigDecimal o = parseBig(newK.getString("o"));
        BigDecimal h = parseBig(newK.getString("h"));
        BigDecimal l = parseBig(newK.getString("l"));
        BigDecimal c = parseBig(newK.getString("c"));
        BigDecimal q = parseBig(newK.getString("q"));
        String newSym = kSym.getSymbol().toUpperCase() + "USDT";
        out.put("s", newSym);
        newK.put("s", newSym);
        newK.put("o", str(o.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        newK.put("h", str(h.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        newK.put("l", str(l.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        newK.put("c", str(c.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        newK.put("q", str(q.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        out.put("k", newK);
        return out.toJSONString();
    }

    private static String applyTradeProportion(String tradeJson, KlineSymbol kSym) {
        BigDecimal proportion = (kSym.getProportion() == null || kSym.getProportion().compareTo(BigDecimal.ZERO) == 0)
                ? new BigDecimal("100")
                : kSym.getProportion();
        JSONObject src = JSON.parseObject(tradeJson);
        JSONObject out = new JSONObject(src);
        BigDecimal p = parseBig(out.getString("p"));
        out.put("s", kSym.getSymbol().toUpperCase() + "USDT");
        out.put("p", str(p.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        return out.toJSONString();
    }

    private static String applyDetailProportion(JSONObject src, KlineSymbol kSym) {
        BigDecimal proportion = (kSym.getProportion() == null || kSym.getProportion().compareTo(BigDecimal.ZERO) == 0)
                ? new BigDecimal("100")
                : kSym.getProportion();
        JSONObject out = new JSONObject(src);
        BigDecimal o = parseBig(out.getString("o"));
        BigDecimal h = parseBig(out.getString("h"));
        BigDecimal l = parseBig(out.getString("l"));
        BigDecimal c = parseBig(out.getString("c"));
        BigDecimal q = parseBig(out.getString("q"));
        out.put("s", kSym.getSymbol().toUpperCase() + "USDT");
        out.put("o", str(o.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        out.put("h", str(h.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        out.put("l", str(l.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        out.put("c", str(c.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        out.put("q", str(q.multiply(proportion).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        return out.toJSONString();
    }

    // ============== utils ==============

    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private static String str(BigDecimal b) {
        return b == null ? "0" : b.toPlainString();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("[Gate-Spot-WS] closed code={} reason={} remote={}", code, reason, remote);
        if (code != -1) scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        log.error("[Gate-Spot-WS] error: {}", ex.getMessage());
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectTimer.schedule(new TimerTask() {
            @Override public void run() {
                try {
                    log.info("[Gate-Spot-WS] reconnecting...");
                    reconnect();
                } catch (Exception e) {
                    log.warn("[Gate-Spot-WS] reconnect failed: {}", e.getMessage());
                }
            }
        }, RECONNECT_INTERVAL_MS);
    }

    /** 工厂便捷方法：从已有内部 symbol 列表（小写连写）构建集合。 */
    public static Set<String> normalizeSymbols(Set<String> raw) {
        Set<String> out = new HashSet<>();
        for (String s : raw) {
            if (s != null && !s.isEmpty()) out.add(s.toLowerCase());
        }
        return out;
    }
}
