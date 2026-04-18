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
public class LiveStockDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${api.yahoo-finance.enabled:true}")
    private boolean yahooFinanceEnabled;
    
    @Value("${api.alpha-vantage.enabled:false}")
    private boolean alphaVantageEnabled;
    
    @Value("${api.alpha-vantage.key:demo}")
    private String alphaVantageApiKey;

    public LiveStockDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public LiveStockData getLiveStockData(String symbol) {
        System.out.println("Fetching LIVE data for symbol: " + symbol);
        
        try {
            // 1. Try Yahoo Finance API (most reliable for Indian stocks)
            if (yahooFinanceEnabled) {
                try {
                    System.out.println("Trying Yahoo Finance for " + symbol);
                    LiveStockData data = fetchFromYahooFinance(symbol);
                    if (data != null && data.getCurrentPrice() != null) {
                        System.out.println("Yahoo Finance SUCCESS for " + symbol + ": " + data.getCurrentPrice());
                        return data;
                    }
                } catch (Exception e) {
                    System.err.println("Yahoo Finance failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 2. Try Alpha Vantage API (if enabled and not demo key)
            if (alphaVantageEnabled && !"demo".equals(alphaVantageApiKey)) {
                try {
                    System.out.println("Trying Alpha Vantage for " + symbol);
                    LiveStockData data = fetchFromAlphaVantage(symbol);
                    if (data != null && data.getCurrentPrice() != null) {
                        System.out.println("Alpha Vantage SUCCESS for " + symbol + ": " + data.getCurrentPrice());
                        return data;
                    }
                } catch (Exception e) {
                    System.err.println("Alpha Vantage failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 3. Try alternative free APIs
            try {
                System.out.println("Trying alternative APIs for " + symbol);
                LiveStockData data = fetchFromAlternativeApis(symbol);
                if (data != null && data.getCurrentPrice() != null) {
                    System.out.println("Alternative API SUCCESS for " + symbol + ": " + data.getCurrentPrice());
                    return data;
                }
            } catch (Exception e) {
                System.err.println("Alternative APIs failed for " + symbol + ": " + e.getMessage());
            }
            
            // 4. No fallback to static data - throw error to force live data usage
            throw new RuntimeException("All live APIs failed for " + symbol + ". Cannot use static data for real-time trading strategies!");
            
        } catch (Exception e) {
            System.err.println("Error fetching live data for " + symbol + ": " + e.getMessage());
            throw new RuntimeException("Cannot fetch live data for " + symbol + ". Real-time trading requires live market data!");
        }
    }

    private LiveStockData fetchFromYahooFinance(String symbol) throws Exception {
        // Use Yahoo Finance API for Indian stocks
        String yahooSymbol = symbol + ".BO"; // BSE suffix for Indian stocks
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s", yahooSymbol);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        headers.set("Accept", "application/json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode chart = root.get("chart");
            if (chart != null && chart.has("result")) {
                JsonNode result = chart.get("result").get(0);
                JsonNode meta = result.get("meta");
                JsonNode quote = result.get("indicators").get("quote").get(0);
                
                if (meta != null && quote != null) {
                    BigDecimal currentPrice = new BigDecimal(meta.get("regularMarketPrice").asText());
                    BigDecimal open = new BigDecimal(meta.get("regularMarketOpen").asText());
                    BigDecimal high = new BigDecimal(meta.get("regularMarketDayHigh").asText());
                    BigDecimal low = new BigDecimal(meta.get("regularMarketDayLow").asText());
                    BigDecimal previousClose = new BigDecimal(meta.get("previousClose").asText());
                    BigDecimal volume = new BigDecimal(meta.get("regularMarketVolume").asText());
                    
                    BigDecimal change = currentPrice.subtract(previousClose);
                    BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    
                    return LiveStockData.builder()
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
                            .dataSource("Yahoo Finance Live Data")
                            .build();
                }
            }
        }
        throw new RuntimeException("No data available from Yahoo Finance");
    }

    private LiveStockData fetchFromAlphaVantage(String symbol) throws Exception {
        String url = String.format("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s", 
                symbol, alphaVantageApiKey);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode quote = root.get("Global Quote");
            
            if (quote != null) {
                BigDecimal currentPrice = new BigDecimal(quote.get("05. price").asText());
                BigDecimal open = new BigDecimal(quote.get("02. open").asText());
                BigDecimal high = new BigDecimal(quote.get("03. high").asText());
                BigDecimal low = new BigDecimal(quote.get("04. low").asText());
                BigDecimal previousClose = new BigDecimal(quote.get("08. previous close").asText());
                BigDecimal volume = new BigDecimal(quote.get("06. volume").asText());
                BigDecimal change = new BigDecimal(quote.get("09. change").asText());
                BigDecimal changePercent = new BigDecimal(quote.get("10. change percent").asText().replace("%", ""));
                
                return LiveStockData.builder()
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
                        .dataSource("Alpha Vantage Live Data")
                        .build();
            }
        }
        throw new RuntimeException("No data available from Alpha Vantage");
    }

    private LiveStockData fetchFromAlternativeApis(String symbol) throws Exception {
        // Try IEX Cloud API (free tier available)
        try {
            String url = String.format("https://cloud.iexapis.com/stable/stock/%s/quote?token=pk_test_123456789", symbol);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("latestPrice")) {
                    BigDecimal currentPrice = new BigDecimal(root.get("latestPrice").asText());
                    BigDecimal open = new BigDecimal(root.get("open").asText());
                    BigDecimal high = new BigDecimal(root.get("high").asText());
                    BigDecimal low = new BigDecimal(root.get("low").asText());
                    BigDecimal previousClose = new BigDecimal(root.get("previousClose").asText());
                    BigDecimal volume = new BigDecimal(root.get("volume").asText());
                    BigDecimal change = new BigDecimal(root.get("change").asText());
                    BigDecimal changePercent = new BigDecimal(root.get("changePercent").asText());
                    
                    return LiveStockData.builder()
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
                            .dataSource("IEX Cloud Live Data")
                            .build();
                }
            }
        } catch (Exception e) {
            System.err.println("IEX Cloud API failed: " + e.getMessage());
        }
        
        throw new RuntimeException("All alternative APIs failed");
    }

    // REMOVED: generateRealisticData method - no static data for real-time trading
    // REMOVED: getFallbackPrice method - no static data for real-time trading

    // Data class for live stock data
    public static class LiveStockData {
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
            private LiveStockData data = new LiveStockData();

            public Builder symbol(String symbol) {
                data.symbol = symbol;
                return this;
            }

            public Builder currentPrice(BigDecimal currentPrice) {
                data.currentPrice = currentPrice;
                return this;
            }

            public Builder open(BigDecimal open) {
                data.open = open;
                return this;
            }

            public Builder high(BigDecimal high) {
                data.high = high;
                return this;
            }

            public Builder low(BigDecimal low) {
                data.low = low;
                return this;
            }

            public Builder previousClose(BigDecimal previousClose) {
                data.previousClose = previousClose;
                return this;
            }

            public Builder volume(BigDecimal volume) {
                data.volume = volume;
                return this;
            }

            public Builder change(BigDecimal change) {
                data.change = change;
                return this;
            }

            public Builder changePercent(BigDecimal changePercent) {
                data.changePercent = changePercent;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                data.timestamp = timestamp;
                return this;
            }

            public Builder dataSource(String dataSource) {
                data.dataSource = dataSource;
                return this;
            }

            public LiveStockData build() {
                return data;
            }
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
