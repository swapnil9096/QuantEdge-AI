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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class MarketDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${api.alpha-vantage.key:demo}")
    private String alphaVantageApiKey;
    
    @Value("${api.yahoo-finance.enabled:true}")
    private boolean yahooFinanceEnabled;
    
    @Value("${api.finnhub.key:}")
    private String finnhubApiKey;

    public MarketDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public MarketData getRealTimeMarketData(String symbol) {
        try {
            // Try multiple APIs in order of preference
            if (!"demo".equals(alphaVantageApiKey)) {
                return fetchFromAlphaVantage(symbol);
            }
            
            if (yahooFinanceEnabled) {
                return fetchFromYahooFinance(symbol);
            }
            
            if (!finnhubApiKey.isEmpty()) {
                return fetchFromFinnhub(symbol);
            }
            
            // Fallback to realistic simulation
            return generateRealisticMarketData(symbol);
            
        } catch (Exception e) {
            // Fallback to realistic simulation
            return generateRealisticMarketData(symbol);
        }
    }

    private MarketData fetchFromAlphaVantage(String symbol) throws Exception {
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

    private MarketData fetchFromYahooFinance(String symbol) throws Exception {
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s.BO", symbol);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode chart = root.get("chart");
            
            if (chart != null && chart.has("result")) {
                JsonNode result = chart.get("result").get(0);
                JsonNode meta = result.get("meta");
                
                if (meta != null) {
                    return parseYahooFinanceData(symbol, meta);
                }
            }
        }
        
        throw new RuntimeException("No data available from Yahoo Finance");
    }

    private MarketData fetchFromFinnhub(String symbol) throws Exception {
        String url = String.format("https://finnhub.io/api/v1/quote?symbol=%s.BSE&token=%s", symbol, finnhubApiKey);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
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

    private MarketData parseAlphaVantageData(String symbol, JsonNode quote) {
        BigDecimal currentPrice = new BigDecimal(quote.get("05. price").asText());
        BigDecimal open = new BigDecimal(quote.get("02. open").asText());
        BigDecimal high = new BigDecimal(quote.get("03. high").asText());
        BigDecimal low = new BigDecimal(quote.get("04. low").asText());
        BigDecimal previousClose = new BigDecimal(quote.get("08. previous close").asText());
        BigDecimal volume = new BigDecimal(quote.get("06. volume").asText());
        BigDecimal change = new BigDecimal(quote.get("09. change").asText());
        BigDecimal changePercent = new BigDecimal(quote.get("10. change percent").asText().replace("%", ""));

        return MarketData.builder()
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
                .dataSource("Alpha Vantage")
                .build();
    }

    private MarketData parseYahooFinanceData(String symbol, JsonNode meta) {
        BigDecimal currentPrice = new BigDecimal(meta.get("regularMarketPrice").asText());
        BigDecimal open = new BigDecimal(meta.get("regularMarketOpen").asText());
        BigDecimal high = new BigDecimal(meta.get("regularMarketDayHigh").asText());
        BigDecimal low = new BigDecimal(meta.get("regularMarketDayLow").asText());
        BigDecimal previousClose = new BigDecimal(meta.get("previousClose").asText());
        BigDecimal volume = new BigDecimal(meta.get("regularMarketVolume").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return MarketData.builder()
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
                .dataSource("Yahoo Finance")
                .build();
    }

    private MarketData parseFinnhubData(String symbol, JsonNode data) {
        BigDecimal currentPrice = new BigDecimal(data.get("c").asText());
        BigDecimal open = new BigDecimal(data.get("o").asText());
        BigDecimal high = new BigDecimal(data.get("h").asText());
        BigDecimal low = new BigDecimal(data.get("l").asText());
        BigDecimal previousClose = new BigDecimal(data.get("pc").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return MarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(BigDecimal.valueOf(1000000)) // Finnhub doesn't provide volume in quote
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("Finnhub")
                .build();
    }

    private MarketData generateRealisticMarketData(String symbol) {
        Random random = new Random();
        
        // Get realistic base price
        BigDecimal basePrice = getRealisticBasePrice(symbol);
        
        // Add realistic market volatility
        BigDecimal volatility = getMarketVolatility(symbol);
        BigDecimal priceVariation = basePrice.multiply(volatility).multiply(BigDecimal.valueOf(random.nextGaussian()));
        
        BigDecimal currentPrice = basePrice.add(priceVariation);
        BigDecimal open = currentPrice.multiply(BigDecimal.valueOf(0.995 + random.nextDouble() * 0.01));
        BigDecimal high = currentPrice.multiply(BigDecimal.valueOf(1.001 + random.nextDouble() * 0.005));
        BigDecimal low = currentPrice.multiply(BigDecimal.valueOf(0.995 - random.nextDouble() * 0.005));
        BigDecimal previousClose = currentPrice.multiply(BigDecimal.valueOf(0.98 + random.nextDouble() * 0.04));

        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal volume = BigDecimal.valueOf(1000000 + random.nextInt(5000000));

        return MarketData.builder()
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
                .dataSource("Realistic Simulation")
                .build();
    }

    private BigDecimal getRealisticBasePrice(String symbol) {
        // Current market prices (October 2024)
        Map<String, BigDecimal> currentPrices = new HashMap<>();
        currentPrices.put("RELIANCE", new BigDecimal("2450"));
        currentPrices.put("TCS", new BigDecimal("3500"));
        currentPrices.put("HDFCBANK", new BigDecimal("1600"));
        currentPrices.put("INFY", new BigDecimal("1500"));
        currentPrices.put("ICICIBANK", new BigDecimal("950"));
        currentPrices.put("KOTAKBANK", new BigDecimal("1800"));
        currentPrices.put("HINDUNILVR", new BigDecimal("2400"));
        currentPrices.put("ITC", new BigDecimal("450"));
        currentPrices.put("BHARTIARTL", new BigDecimal("1100"));
        currentPrices.put("SBIN", new BigDecimal("580"));
        currentPrices.put("LT", new BigDecimal("2700"));
        currentPrices.put("ASIANPAINT", new BigDecimal("3200"));
        currentPrices.put("AXISBANK", new BigDecimal("1000"));
        currentPrices.put("MARUTI", new BigDecimal("9500"));
        currentPrices.put("SUNPHARMA", new BigDecimal("1200"));
        currentPrices.put("TITAN", new BigDecimal("3200"));
        currentPrices.put("WIPRO", new BigDecimal("450"));
        currentPrices.put("ULTRACEMCO", new BigDecimal("7500"));
        currentPrices.put("NESTLEIND", new BigDecimal("18000"));
        currentPrices.put("POWERGRID", new BigDecimal("250"));
        currentPrices.put("YESBANK", new BigDecimal("25"));
        currentPrices.put("IRCTC", new BigDecimal("850"));
        currentPrices.put("IRFC", new BigDecimal("120"));
        currentPrices.put("ADANIPORTS", new BigDecimal("1200"));
        currentPrices.put("BAJFINANCE", new BigDecimal("7000"));
        currentPrices.put("BAJAJFINSV", new BigDecimal("1600"));
        currentPrices.put("BAJAJHLDNG", new BigDecimal("6500"));
        currentPrices.put("DRREDDY", new BigDecimal("5500"));
        currentPrices.put("EICHERMOT", new BigDecimal("3500"));
        currentPrices.put("HEROMOTOCO", new BigDecimal("2500"));
        currentPrices.put("M&M", new BigDecimal("1400"));
        currentPrices.put("TATAMOTORS", new BigDecimal("600"));
        currentPrices.put("HCLTECH", new BigDecimal("1200"));
        currentPrices.put("TECHM", new BigDecimal("1200"));
        currentPrices.put("GRASIM", new BigDecimal("1800"));
        currentPrices.put("ADANIGREEN", new BigDecimal("1000"));
        currentPrices.put("SHREECEM", new BigDecimal("25000"));
        currentPrices.put("INDUSINDBK", new BigDecimal("1200"));
        currentPrices.put("NTPC", new BigDecimal("200"));
        currentPrices.put("ONGC", new BigDecimal("200"));
        currentPrices.put("TATASTEEL", new BigDecimal("120"));
        currentPrices.put("JSWSTEEL", new BigDecimal("800"));
        currentPrices.put("HINDALCO", new BigDecimal("500"));
        currentPrices.put("DIVISLAB", new BigDecimal("3500"));
        currentPrices.put("GODREJCP", new BigDecimal("800"));
        currentPrices.put("PIDILITIND", new BigDecimal("2500"));
        currentPrices.put("TATACONSUM", new BigDecimal("800"));
        currentPrices.put("FEDERALBNK", new BigDecimal("150"));
        currentPrices.put("BANDHANBNK", new BigDecimal("200"));
        currentPrices.put("IDFCFIRSTB", new BigDecimal("100"));
        
        return currentPrices.getOrDefault(symbol, new BigDecimal("100"));
    }

    private BigDecimal getMarketVolatility(String symbol) {
        // Different volatility based on stock characteristics
        if (symbol.contains("BANK") || symbol.contains("FINANCE")) {
            return new BigDecimal("0.025"); // Banking stocks are more volatile
        } else if (symbol.contains("TECH") || symbol.contains("IT")) {
            return new BigDecimal("0.03"); // Tech stocks are volatile
        } else if (symbol.contains("STEEL") || symbol.contains("METAL")) {
            return new BigDecimal("0.04"); // Metal stocks are very volatile
        } else {
            return new BigDecimal("0.02"); // Default volatility
        }
    }

    public static class MarketData {
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
            private MarketData data = new MarketData();
            
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
            
            public MarketData build() { return data; }
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
