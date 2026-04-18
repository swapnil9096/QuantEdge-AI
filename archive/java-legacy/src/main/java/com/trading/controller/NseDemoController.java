package com.trading.controller;

import com.trading.model.NseStockData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nse-demo")
@CrossOrigin(origins = "*")
public class NseDemoController {

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<Map<String, Object>> getDemoStockData(@PathVariable String symbol) {
        try {
            // Create comprehensive demo data that shows what the NSE API would return
            NseStockData stockData = createDemoNseData(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stockData);
            response.put("message", "Demo NSE stock data for " + symbol);
            response.put("note", "This is sample data showing the structure of NSE API response");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to generate demo data for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/basic")
    public ResponseEntity<Map<String, Object>> getDemoBasicStockData(@PathVariable String symbol) {
        try {
            NseStockData stockData = createDemoNseData(symbol);
            
            Map<String, Object> basicData = new HashMap<>();
            basicData.put("symbol", stockData.getSymbol());
            basicData.put("companyName", stockData.getCompanyName());
            basicData.put("lastPrice", stockData.getLastPrice());
            basicData.put("open", stockData.getOpen());
            basicData.put("dayHigh", stockData.getDayHigh());
            basicData.put("dayLow", stockData.getDayLow());
            basicData.put("previousClose", stockData.getPreviousClose());
            basicData.put("change", stockData.getChange());
            basicData.put("changePercent", stockData.getChangePercent());
            basicData.put("volume", stockData.getTotalTradedVolume());
            basicData.put("timestamp", stockData.getTimestamp());
            basicData.put("dataSource", stockData.getDataSource());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", basicData);
            response.put("message", "Demo basic stock data for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to generate demo basic data for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/market-depth")
    public ResponseEntity<Map<String, Object>> getDemoMarketDepth(@PathVariable String symbol) {
        try {
            NseStockData stockData = createDemoNseData(symbol);
            
            Map<String, Object> marketDepth = new HashMap<>();
            marketDepth.put("symbol", stockData.getSymbol());
            marketDepth.put("bid", stockData.getBid());
            marketDepth.put("ask", stockData.getAsk());
            marketDepth.put("timestamp", stockData.getTimestamp());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", marketDepth);
            response.put("message", "Demo market depth data for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to generate demo market depth for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/company-info")
    public ResponseEntity<Map<String, Object>> getDemoCompanyInfo(@PathVariable String symbol) {
        try {
            NseStockData stockData = createDemoNseData(symbol);
            
            Map<String, Object> companyInfo = new HashMap<>();
            companyInfo.put("symbol", stockData.getSymbol());
            companyInfo.put("companyName", stockData.getCompanyName());
            companyInfo.put("industry", stockData.getIndustry());
            companyInfo.put("sector", stockData.getSector());
            companyInfo.put("isin", stockData.getIsin());
            companyInfo.put("series", stockData.getSeries());
            companyInfo.put("faceValue", stockData.getFaceValue());
            companyInfo.put("marketCap", stockData.getMarketCap());
            companyInfo.put("pe", stockData.getPe());
            companyInfo.put("pb", stockData.getPb());
            companyInfo.put("dividendYield", stockData.getDividendYield());
            companyInfo.put("bookValue", stockData.getBookValue());
            companyInfo.put("eps", stockData.getEps());
            companyInfo.put("timestamp", stockData.getTimestamp());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", companyInfo);
            response.put("message", "Demo company information for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to generate demo company information for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private NseStockData createDemoNseData(String symbol) {
        // Create realistic demo data based on the symbol
        String companyName = getCompanyName(symbol);
        BigDecimal basePrice = getBasePrice(symbol);
        
        // Create realistic price variations
        BigDecimal lastPrice = basePrice.multiply(BigDecimal.valueOf(0.98 + Math.random() * 0.04));
        BigDecimal open = lastPrice.multiply(BigDecimal.valueOf(0.995 + Math.random() * 0.01));
        BigDecimal dayHigh = lastPrice.multiply(BigDecimal.valueOf(1.001 + Math.random() * 0.005));
        BigDecimal dayLow = lastPrice.multiply(BigDecimal.valueOf(0.995 - Math.random() * 0.005));
        BigDecimal previousClose = lastPrice.multiply(BigDecimal.valueOf(0.98 + Math.random() * 0.04));
        
        BigDecimal change = lastPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        // Create market depth data
        List<NseStockData.BidAsk> bid = createMarketDepth(lastPrice, true);
        List<NseStockData.BidAsk> ask = createMarketDepth(lastPrice, false);
        
        return NseStockData.builder()
                .symbol(symbol)
                .companyName(companyName)
                .lastPrice(lastPrice.setScale(2, java.math.RoundingMode.HALF_UP))
                .open(open.setScale(2, java.math.RoundingMode.HALF_UP))
                .dayHigh(dayHigh.setScale(2, java.math.RoundingMode.HALF_UP))
                .dayLow(dayLow.setScale(2, java.math.RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, java.math.RoundingMode.HALF_UP))
                .totalTradedVolume(BigDecimal.valueOf(1000000 + (int)(Math.random() * 5000000)))
                .totalTradedValue(lastPrice.multiply(BigDecimal.valueOf(1000000 + Math.random() * 5000000)))
                .marketCap(lastPrice.multiply(BigDecimal.valueOf(100000000)))
                .change(change.setScale(2, java.math.RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, java.math.RoundingMode.HALF_UP))
                .high52Week(dayHigh.multiply(BigDecimal.valueOf(1.1 + Math.random() * 0.1)))
                .low52Week(dayLow.multiply(BigDecimal.valueOf(0.8 + Math.random() * 0.1)))
                .close(lastPrice)
                .yearHigh(dayHigh.multiply(BigDecimal.valueOf(1.2)))
                .yearLow(dayLow.multiply(BigDecimal.valueOf(0.7)))
                .bid(bid)
                .ask(ask)
                .industry(getIndustry(symbol))
                .sector(getSector(symbol))
                .isin(getIsin(symbol))
                .series("EQ")
                .faceValue("10")
                .pe(BigDecimal.valueOf(15 + Math.random() * 30))
                .pb(BigDecimal.valueOf(1 + Math.random() * 5))
                .dividendYield(BigDecimal.valueOf(0.5 + Math.random() * 3))
                .bookValue(lastPrice.multiply(BigDecimal.valueOf(0.5 + Math.random() * 0.5)))
                .eps(lastPrice.divide(BigDecimal.valueOf(15 + Math.random() * 30), 2, java.math.RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("NSE Demo Data")
                .build();
    }

    private String getCompanyName(String symbol) {
        switch (symbol.toUpperCase()) {
            case "IRCTC": return "Indian Railway Catering and Tourism Corporation Ltd";
            case "RELIANCE": return "Reliance Industries Ltd";
            case "TCS": return "Tata Consultancy Services Ltd";
            case "HDFCBANK": return "HDFC Bank Ltd";
            case "INFY": return "Infosys Ltd";
            default: return symbol + " Company Ltd";
        }
    }

    private BigDecimal getBasePrice(String symbol) {
        switch (symbol.toUpperCase()) {
            case "IRCTC": return new BigDecimal("850");
            case "RELIANCE": return new BigDecimal("2450");
            case "TCS": return new BigDecimal("3500");
            case "HDFCBANK": return new BigDecimal("1600");
            case "INFY": return new BigDecimal("1500");
            default: return new BigDecimal("100");
        }
    }

    private String getIndustry(String symbol) {
        switch (symbol.toUpperCase()) {
            case "IRCTC": return "Tourism & Hospitality";
            case "RELIANCE": return "Oil & Gas";
            case "TCS": return "Information Technology";
            case "HDFCBANK": return "Banking";
            case "INFY": return "Information Technology";
            default: return "General";
        }
    }

    private String getSector(String symbol) {
        switch (symbol.toUpperCase()) {
            case "IRCTC": return "Services";
            case "RELIANCE": return "Energy";
            case "TCS": return "Technology";
            case "HDFCBANK": return "Financial Services";
            case "INFY": return "Technology";
            default: return "General";
        }
    }

    private String getIsin(String symbol) {
        switch (symbol.toUpperCase()) {
            case "IRCTC": return "INE335Y01020";
            case "RELIANCE": return "INE002A01018";
            case "TCS": return "INE467B01029";
            case "HDFCBANK": return "INE040A01034";
            case "INFY": return "INE009A01021";
            default: return "INE000000000";
        }
    }

    private List<NseStockData.BidAsk> createMarketDepth(BigDecimal basePrice, boolean isBid) {
        List<NseStockData.BidAsk> depth = new ArrayList<>();
        BigDecimal multiplier = isBid ? BigDecimal.valueOf(0.99) : BigDecimal.valueOf(1.01);
        
        for (int i = 0; i < 5; i++) {
            BigDecimal price = basePrice.multiply(multiplier).multiply(BigDecimal.valueOf(1 - (i * 0.001)));
            BigDecimal quantity = BigDecimal.valueOf(1000 + Math.random() * 10000);
            Integer orders = (int)(1 + Math.random() * 10);
            
            depth.add(NseStockData.BidAsk.builder()
                    .price(price.setScale(2, java.math.RoundingMode.HALF_UP))
                    .quantity(quantity.setScale(0, java.math.RoundingMode.HALF_UP))
                    .orders(orders)
                    .build());
        }
        
        return depth;
    }
}
