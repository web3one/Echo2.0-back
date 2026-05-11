package com.ruoyi.web.controller.blockcc;

import cc.block.data.api.domain.market.Kline;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.ruoyi.common.core.domain.AjaxResult;
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

    /**
     * 登录历史k线
     *
     * @param klineParamVO 入参
     * @return 结果
     */
    @PostMapping({"/kline", "/api/kline"})
    public AjaxResult kline(@RequestBody KlineParamVO klineParamVO) {
        AjaxResult ajax = AjaxResult.success();
        HashMap<String, Object> map = new HashMap<>();
        List<Kline> historyKline;
        Ticker24hVO ticker;
        try {
            historyKline = blockccService.getHistoryKline(klineParamVO);
            if (historyKline == null) historyKline = new ArrayList<>();
        } catch (Exception e) {
            log.warn("[/kline] historyKline 获取失败 (market={}, symbol={}): {}",
                    klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
            historyKline = new ArrayList<>();
        }
        try {
            List<Kline> mappedKline = blockccService.getConPriceMap(klineParamVO, historyKline);
            if (mappedKline != null) historyKline = mappedKline;
        } catch (Exception e) {
            log.debug("[/kline] 控线价差叠加失败，使用原始K线 (market={}, symbol={}): {}",
                    klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
        }
        try {
            ticker = blockccService.getHistoryKline24hrTicker(klineParamVO);
        } catch (Exception e) {
            log.warn("[/kline] ticker 获取失败 (market={}, symbol={}): {}",
                    klineParamVO.getMarket(), klineParamVO.getSymbol(), e.toString());
            ticker = null;
        }
        if (ticker == null) ticker = new Ticker24hVO();
        if (historyKline.isEmpty() && "gate".equals(klineParamVO.getMarket())) {
            historyKline = getGateHistoryKline(klineParamVO);
        }
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
        return ajax;
    }

    private List<Kline> getGateHistoryKline(KlineParamVO klineParamVO) {
        List<Kline> list = new ArrayList<>();
        String pair = toGatePair(klineParamVO.getSymbol());
        if (pair == null) return list;
        String interval = toGateInterval(klineParamVO.getInterval());
        StringBuilder url = new StringBuilder("https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=")
                .append(pair)
                .append("&interval=").append(interval)
                .append("&limit=1000");
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
