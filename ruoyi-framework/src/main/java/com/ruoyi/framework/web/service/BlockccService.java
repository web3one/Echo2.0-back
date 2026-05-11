package com.ruoyi.framework.web.service;

import cc.block.data.api.domain.enumeration.Interval;
import cc.block.data.api.domain.market.Kline;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;
import com.huobi.api.request.account.SwapMarketHistoryKlineRequest;
import com.huobi.api.response.market.SwapMarketHistoryKlineResponse;
import com.huobi.api.service.market.MarketAPIServiceImpl;
import com.ruoyi.bussiness.domain.KlineSymbol;
import com.ruoyi.bussiness.domain.TBotKlineModel;
import com.ruoyi.bussiness.service.IKlineSymbolService;
import com.ruoyi.bussiness.service.ITBotKlineModelInfoService;
import com.ruoyi.bussiness.service.ITBotKlineModelService;
import com.ruoyi.bussiness.service.ITOwnCoinService;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.CachePrefix;
import com.ruoyi.common.enums.CandlestickIntervalEnum;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.domain.KlineParamVO;
import com.ruoyi.framework.web.domain.Ticker24hVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
public class BlockccService {
    @Autowired
    private ITBotKlineModelInfoService botKlineModelInfoService;
    @Autowired
    private ITBotKlineModelService botKlineModelService;
    @Resource
    private ITOwnCoinService itOwnCoinService;
    @Resource
    private IKlineSymbolService klineSymbolService;
    @Resource
    private RedisCache redisCache;

    public List<Kline> getHistoryKline(KlineParamVO klineParam) {
        try {


        String market = StringUtils.isBlank(klineParam.getMarket()) ? "" : klineParam.getMarket();
        String timeCode = this.getMt5Time(klineParam.getInterval());
        String symbol = klineParam.getSymbol();
        String baseSymbol = normalizeBaseSymbol(symbol);
        switch (market) {
            case "gate": {
                // Gate 现货 REST 历史 K 线：公开免鉴权
                // 接口：GET https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=BTC_USDT&interval=1m&limit=1000&to=...(秒)
                String pair = toGatePair(symbol);
                String gateInterval = toGateInterval(klineParam.getInterval());
                if (pair == null || gateInterval == null) {
                    return new ArrayList<>();
                }
                StringBuilder url = new StringBuilder("https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=")
                        .append(pair)
                        .append("&interval=").append(gateInterval)
                        .append("&limit=1000");
                if (klineParam.getEnd() != null) {
                    url.append("&to=").append(klineParam.getEnd() / 1000L);
                }
                String body = HttpRequest.get(url.toString())
                        .timeout(15000)
                        .execute().body();
                List<Kline> his = parseGateSpotCandles(body);
                return applyBotLineList(baseSymbol, his, klineParam.getInterval());
            }
            case "binance": {
                //后续加上控线逻辑
                Map<String, Object> parameters = new LinkedHashMap<>();
                SpotClient client = new SpotClientImpl();
                parameters.put("symbol", toUsdtPairNoSep(symbol));
                Interval interval = Interval.valueOf(klineParam.getInterval());
                parameters.put("interval", interval.toString());
                if (klineParam.getEnd() != null) {
                    parameters.put("endTime", klineParam.getEnd());
                }
                log.debug("参数" + JSONObject.toJSONString(parameters));
                String result = client.createMarket().klines(parameters);
                List<Kline> his = new ArrayList<>();
                JSONArray parse = JSONArray.parse(result);
                for (int i = 0; i < parse.size(); i++) {
                    JSONArray jsonObject = parse.getJSONArray(i);
                    Object[] array = jsonObject.toArray();
                    Kline kline = new Kline();
                    kline.setTimestamp((Long) array[0]);
                    kline.setOpen(Double.parseDouble((String) array[1]));
                    kline.setHigh(Double.parseDouble((String) array[2]));
                    kline.setLow(Double.parseDouble((String) array[3]));
                    kline.setClose(Double.parseDouble((String) array[4]));
                    kline.setVolume(Double.parseDouble((String) array[5]));
                    his.add(kline);
                }
                his = applyBotLineList(baseSymbol, his, klineParam.getInterval());

                return his;
            }
            case "huobi": {
                MarketAPIServiceImpl huobiAPIService = new MarketAPIServiceImpl();
                SwapMarketHistoryKlineRequest result = SwapMarketHistoryKlineRequest.builder()
                        .contractCode((baseSymbol == null ? symbol.toUpperCase() : baseSymbol) + "-USDT")//合约代码	"BTC-USDT" ...
                        .period(CandlestickIntervalEnum.getValue(klineParam.getInterval())) //K线类型	1min, 5min, 15min, 30min, 60min,4hour,1day,1week,1mon
                        .size(1000) //获取数量，默认150	[1,2000]
                        //.from() //开始时间戳 10位 单位S
                        //.to();//结束时间戳 10位 单位S
                        .build();
                List<Kline> his = new ArrayList<>();
                SwapMarketHistoryKlineResponse response = huobiAPIService.getSwapMarketHistoryKline(result);
                if ("ok".equalsIgnoreCase(response.getStatus())) {
                    List<SwapMarketHistoryKlineResponse.DataBean> list = response.getData();
                    for (SwapMarketHistoryKlineResponse.DataBean data : list) {
                        Kline kline = new Kline();
                        kline.setTimestamp(data.getId());
                        kline.setOpen(data.getOpen().doubleValue());
                        kline.setHigh(data.getHigh().doubleValue());
                        kline.setLow(data.getLow().doubleValue());
                        kline.setClose(data.getClose().doubleValue());
                        kline.setVolume(data.getVol().doubleValue());
                        his.add(kline);
                    }
                }
                his = applyBotLineList(baseSymbol, his, klineParam.getInterval());
                return his;
            }
            case "echo": {
                //后续加上控线逻辑
                Map<String, Object> parameters = new LinkedHashMap<>();
                SpotClient client = new SpotClientImpl();
                KlineSymbol one = klineSymbolService.getOne(new LambdaQueryWrapper<KlineSymbol>().eq(KlineSymbol::getSymbol, symbol.toLowerCase()));
                if (null == one) {
                    return null;
                }
                parameters.put("symbol", one.getReferCoin().toUpperCase() + "USDT");
                Interval interval = Interval.valueOf(klineParam.getInterval());
                parameters.put("interval", interval.toString());
                if (klineParam.getEnd() != null) {
                    parameters.put("endTime", klineParam.getEnd());
                }
                log.debug("参数" + JSONObject.toJSONString(parameters));
                String result = client.createMarket().klines(parameters);
                List<Kline> his = new ArrayList<>();
                JSONArray parse = JSONArray.parse(result);
                for (int i = 0; i < parse.size(); i++) {
                    JSONArray jsonObject = parse.getJSONArray(i);
                    Object[] array = jsonObject.toArray();
                    Kline kline = new Kline();
                    kline.setTimestamp((Long) array[0]);
                    kline.setOpen(Double.parseDouble((String) array[1]));
                    kline.setHigh(Double.parseDouble((String) array[2]));
                    kline.setLow(Double.parseDouble((String) array[3]));
                    kline.setClose(Double.parseDouble((String) array[4]));
                    kline.setVolume(Double.parseDouble((String) array[5]));
                    his.add(kline);
                }
                his = itOwnCoinService.selectLineList(one, his);
                his = applyBotLineList(baseSymbol, his, klineParam.getInterval());
                return his;
            }
            case "energy": {
                Random random = new Random();
                double v = random.nextDouble();
                String url = "https://api-q.fx678img.com/histories.php?symbol="
                        + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=5700&st=" + v;
                String result = HttpRequest.get(url)
                        .header("Referer", "https://quote.fx678.com/")
                        .header("Host", "api-q.fx678img.com")
                        .header("Origin", "https://quote.fx678.com")
                        .timeout(20000)
                        .execute().body();
                JSONObject ret = JSONObject.parseObject(result);
                List<Kline> klines = buildHisKline(ret);
                return botKlineModelInfoService.selectBotLineList(symbol.toLowerCase(), klines, klineParam.getInterval());
            }
            default: {
                Random random = new Random();
                double v = random.nextDouble();
                String url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=8100&st=" + v;
                String result = HttpRequest.get(url)
                        .header("referer", "https://quote.fx678.com/")
                        .timeout(10000)
                        .execute().body();
                JSONObject ret = JSONObject.parseObject(result);
                List<Kline> klines = buildHisKline(ret);
                if (klines.isEmpty()) {
                    url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=8200&st=" + v;
                    result = HttpRequest.get(url)
                            .header("referer", "https://quote.fx678.com/")
                            .timeout(10000)
                            .execute().body();
                }
                ret = JSONObject.parseObject(result);
                klines = buildHisKline(ret);
                if (klines.isEmpty()) {
                    url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=5c00&st=" + v;
                    result = HttpRequest.get(url)
                            .header("referer", "https://quote.fx678.com/")
                            .timeout(10000)
                            .execute().body();
                }
                ret = JSONObject.parseObject(result);
                klines = buildHisKline(ret);
                return botKlineModelInfoService.selectBotLineList(symbol.toLowerCase(), klines, klineParam.getInterval());
            }
        }
        }catch (Exception e){
            log.info(e.toString());
        }
        return null;
    }

    public List<Kline> getHistoryKline2(KlineParamVO klineParam) {
        String market = StringUtils.isBlank(klineParam.getMarket()) ? "" : klineParam.getMarket();
        String timeCode = this.getMt5Time(klineParam.getInterval());
        String symbol = klineParam.getSymbol();
        switch (market) {
            case "binance": {
                //后续加上控线逻辑
                Map<String, Object> parameters = new LinkedHashMap<>();
                SpotClient client = new SpotClientImpl();
                parameters.put("symbol", symbol.toUpperCase() + "USDT");
                Interval interval = Interval.valueOf(klineParam.getInterval());
                parameters.put("interval", interval.toString());
                parameters.put("limit",50);
                if (klineParam.getEnd() != null) {
                    parameters.put("endTime", klineParam.getEnd());
                }
                log.debug("参数" + JSONObject.toJSONString(parameters));
                String result = client.createMarket().klines(parameters);
                List<Kline> his = new ArrayList<>();
                JSONArray parse = JSONArray.parse(result);
                for (int i = 0; i < parse.size(); i++) {
                    JSONArray jsonObject = parse.getJSONArray(i);
                    Object[] array = jsonObject.toArray();
                    Kline kline = new Kline();
                    kline.setTimestamp((Long) array[0]);
                    kline.setOpen(Double.parseDouble((String) array[1]));
                    kline.setHigh(Double.parseDouble((String) array[2]));
                    kline.setLow(Double.parseDouble((String) array[3]));
                    kline.setClose(Double.parseDouble((String) array[4]));
                    kline.setVolume(Double.parseDouble((String) array[5]));
                    his.add(kline);
                }
                his = botKlineModelInfoService.selectBotLineList(symbol.toLowerCase() + "usdt", his, klineParam.getInterval());

                return his;
            }
            case "huobi": {
                MarketAPIServiceImpl huobiAPIService = new MarketAPIServiceImpl();
                SwapMarketHistoryKlineRequest result = SwapMarketHistoryKlineRequest.builder()
                        .contractCode(symbol.toUpperCase() + "-USDT")//合约代码	"BTC-USDT" ...
                        .period(CandlestickIntervalEnum.getValue(klineParam.getInterval())) //K线类型	1min, 5min, 15min, 30min, 60min,4hour,1day,1week,1mon
                        .size(1000) //获取数量，默认150	[1,2000]
                        //.from() //开始时间戳 10位 单位S
                        //.to();//结束时间戳 10位 单位S
                        .build();
                List<Kline> his = new ArrayList<>();
                SwapMarketHistoryKlineResponse response = huobiAPIService.getSwapMarketHistoryKline(result);
                if ("ok".equalsIgnoreCase(response.getStatus())) {
                    List<SwapMarketHistoryKlineResponse.DataBean> list = response.getData();
                    for (SwapMarketHistoryKlineResponse.DataBean data : list) {
                        Kline kline = new Kline();
                        kline.setTimestamp(data.getId());
                        kline.setOpen(data.getOpen().doubleValue());
                        kline.setHigh(data.getHigh().doubleValue());
                        kline.setLow(data.getLow().doubleValue());
                        kline.setClose(data.getClose().doubleValue());
                        kline.setVolume(data.getVol().doubleValue());
                        his.add(kline);
                    }
                }
                his = botKlineModelInfoService.selectBotLineList(symbol.toLowerCase() + "usdt", his, klineParam.getInterval());
                return his;
            }
            case "echo": {
                //后续加上控线逻辑
                Map<String, Object> parameters = new LinkedHashMap<>();
                SpotClient client = new SpotClientImpl();
                KlineSymbol one = klineSymbolService.getOne(new LambdaQueryWrapper<KlineSymbol>().eq(KlineSymbol::getSymbol, symbol.toLowerCase()));
                if (null == one) {
                    return null;
                }
                parameters.put("symbol", one.getReferCoin().toUpperCase() + "USDT");
                Interval interval = Interval.valueOf(klineParam.getInterval());
                parameters.put("interval", interval.toString());
                if (klineParam.getEnd() != null) {
                    parameters.put("endTime", klineParam.getEnd());
                }
                log.debug("参数" + JSONObject.toJSONString(parameters));
                String result = client.createMarket().klines(parameters);
                List<Kline> his = new ArrayList<>();
                JSONArray parse = JSONArray.parse(result);
                for (int i = 0; i < parse.size(); i++) {
                    JSONArray jsonObject = parse.getJSONArray(i);
                    Object[] array = jsonObject.toArray();
                    Kline kline = new Kline();
                    kline.setTimestamp((Long) array[0]);
                    kline.setOpen(Double.parseDouble((String) array[1]));
                    kline.setHigh(Double.parseDouble((String) array[2]));
                    kline.setLow(Double.parseDouble((String) array[3]));
                    kline.setClose(Double.parseDouble((String) array[4]));
                    kline.setVolume(Double.parseDouble((String) array[5]));
                    his.add(kline);
                }
                his = itOwnCoinService.selectLineList(one, his);
                his = botKlineModelInfoService.selectBotLineList(symbol.toLowerCase() + "usdt", his, klineParam.getInterval());
                return his;
            }
            case "energy": {
                Random random = new Random();
                double v = random.nextDouble();
                String url = "https://api-q.fx678img.com/histories.php?symbol="
                        + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=5700&st=" + v;
                String result = HttpRequest.get(url)
                        .header("Referer", "https://quote.fx678.com/")
                        .header("Host", "api-q.fx678img.com")
                        .header("Origin", "https://quote.fx678.com")
                        .timeout(20000)
                        .execute().body();
                JSONObject ret = JSONObject.parseObject(result);
                List<Kline> klines = buildHisKline(ret);
                return botKlineModelInfoService.selectBotLineList(symbol.toLowerCase(), klines, klineParam.getInterval());
            }
            default: {
                Random random = new Random();
                double v = random.nextDouble();
                String url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=8100&st=" + v;
                String result = HttpRequest.get(url)
                        .header("referer", "https://quote.fx678.com/")
                        .timeout(10000)
                        .execute().body();
                JSONObject ret = JSONObject.parseObject(result);
                List<Kline> klines = buildHisKline(ret);
                if (klines.isEmpty()) {
                    url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=8200&st=" + v;
                    result = HttpRequest.get(url)
                            .header("referer", "https://quote.fx678.com/")
                            .timeout(10000)
                            .execute().body();
                }
                ret = JSONObject.parseObject(result);
                klines = buildHisKline(ret);
                if (klines.isEmpty()) {
                    url = "https://api-q.fx678img.com/histories.php?symbol=" + symbol.toUpperCase() + "&limit=" + 1000 + "&resolution=" + timeCode + "&codeType=5c00&st=" + v;
                    result = HttpRequest.get(url)
                            .header("referer", "https://quote.fx678.com/")
                            .timeout(10000)
                            .execute().body();
                }
                ret = JSONObject.parseObject(result);
                klines = buildHisKline(ret);
                return botKlineModelInfoService.selectBotLineList(symbol.toLowerCase(), klines, klineParam.getInterval());
            }
        }
    }

    /** 兼容 "btc"、"BTC/USDT"、"BTC_USDT"、"BTCUSDT"，统一取基础币种。 */
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

    private List<Kline> applyBotLineList(String baseSymbol, List<Kline> his, String interval) {
        if (his == null) return new ArrayList<>();
        if (baseSymbol == null) return his;
        try {
            List<Kline> adjusted = botKlineModelInfoService.selectBotLineList(baseSymbol.toLowerCase() + "usdt", his, interval);
            return adjusted == null ? his : adjusted;
        } catch (Exception e) {
            log.warn("[Kline] 控线叠加失败，使用原始K线 (symbol={}, interval={}): {}", baseSymbol, interval, e.toString());
            return his;
        }
    }

    /** 内部 symbol → 无分隔 USDT 交易对（"BTCUSDT"） */
    private static String toUsdtPairNoSep(String symbol) {
        String base = normalizeBaseSymbol(symbol);
        return (base == null ? symbol.toUpperCase(Locale.ROOT) : base) + "USDT";
    }

    /** 内部 symbol → Gate 现货 pair（"BTC_USDT"） */
    private static String toGatePair(String symbol) {
        String base = normalizeBaseSymbol(symbol);
        if (base == null) return null;
        return base + "_USDT";
    }

    /** 项目内 Interval 枚举名 → Gate REST interval 字符串。Gate 不支持的周期返回 null。 */
    private static String toGateInterval(String interval) {
        if (interval == null || interval.trim().isEmpty()) return "1m";
        switch (interval) {
            case "1":
            case "1m":          return "1m";
            case "5":
            case "5m":          return "5m";
            case "15":
            case "15m":         return "15m";
            case "30":
            case "30m":         return "30m";
            case "60":
            case "1h":          return "1h";
            case "120":
            case "2h":          return "2h";
            case "360":
            case "6h":          return "8h";
            case "1d":          return "1d";
            case "W":
            case "1w":          return "7d";
            case "M":
            case "1M":          return "30d";
            case "ONE_MIN":     return "1m";
            case "FIVE_MIN":    return "5m";
            case "FIFTEEN_MIN": return "15m";
            case "THIRTY_MIN":  return "30m";
            case "ONE_HOUR":    return "1h";
            case "TWO_HOUR":    return "2h";
            case "FOUR_HOUR":   return "4h";
            case "SIX_HOUR":    return "8h";   // Gate 不直接给 6h，用 8h 兜底
            case "ONE_DAY":
            case "TWO_DAY":     return "1d";
            case "SEVEN_DAY":   return "7d";
            case "ONE_WEEK":    return "7d";
            case "ONE_MON":     return "30d";
            default:            return null;
        }
    }

    /**
     * Gate 现货 candlesticks REST 响应解析。
     * 响应是二维数组：[ [ts_sec_str, quote_vol, close, high, low, open, base_vol, finished?], ... ]
     */
    private static List<Kline> parseGateSpotCandles(String body) {
        List<Kline> his = new ArrayList<>();
        if (body == null || body.isEmpty() || body.charAt(0) != '[') return his;
        com.alibaba.fastjson.JSONArray rows = JSON.parseArray(body);
        int skipped = 0;
        String firstError = null;
        for (int i = 0; i < rows.size(); i++) {
            com.alibaba.fastjson.JSONArray r = rows.getJSONArray(i);
            if (r == null || r.size() < 6) {
                skipped++;
                continue;
            }
            try {
                Kline kline = new Kline();
                kline.setTimestamp(Long.parseLong(String.valueOf(r.get(0))) * 1000L);
                kline.setClose(Double.parseDouble(String.valueOf(r.get(2))));
                kline.setHigh(Double.parseDouble(String.valueOf(r.get(3))));
                kline.setLow(Double.parseDouble(String.valueOf(r.get(4))));
                kline.setOpen(Double.parseDouble(String.valueOf(r.get(5))));
                double vol = r.size() >= 7 ? Double.parseDouble(String.valueOf(r.get(6))) : 0d;
                kline.setVolume(vol);
                his.add(kline);
            } catch (Exception e) {
                skipped++;
                if (firstError == null) {
                    firstError = e.toString();
                }
                // 单根异常不影响整体
            }
        }
        if (his.isEmpty() && rows != null && !rows.isEmpty()) {
            log.warn("[Gate] K线解析为空 rows={}, skipped={}, firstError={}, bodyHead={}",
                    rows.size(), skipped, firstError, body.substring(0, Math.min(120, body.length())));
        }
        return his;
    }

    private String getMt5Time(String time) {
        //["ONE_MIN","FIVE_MIN","FIFTEEN_MIN","THIRTY_MIN","ONE_HOUR","TWO_HOUR","SIX_HOUR","ONE_DAY","TWO_DAY","SEVEN_DAY"]
        switch (time) {
            case "ONE_MIN":
                return "1";
            case "FIVE_MIN":
                return "5";
            case "FIFTEEN_MIN":
                return "15";
            case "THIRTY_MIN":
                return "30";
            case "ONE_HOUR":
                return "60";
            case "ONE_DAY":
                return "D";
            case "SEVEN_DAY":
                return "W";
            default:
                return "";
        }
    }

    private List<Kline> buildHisKline(JSONObject ret) {
        List<Kline> klines = new ArrayList<>();
        String s = ret.getString("s");
        if ("ok".equals(s)) {
            JSONArray close = ret.getJSONArray("c");
            JSONArray high = ret.getJSONArray("h");
            JSONArray low = ret.getJSONArray("l");
            JSONArray open = ret.getJSONArray("o");
            JSONArray volume = ret.getJSONArray("v");
            JSONArray timestamp = ret.getJSONArray("t");
            Kline kline;
            for (int i = 0; i < close.size(); i++) {
                kline = new Kline();
                kline.setClose(close.getDouble(i));
                kline.setOpen(open.getDouble(i));
                kline.setHigh(high.getDouble(i));
                kline.setLow(low.getDouble(i));
                kline.setVolume(volume.getDouble(i));
                kline.setTimestamp((timestamp.getLong(i)) * 1000L);
                klines.add(kline);
            }
        }
        return klines;
    }

    public Ticker24hVO getHistoryKline24hrTicker(KlineParamVO klineParamVO) {
        String market = klineParamVO.getMarket();
        if("metal".equals(market) || "mt5".equals(market) || "energy".equals(market)){
            Ticker24hVO ticker24hVO = new Ticker24hVO();
            ticker24hVO.setSymbol(klineParamVO.getSymbol());
            BigDecimal cacheObject = safeBd(redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + klineParamVO.getSymbol()));
            ticker24hVO.setHighPrice(cacheObject);
            ticker24hVO.setLowPrice(cacheObject);
            Random random = new Random();

            double randomValue = 0 + (1000 - 0) * random.nextDouble();
            ticker24hVO.setVolume(new BigDecimal(randomValue));
            return ticker24hVO;
        }
        // Gate 现货 24hr ticker：跟 K 线同源，公开免鉴权
        if ("gate".equals(market)) {
            try {
                String pair = toGatePair(klineParamVO.getSymbol());
                if (pair == null) return new Ticker24hVO();
                String body = HttpRequest.get("https://api.gateio.ws/api/v4/spot/tickers?currency_pair=" + pair)
                        .timeout(10000)
                        .execute().body();
                JSONArray arr = JSONArray.parse(body);
                Ticker24hVO vo = new Ticker24hVO();
                String base = normalizeBaseSymbol(klineParamVO.getSymbol());
                vo.setSymbol((base == null ? klineParamVO.getSymbol().toUpperCase(Locale.ROOT) : base) + "USDT");
                if (arr != null && !arr.isEmpty()) {
                    com.alibaba.fastjson2.JSONObject t = arr.getJSONObject(0);
                    vo.setHighPrice(safeBd(t.getString("high_24h")));
                    vo.setLowPrice(safeBd(t.getString("low_24h")));
                    vo.setVolume(safeBd(t.getString("base_volume")));
                }
                return vo;
            } catch (Exception e) {
                log.warn("[Gate] ticker24h 失败: {}", e.toString());
                return new Ticker24hVO();
            }
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        SpotClient client = new SpotClientImpl();
        parameters.put("symbol", toUsdtPairNoSep(klineParamVO.getSymbol()));
        if("echo".equals(market)){
            KlineSymbol one = klineSymbolService.getOne(new LambdaQueryWrapper<KlineSymbol>().eq(KlineSymbol::getSymbol, klineParamVO.getSymbol().toLowerCase()));
            if(null == one){
                return null;
            }
            parameters.put("symbol", one.getReferCoin().toUpperCase() + "USDT");
        }
        try {
            String result = client.createMarket().ticker24H(parameters);
            return JSON.parseObject(result, Ticker24hVO.class);
        } catch (Exception e) {
            log.warn("[Binance] ticker24h 失败 (market={}): {}", market, e.toString());
            return new Ticker24hVO();
        }
    }

    private static BigDecimal safeBd(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private static BigDecimal safeBd(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    public Ticker24hVO getHistoryKline24hrTicker2(KlineParamVO klineParamVO) {
        //遍历传来的market参数数组
        Object marketValue = klineParamVO.getMarkets();
        // 如果 marketValue 是字符串数组，遍历数组元素执行条件判断
        String[] marketArray = (String[]) marketValue;
        List<Kline> dataList = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();
        SpotClient client = new SpotClientImpl();
        //循环遍历所有数据来执行以下操作
        for (String market : marketArray) {
            Object symbolValue = klineParamVO.getSymbols();
            // 如果 symbolValue 是字符串数组，遍历数组元素执行条件判断
            String[] symbolArray = (String[]) symbolValue;
            for (String symbol : symbolArray) {
                if (market.equals("metal") || market.equals("mt5")) {
                    Ticker24hVO ticker24hVO = new Ticker24hVO();

                    ticker24hVO.setSymbol(symbol);
                    BigDecimal cacheObject = safeBd(redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + symbol));
                    ticker24hVO.setHighPrice(cacheObject);
                    ticker24hVO.setLowPrice(cacheObject);
                    Random random = new Random();
                    double randomValue = 0 + (1000 - 0) * random.nextDouble();
                    ticker24hVO.setVolume(new BigDecimal(randomValue));
                    return ticker24hVO;
                }
                parameters.put("symbol", toUsdtPairNoSep(symbol));
                if (market.equals("echo")) {
                    KlineSymbol one = klineSymbolService.getOne(new LambdaQueryWrapper<KlineSymbol>().eq(KlineSymbol::getSymbol, symbol.toLowerCase()));
                    if (null == one) {
                        return null;
                    }
                    parameters.put("symbol", one.getReferCoin().toUpperCase() + "USDT");
                }
            }
        }
        String result = client.createMarket().ticker24H(parameters);
        Ticker24hVO ticker24hVO = JSON.parseObject(result, Ticker24hVO.class);
        return ticker24hVO;
    }

    public List<Kline>  getConPriceMap(KlineParamVO klineParamVO,  List<Kline> historyKline){
        if (historyKline == null || historyKline.isEmpty()) {
            return historyKline == null ? new ArrayList<>() : historyKline;
        }
        TBotKlineModel tBotKlineModel = new TBotKlineModel();
        String baseSymbol = normalizeBaseSymbol(klineParamVO.getSymbol());
        tBotKlineModel.setSymbol((baseSymbol == null ? klineParamVO.getSymbol() : baseSymbol.toLowerCase()) + "usdt");
        tBotKlineModel.setModel(0L);
        List<TBotKlineModel> tBotKlineModels = botKlineModelService.selectTBotKlineModelList(tBotKlineModel);
        if (tBotKlineModels == null || tBotKlineModels.isEmpty()) {
            return historyKline;
        }
        BigDecimal cc = new BigDecimal(0);
        int num = 0;
        for (TBotKlineModel tBotKlineModel1: tBotKlineModels ) {
            if (tBotKlineModel1 == null || tBotKlineModel1.getConPrice() == null || tBotKlineModel1.getBeginTime() == null) {
                continue;
            }
            cc=cc.add( tBotKlineModel1.getConPrice());
            boolean isF = true;
            int a = 0;
            long time = tBotKlineModel1.getBeginTime().getTime();
            for (Kline kline: historyKline) {
                if (kline == null || kline.getTimestamp() == null || kline.getOpen() == null
                        || kline.getHigh() == null || kline.getLow() == null || kline.getClose() == null) {
                    continue;
                }

                    if(kline.getTimestamp()>=time){
                        if(isF){
                            double c = kline.getClose() + tBotKlineModel1.getConPrice().doubleValue();
                            double h = kline.getHigh() + tBotKlineModel1.getConPrice().doubleValue();
                            double l = kline.getLow() + tBotKlineModel1.getConPrice().doubleValue();
                            kline.setClose(c);
                            if(h>kline.getHigh()){
                                kline.setHigh(h);
                            }
                            if(l<kline.getLow()){
                                kline.setLow(l);
                            }
                            isF=false;
                        }else {
                            double c = kline.getClose() + tBotKlineModel1.getConPrice().doubleValue();
                            double h = kline.getHigh() + tBotKlineModel1.getConPrice().doubleValue();
                            double l = kline.getLow() + tBotKlineModel1.getConPrice().doubleValue();
                            double o = kline.getOpen() + tBotKlineModel1.getConPrice().doubleValue();
                            kline.setClose(c);
                            kline.setHigh(h);
                            kline.setLow(l);
                            kline.setOpen(o);
                        }
                    }

                a++;
                if(a==historyKline.size()){
                    BigDecimal cacheObject = safeBd(redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + (baseSymbol == null ? klineParamVO.getSymbol().replace("usdt", "").toLowerCase() : baseSymbol.toLowerCase())));
                    if (cacheObject != null) {
                        kline.setClose(cacheObject.doubleValue());
                    }
                }

            }

            num++;
        }
        return historyKline;
    }

    public List<Kline>  getConPriceMap2(KlineParamVO klineParamVO,  List<Kline> historyKline){
        TBotKlineModel tBotKlineModel = new TBotKlineModel();
        //遍历币种数组
        Object symbolValue = klineParamVO.getSymbols();
        // 如果 symbolValue 是字符串数组，遍历数组元素执行条件判断
        String[] symbolArray = (String[]) symbolValue;
        for (String symbol : symbolArray) {
            tBotKlineModel.setSymbol(symbol + "usdt");
            tBotKlineModel.setModel(0L);
            List<TBotKlineModel> tBotKlineModels = botKlineModelService.selectTBotKlineModelList(tBotKlineModel);
            BigDecimal cc = new BigDecimal(0);
            int num = 0;
            for (TBotKlineModel tBotKlineModel1 : tBotKlineModels) {
                cc = cc.add(tBotKlineModel1.getConPrice());
                boolean isF = true;
                int a = 0;
                long time = tBotKlineModel1.getBeginTime().getTime();
                for (Kline kline : historyKline) {

                    if (kline.getTimestamp() >= time) {
                        if (isF) {
                            double c = kline.getClose() + tBotKlineModel1.getConPrice().doubleValue();
                            double h = kline.getHigh() + tBotKlineModel1.getConPrice().doubleValue();
                            double l = kline.getLow() + tBotKlineModel1.getConPrice().doubleValue();
                            kline.setClose(c);
                            if (h > kline.getHigh()) {
                                kline.setHigh(h);
                            }
                            if (l < kline.getLow()) {
                                kline.setLow(l);
                            }
                            isF = false;
                        } else {
                            double c = kline.getClose() + tBotKlineModel1.getConPrice().doubleValue();
                            double h = kline.getHigh() + tBotKlineModel1.getConPrice().doubleValue();
                            double l = kline.getLow() + tBotKlineModel1.getConPrice().doubleValue();
                            double o = kline.getOpen() + tBotKlineModel1.getConPrice().doubleValue();
                            kline.setClose(c);
                            kline.setHigh(h);
                            kline.setLow(l);
                            kline.setOpen(o);
                        }
                    }

                    a++;
                    if (a == historyKline.size()) {
                        BigDecimal cacheObject = redisCache.getCacheObject(CachePrefix.CURRENCY_PRICE.getPrefix() + symbol.replace("usdt", "").toLowerCase());
                        kline.setClose(cacheObject.doubleValue());
                    }

                }

                num++;
            }
        }
        return historyKline;
    }
}
