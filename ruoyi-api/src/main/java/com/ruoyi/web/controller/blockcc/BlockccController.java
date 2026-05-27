package com.ruoyi.web.controller.blockcc;

import cc.block.data.api.domain.market.Kline;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.domain.KlineParamVO;
import com.ruoyi.framework.web.domain.Ticker24hVO;
import com.ruoyi.framework.web.service.BlockccService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据源接口 不做验证 直接调用api接口
 *
 * @author ruoyi
 */
@RestController
public class BlockccController {
    public static final Logger log = LoggerFactory.getLogger(BlockccController.class);
    @Value("${mifeng.api.eth}")
    private String apiKey;
    @Value("${mifeng.api.host}")
    private String host;
    @Autowired
    BlockccService blockccService;
    @Autowired
    RedisCache redisCache;

    /**
     * 登录历史k线
     *
     * @param klineParamVO 入参
     * @return 结果
     */
    @PostMapping({"/kline", "/api/kline"})
    public AjaxResult kline(@RequestBody KlineParamVO klineParamVO) {
        String cacheKey = buildKlineCacheKey(klineParamVO);
        Object cached = redisCache.getCacheObject(cacheKey);
        if (cached != null) {
            AjaxResult cachedResult = toAjaxResult(cached);
            if (cachedResult != null) {
                return cachedResult;
            }
        }
        AjaxResult ajax = AjaxResult.success();
        HashMap<String, Object> map = new HashMap<>();
        List<Kline> historyKline;
        Ticker24hVO ticker;
        boolean gateMarket = isGateMarket(klineParamVO);
        try {
            historyKline = blockccService.getHistoryKline(klineParamVO);
            if (historyKline == null) historyKline = new ArrayList<>();
        } catch (Exception e) {
            log.warn("[/kline] historyKline 获取失败 (market={}, symbol={}): {}",
                    klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
            historyKline = new ArrayList<>();
        }
        if (historyKline.isEmpty() && gateMarket) {
            historyKline = getGateHistoryKline(klineParamVO);
        }
        if (!gateMarket) {
            try {
                List<Kline> mappedKline = blockccService.getConPriceMap(klineParamVO, historyKline);
                if (mappedKline != null) historyKline = mappedKline;
            } catch (Exception e) {
                log.debug("[/kline] 控线价差叠加失败，使用原始K线 (market={}, symbol={}): {}",
                        klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
            }
        }
        if (gateMarket) {
            ticker = tickerFromHistory(klineParamVO, historyKline);
        } else {
            try {
                ticker = blockccService.getHistoryKline24hrTicker(klineParamVO);
            } catch (Exception e) {
                log.warn("[/kline] ticker 获取失败 (market={}, symbol={}): {}",
                        klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
                ticker = null;
            }
        }
        if (ticker == null) ticker = new Ticker24hVO();
        map.put("historyKline", historyKline);
        if (!historyKline.isEmpty()) {
            Kline kline = historyKline.get(historyKline.size() - 1);
            try {
                ticker.setHighPrice(new BigDecimal(String.valueOf(kline.getHigh())));
            } catch (Exception e) {
                log.warn("[/kline] highPrice 转换失败 (market={}, symbol={}, high={}): {}",
                        klineParamVO.getMarket(), klineParamVO.getSymbol(), kline.getHigh(), e.toString());
            }
        }
        map.put("ticker", ticker);
        ajax.put("data", map);
        redisCache.setCacheObject(cacheKey, ajax, klineCacheSeconds(klineParamVO), TimeUnit.SECONDS);
        return ajax;
    }

    @PostMapping("/api/market/depth")
    public AjaxResult depth(@RequestBody Map<String, Object> params) {
        String market = normalizeParam(params.get("market"), "binance");
        String symbol = normalizeParam(params.get("symbol"), "");
        int limit = parseLimit(params.get("limit"));
        String cacheKey = "api:market:depth:" + market + ":" + symbol + ":" + limit;
        Object cached = redisCache.getCacheObject(cacheKey);
        if (cached != null) {
            AjaxResult cachedResult = toAjaxResult(cached);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("asks", new ArrayList<>());
        data.put("bids", new ArrayList<>());
        data.put("source", "none");
        data.put("timestamp", System.currentTimeMillis());
        try {
            Map<String, Object> depth = fetchRealDepth(market, symbol, limit);
            if (depth != null) {
                data = depth;
            }
        } catch (Exception e) {
            log.warn("[/api/market/depth] depth failed (market={}, symbol={}): {}", market, symbol, e.toString());
        }
        AjaxResult result = AjaxResult.success(data);
        redisCache.setCacheObject(cacheKey, result, 2, TimeUnit.SECONDS);
        return result;
    }

    private Map<String, Object> fetchRealDepth(String market, String symbol, int limit) {
        if (symbol == null || symbol.isEmpty()) return null;
        if ("gate".equals(market) || "tradfi".equals(market)) {
            return fetchGateDepth(symbol, limit);
        }
        Map<String, Object> binanceDepth = fetchBinanceDepth(symbol, limit);
        if (binanceDepth != null) return binanceDepth;
        return fetchGateDepth(symbol, limit);
    }

    private Map<String, Object> fetchBinanceDepth(String symbol, int limit) {
        String pair = toBinancePair(symbol);
        if (pair == null) return null;
        String body = HttpRequest.get("https://api.binance.com/api/v3/depth?symbol=" + pair + "&limit=" + limit)
                .timeout(3000).execute().body();
        JSONObject json = JSON.parseObject(body);
        return toDepthResult(json.getJSONArray("asks"), json.getJSONArray("bids"), "binance");
    }

    private Map<String, Object> fetchGateDepth(String symbol, int limit) {
        String pair = toGatePair(symbol);
        if (pair == null) return null;
        String body = HttpRequest.get("https://api.gateio.ws/api/v4/spot/order_book?currency_pair=" + pair + "&limit=" + limit)
                .timeout(3000).execute().body();
        JSONObject json = JSON.parseObject(body);
        return toDepthResult(json.getJSONArray("asks"), json.getJSONArray("bids"), "gate");
    }

    private Map<String, Object> toDepthResult(JSONArray asks, JSONArray bids, String source) {
        Map<String, Object> data = new HashMap<>();
        data.put("asks", normalizeDepthRows(asks));
        data.put("bids", normalizeDepthRows(bids));
        data.put("source", source);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    private List<List<BigDecimal>> normalizeDepthRows(JSONArray rows) {
        List<List<BigDecimal>> list = new ArrayList<>();
        if (rows == null) return list;
        for (int i = 0; i < rows.size(); i++) {
            JSONArray row = rows.getJSONArray(i);
            if (row == null || row.size() < 2) continue;
            try {
                List<BigDecimal> item = new ArrayList<>();
                item.add(new BigDecimal(String.valueOf(row.get(0))));
                item.add(new BigDecimal(String.valueOf(row.get(1))));
                list.add(item);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private String buildKlineCacheKey(KlineParamVO p) {
        if (p == null) return "api:market:kline:null";
        return "api:market:kline:"
                + normalizeKey(p.getMarket()) + ":"
                + normalizeKey(p.getSymbol()) + ":"
                + normalizeKey(p.getInterval()) + ":"
                + (p.getEnd() == null ? "latest" : p.getEnd()) + ":"
                + (p.getLimit() == null ? "default" : p.getLimit());
    }

    private int klineCacheSeconds(KlineParamVO p) {
        return p != null && p.getEnd() != null ? 120 : 20;
    }

    private int klineLimit(KlineParamVO p) {
        Integer limit = p == null ? null : p.getLimit();
        if (limit == null) return 120;
        return Math.max(50, Math.min(limit, 1000));
    }

    private boolean isGateMarket(KlineParamVO p) {
        return p != null && "gate".equalsIgnoreCase(p.getMarket());
    }

    private Ticker24hVO tickerFromHistory(KlineParamVO p, List<Kline> historyKline) {
        Ticker24hVO ticker = new Ticker24hVO();
        String base = p == null ? null : normalizeBaseSymbol(p.getSymbol());
        if (base != null) {
            ticker.setSymbol(base + "USDT");
        } else if (p != null) {
            ticker.setSymbol(p.getSymbol());
        }
        if (historyKline == null || historyKline.isEmpty()) {
            return ticker;
        }
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal volume = BigDecimal.ZERO;
        for (Kline kline : historyKline) {
            if (kline == null) continue;
            if (kline.getHigh() != null) {
                BigDecimal value = BigDecimal.valueOf(kline.getHigh());
                high = high == null ? value : high.max(value);
            }
            if (kline.getLow() != null) {
                BigDecimal value = BigDecimal.valueOf(kline.getLow());
                low = low == null ? value : low.min(value);
            }
            if (kline.getVolume() != null) {
                volume = volume.add(BigDecimal.valueOf(kline.getVolume()));
            }
        }
        ticker.setHighPrice(high);
        ticker.setLowPrice(low);
        ticker.setVolume(volume);
        return ticker;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeParam(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? fallback : text;
    }

    private int parseLimit(Object value) {
        try {
            int limit = Integer.parseInt(String.valueOf(value));
            return Math.max(5, Math.min(limit, 20));
        } catch (Exception e) {
            return 10;
        }
    }

    private String toBinancePair(String symbol) {
        String base = normalizeBaseSymbol(symbol);
        return base == null ? null : base + "USDT";
    }

    private AjaxResult toAjaxResult(Object cached) {
        if (cached instanceof AjaxResult) {
            return (AjaxResult) cached;
        }
        if (cached instanceof Map) {
            AjaxResult result = new AjaxResult();
            result.putAll((Map<? extends String, ?>) cached);
            return result;
        }
        return null;
    }

    private List<Kline> getGateHistoryKline(KlineParamVO klineParamVO) {
        List<Kline> list = new ArrayList<>();
        String pair = toGatePair(klineParamVO.getSymbol());
        if (pair == null) return list;
        String interval = toGateInterval(klineParamVO.getInterval());
        StringBuilder url = new StringBuilder("https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=")
                .append(pair)
                .append("&interval=").append(interval)
                .append("&limit=").append(klineLimit(klineParamVO));
        if (klineParamVO.getEnd() != null) {
            url.append("&to=").append(klineParamVO.getEnd() / 1000L);
        }
        try {
            String body = HttpRequest.get(url.toString()).timeout(15000).execute().body();
            if (body == null || body.isEmpty() || body.charAt(0) != '[') return list;
            JSONArray rows = JSON.parseArray(body);
            for (int i = 0; i < rows.size(); i++) {
                JSONArray row = rows.getJSONArray(i);
                if (row == null || row.size() < 6) continue;
                Kline kline = new Kline();
                kline.setTimestamp(Long.parseLong(String.valueOf(row.get(0))) * 1000L);
                kline.setClose(Double.parseDouble(String.valueOf(row.get(2))));
                kline.setHigh(Double.parseDouble(String.valueOf(row.get(3))));
                kline.setLow(Double.parseDouble(String.valueOf(row.get(4))));
                kline.setOpen(Double.parseDouble(String.valueOf(row.get(5))));
                double volume = row.size() >= 7 ? Double.parseDouble(String.valueOf(row.get(6))) : 0d;
                kline.setVolume(volume);
                list.add(kline);
            }
        } catch (Exception e) {
            log.warn("[/kline] Gate history fallback failed (symbol={}): {}", klineParamVO.getSymbol(), e.toString());
        }
        return list;
    }

    private static String toGatePair(String symbol) {
        String base = normalizeBaseSymbol(symbol);
        return base == null ? null : base + "_USDT";
    }

    private static String normalizeBaseSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return null;
        String value = symbol.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('/', '_');
        if (value.endsWith("_USDT")) {
            value = value.substring(0, value.length() - 5);
        } else if (value.endsWith("USDT") && value.length() > 4) {
            value = value.substring(0, value.length() - 4);
        }
        value = value.replace("_", "");
        return value.isEmpty() ? null : value;
    }

    private static String toGateInterval(String interval) {
        if (interval == null || interval.trim().isEmpty()) return "1m";
        switch (interval) {
            case "1":
            case "1m":
            case "ONE_MIN":
                return "1m";
            case "5":
            case "5m":
            case "FIVE_MIN":
                return "5m";
            case "15":
            case "15m":
            case "FIFTEEN_MIN":
                return "15m";
            case "30":
            case "30m":
            case "THIRTY_MIN":
                return "30m";
            case "60":
            case "1h":
            case "ONE_HOUR":
                return "1h";
            case "120":
            case "2h":
            case "TWO_HOUR":
                return "2h";
            case "360":
            case "6h":
            case "SIX_HOUR":
                return "8h";
            case "1d":
            case "ONE_DAY":
            case "TWO_DAY":
                return "1d";
            case "W":
            case "1w":
            case "ONE_WEEK":
            case "SEVEN_DAY":
                return "7d";
            case "M":
            case "1M":
            case "ONE_MON":
                return "30d";
            default:
                return "1m";
        }
    }

    /**
     * 封装缩减k线数据
     *
     * @param list 入参数组list
     * @return 结果
     */
      @PostMapping({"/newKline", "/api/newKline"})
    public AjaxResult new_kline(@RequestBody List<KlineParamVO> list) {
        AjaxResult ajax = AjaxResult.success();
        List<Map> re=new ArrayList<>();
        for (KlineParamVO paramVO : list) {
            HashMap<String, Object> map = new HashMap<>();
            List<Kline> historyKline = blockccService.getHistoryKline2(paramVO);
            historyKline = blockccService.getConPriceMap(paramVO,historyKline);
            Ticker24hVO ticker =  blockccService.getHistoryKline24hrTicker(paramVO);
            map.put("historyKline",historyKline);
            map.put("ticker",ticker);
            re.add(map);
        }
        ajax.put("data", re);
        return ajax;
    }
}
