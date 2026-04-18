package com.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class LiveMarketDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebScrapingDataService webScrapingDataService;
    private final RobustMarketDataService robustMarketDataService;
    private final McpDataService mcpDataService;
    private final ComprehensiveNseDataService comprehensiveNseDataService;
    
    @Value("${api.alpha-vantage.key:demo}")
    private String alphaVantageApiKey;
    
    @Value("${api.finnhub.key:demo}")
    private String finnhubApiKey;
    
    @Value("${api.nse.enabled:true}")
    private boolean nseEnabled;
    
    @Value("${api.yahoo-finance.enabled:true}")
    private boolean yahooFinanceEnabled;
    
    @Value("${api.alpha-vantage.enabled:true}")
    private boolean alphaVantageEnabled;
    
    @Value("${api.finnhub.enabled:true}")
    private boolean finnhubEnabled;
    
    @Value("${api.mcp.enabled:true}")
    private boolean mcpEnabled;

    public LiveMarketDataService(WebScrapingDataService webScrapingDataService, 
                                RobustMarketDataService robustMarketDataService, 
                                McpDataService mcpDataService,
                                ComprehensiveNseDataService comprehensiveNseDataService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.webScrapingDataService = webScrapingDataService;
        this.robustMarketDataService = robustMarketDataService;
        this.mcpDataService = mcpDataService;
        this.comprehensiveNseDataService = comprehensiveNseDataService;
    }

    public LiveMarketData getLiveMarketData(String symbol) {
        System.out.println("Fetching live data for symbol: " + symbol);
        
        try {
            // 1. Try Comprehensive NSE Data Service (most reliable with enhanced parsing)
            if (nseEnabled) {
                try {
                    System.out.println("Trying Comprehensive NSE Data Service for " + symbol);
                    com.trading.model.NseStockData nseData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
                    if (nseData != null && nseData.getLastPrice() != null && nseData.getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("Comprehensive NSE Data Service success for " + symbol);
                        return convertNseStockData(nseData);
                    }
                } catch (Exception e) {
                    System.err.println("Comprehensive NSE Data Service failed for " + symbol + ": " + e.getMessage());
                }
            }

            // 2. Try MCP server (if enabled and configured) - uses enhanced NSE parsing
            if (mcpEnabled) {
                try {
                    System.out.println("Trying MCP server for " + symbol);
                    McpDataService.LiveMarketData data = mcpDataService.getLiveMarketData(symbol);
                    System.out.println("MCP server success for " + symbol + ", data source: " + data.getDataSource());
                    return convertMcpData(data);
                } catch (Exception e) {
                    System.err.println("MCP server failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 3. Try MCP comprehensive data endpoint
            if (mcpEnabled) {
                try {
                    System.out.println("Trying MCP comprehensive data for " + symbol);
                    com.trading.model.NseStockData nseData = mcpDataService.getComprehensiveNseData(symbol);
                    if (nseData != null && nseData.getLastPrice() != null && nseData.getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("MCP comprehensive data success for " + symbol);
                        return convertNseStockData(nseData);
                    }
                } catch (Exception e) {
                    System.err.println("MCP comprehensive data failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 4. Try robust market data service as fallback
            try {
                System.out.println("Trying robust market data service for " + symbol);
                RobustMarketDataService.LiveMarketData data = robustMarketDataService.getRealTimeData(symbol);
                System.out.println("Robust market data success for " + symbol + ", data source: " + data.getDataSource());
                return convertRobustData(data);
            } catch (Exception e) {
                System.err.println("Robust market data failed for " + symbol + ": " + e.getMessage());
            }
            
            // 5. Try Yahoo Finance (works well for Indian stocks)
            if (yahooFinanceEnabled) {
                try {
                    System.out.println("Trying Yahoo Finance for " + symbol);
                    LiveMarketData data = fetchFromYahooFinance(symbol);
                    System.out.println("Yahoo Finance success for " + symbol + ", data source: " + data.getDataSource());
                    return data;
                } catch (Exception e) {
                    System.err.println("Yahoo Finance failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 6. Try Finnhub API (good for international stocks)
            if (finnhubEnabled && !"demo".equals(finnhubApiKey)) {
                try {
                    System.out.println("Trying Finnhub API for " + symbol);
                    LiveMarketData data = fetchFromFinnhub(symbol);
                    System.out.println("Finnhub API success for " + symbol + ", data source: " + data.getDataSource());
                    return data;
                } catch (Exception e) {
                    System.err.println("Finnhub API failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 7. Try Alpha Vantage as last resort
            if (alphaVantageEnabled && !"demo".equals(alphaVantageApiKey)) {
                try {
                    System.out.println("Trying Alpha Vantage for " + symbol);
                    LiveMarketData data = fetchFromAlphaVantage(symbol);
                    System.out.println("Alpha Vantage success for " + symbol + ", data source: " + data.getDataSource());
                    return data;
                } catch (Exception e) {
                    System.err.println("Alpha Vantage failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 8. Try web scraping as fallback
            try {
                System.out.println("Trying web scraping for " + symbol);
                WebScrapingDataService.LiveMarketData data = webScrapingDataService.getRealTimeData(symbol);
                System.out.println("Web scraping success for " + symbol + ", data source: " + data.getDataSource());
                return convertWebScrapingData(data);
            } catch (Exception e) {
                System.err.println("Web scraping failed for " + symbol + ": " + e.getMessage());
            }
            
            // 8. No fallback to static data - throw error to force live data usage
            throw new RuntimeException("All live APIs failed for " + symbol + ". Cannot use static data for real-time trading strategies!");
            
        } catch (Exception e) {
            System.err.println("All APIs failed for " + symbol + ": " + e.getMessage());
            throw new RuntimeException("Cannot fetch live data for " + symbol + ". Real-time trading requires live market data!");
        }
    }

    // Removed fetchFromNseApiWithSession and establishNseSession - now handled by ComprehensiveNseDataService

    private LiveMarketData parseNseData(String symbol, JsonNode data) {
        BigDecimal currentPrice = new BigDecimal(data.get("lastPrice").asText());
        BigDecimal open = new BigDecimal(data.get("open").asText());
        BigDecimal high = new BigDecimal(data.get("dayHigh").asText());
        BigDecimal low = new BigDecimal(data.get("dayLow").asText());
        BigDecimal previousClose = new BigDecimal(data.get("previousClose").asText());
        BigDecimal volume = new BigDecimal(data.get("totalTradedVolume").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("NSE Live Data")
                .build();
    }

    private LiveMarketData parseNsePriceInfo(String symbol, JsonNode priceInfo, JsonNode root) {
        BigDecimal currentPrice = asBigDecimal(priceInfo, "lastPrice");
        BigDecimal open = asBigDecimal(priceInfo, "open");
        BigDecimal previousClose = asBigDecimal(priceInfo, "previousClose");
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            previousClose = asBigDecimal(priceInfo, "close");
        }

        BigDecimal high = currentPrice;
        BigDecimal low = currentPrice;
        JsonNode intra = priceInfo.get("intraDayHighLow");
        if (intra != null) {
            if (intra.has("max")) high = asBigDecimal(intra, "max");
            if (intra.has("min")) low = asBigDecimal(intra, "min");
        }

        BigDecimal volume = BigDecimal.ZERO;
        if (priceInfo.has("totalTradedVolume")) {
            volume = asBigDecimal(priceInfo, "totalTradedVolume");
        } else {
            JsonNode preOpen = root.get("preOpenMarket");
            if (preOpen != null && preOpen.has("totalTradedVolume")) {
                volume = asBigDecimal(preOpen, "totalTradedVolume");
            }
        }

        BigDecimal change = previousClose.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : currentPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ?
                change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("NSE Real-Time Live Data")
                .build();
    }

    private BigDecimal asBigDecimal(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            try {
                return new BigDecimal(node.get(field).asText());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return BigDecimal.ZERO;
    }

    private LiveMarketData fetchFromYahooFinance(String symbol) throws Exception {
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s.BO", symbol);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode chart = root.get("chart");
            
            if (chart != null && chart.has("result") && chart.get("result").size() > 0) {
                JsonNode result = chart.get("result").get(0);
                JsonNode meta = result.get("meta");
                
                if (meta != null && meta.has("regularMarketPrice")) {
                    return parseYahooFinanceData(symbol, meta);
                }
            }
        }
        
        throw new RuntimeException("No data available from Yahoo Finance");
    }

    private LiveMarketData fetchFromFinnhub(String symbol) throws Exception {
        // Finnhub API for stock quotes
        String url = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", symbol, finnhubApiKey);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        headers.set("Accept", "application/json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("c") && !root.get("c").isNull()) {
                return parseFinnhubData(symbol, root);
            }
        }
        
        throw new RuntimeException("No data available from Finnhub");
    }
    
    private LiveMarketData parseFinnhubData(String symbol, JsonNode data) {
        BigDecimal currentPrice = new BigDecimal(data.get("c").asText());
        BigDecimal open = new BigDecimal(data.get("o").asText());
        BigDecimal high = new BigDecimal(data.get("h").asText());
        BigDecimal low = new BigDecimal(data.get("l").asText());
        BigDecimal previousClose = new BigDecimal(data.get("pc").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(BigDecimal.ZERO) // Finnhub doesn't provide volume in quote endpoint
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("Finnhub Live Data")
                .build();
    }

    private LiveMarketData fetchFromAlphaVantage(String symbol) throws Exception {
        String url = String.format("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s.BSE&apikey=%s", 
                                   symbol, alphaVantageApiKey);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode quote = root.get("Global Quote");
            
            if (quote != null && !quote.isEmpty()) {
                return parseAlphaVantageData(symbol, quote);
            }
        }
        
        throw new RuntimeException("No data available from Alpha Vantage");
    }

    private LiveMarketData parseYahooFinanceData(String symbol, JsonNode meta) {
        BigDecimal currentPrice = new BigDecimal(meta.get("regularMarketPrice").asText());
        BigDecimal open = new BigDecimal(meta.get("regularMarketOpen").asText());
        BigDecimal high = new BigDecimal(meta.get("regularMarketDayHigh").asText());
        BigDecimal low = new BigDecimal(meta.get("regularMarketDayLow").asText());
        BigDecimal previousClose = new BigDecimal(meta.get("previousClose").asText());
        BigDecimal volume = new BigDecimal(meta.get("regularMarketVolume").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("Yahoo Finance Live")
                .build();
    }

    private LiveMarketData parseAlphaVantageData(String symbol, JsonNode quote) {
        BigDecimal currentPrice = new BigDecimal(quote.get("05. price").asText());
        BigDecimal open = new BigDecimal(quote.get("02. open").asText());
        BigDecimal high = new BigDecimal(quote.get("03. high").asText());
        BigDecimal low = new BigDecimal(quote.get("04. low").asText());
        BigDecimal previousClose = new BigDecimal(quote.get("08. previous close").asText());
        BigDecimal volume = new BigDecimal(quote.get("06. volume").asText());
        BigDecimal change = new BigDecimal(quote.get("09. change").asText());
        BigDecimal changePercent = new BigDecimal(quote.get("10. change percent").asText().replace("%", ""));

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("Alpha Vantage Live")
                .build();
    }

    // REMOVED: generateRealisticMarketData method - no static data for real-time trading

    // REMOVED: getCurrentMarketPrice method - no static data for real-time trading

    private BigDecimal getMarketVolatility(String symbol) {
        // Different volatility based on stock characteristics
        if (symbol.contains("BANK") || symbol.contains("FINANCE")) {
            return new BigDecimal("0.025"); // Banking stocks are more volatile
        } else if (symbol.contains("TECH") || symbol.contains("IT")) {
            return new BigDecimal("0.03"); // Tech stocks are volatile
        } else if (symbol.contains("STEEL") || symbol.contains("METAL")) {
            return new BigDecimal("0.04"); // Metal stocks are very volatile
        } else if (symbol.equals("IRFC") || symbol.equals("IRCTC")) {
            return new BigDecimal("0.035"); // Railway stocks are volatile
        } else {
            return new BigDecimal("0.02"); // Default volatility
        }
    }

    private LiveMarketData convertRobustData(RobustMarketDataService.LiveMarketData robustData) {
        return LiveMarketData.builder()
                .symbol(robustData.getSymbol())
                .currentPrice(robustData.getCurrentPrice())
                .open(robustData.getOpen())
                .high(robustData.getHigh())
                .low(robustData.getLow())
                .previousClose(robustData.getPreviousClose())
                .volume(robustData.getVolume())
                .change(robustData.getChange())
                .changePercent(robustData.getChangePercent())
                .timestamp(robustData.getTimestamp())
                .dataSource(robustData.getDataSource())
                .build();
    }

    private LiveMarketData convertWebScrapingData(WebScrapingDataService.LiveMarketData webData) {
        return LiveMarketData.builder()
                .symbol(webData.getSymbol())
                .currentPrice(webData.getCurrentPrice())
                .open(webData.getOpen())
                .high(webData.getHigh())
                .low(webData.getLow())
                .previousClose(webData.getPreviousClose())
                .volume(webData.getVolume())
                .change(webData.getChange())
                .changePercent(webData.getChangePercent())
                .timestamp(webData.getTimestamp())
                .dataSource(webData.getDataSource())
                .build();
    }

    private LiveMarketData convertMcpData(McpDataService.LiveMarketData mcpData) {
        return LiveMarketData.builder()
                .symbol(mcpData.getSymbol())
                .currentPrice(mcpData.getCurrentPrice())
                .open(mcpData.getOpen())
                .high(mcpData.getHigh())
                .low(mcpData.getLow())
                .previousClose(mcpData.getPreviousClose())
                .volume(mcpData.getVolume())
                .change(mcpData.getChange())
                .changePercent(mcpData.getChangePercent())
                .timestamp(mcpData.getTimestamp())
                .dataSource(mcpData.getDataSource())
                .build();
    }
    
    private LiveMarketData convertNseStockData(com.trading.model.NseStockData nseData) {
        return LiveMarketData.builder()
                .symbol(nseData.getSymbol())
                .currentPrice(nseData.getLastPrice())
                .open(nseData.getOpen())
                .high(nseData.getDayHigh())
                .low(nseData.getDayLow())
                .previousClose(nseData.getPreviousClose())
                .volume(nseData.getTotalTradedVolume())
                .change(nseData.getChange())
                .changePercent(nseData.getChangePercent())
                .timestamp(nseData.getTimestamp() != null ? nseData.getTimestamp() : LocalDateTime.now())
                .dataSource(nseData.getDataSource() != null ? nseData.getDataSource() : "NSE Comprehensive Data")
                .build();
    }

    public static class LiveMarketData {
        private String symbol;
        private BigDecimal currentPrice;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal previousClose;
        private BigDecimal volume;
        private BigDecimal change;
        private BigDecimal changePercent;
        private LocalDateTime timestamp;
        private String dataSource;
        
        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private LiveMarketData data = new LiveMarketData();
            
            public Builder symbol(String symbol) { data.symbol = symbol; return this; }
            public Builder currentPrice(BigDecimal currentPrice) { data.currentPrice = currentPrice; return this; }
            public Builder open(BigDecimal open) { data.open = open; return this; }
            public Builder high(BigDecimal high) { data.high = high; return this; }
            public Builder low(BigDecimal low) { data.low = low; return this; }
            public Builder previousClose(BigDecimal previousClose) { data.previousClose = previousClose; return this; }
            public Builder volume(BigDecimal volume) { data.volume = volume; return this; }
            public Builder change(BigDecimal change) { data.change = change; return this; }
            public Builder changePercent(BigDecimal changePercent) { data.changePercent = changePercent; return this; }
            public Builder timestamp(LocalDateTime timestamp) { data.timestamp = timestamp; return this; }
            public Builder dataSource(String dataSource) { data.dataSource = dataSource; return this; }
            
            public LiveMarketData build() { return data; }
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public BigDecimal getOpen() { return open; }
        public BigDecimal getHigh() { return high; }
        public BigDecimal getLow() { return low; }
        public BigDecimal getPreviousClose() { return previousClose; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getChange() { return change; }
        public BigDecimal getChangePercent() { return changePercent; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getDataSource() { return dataSource; }
    }
}
