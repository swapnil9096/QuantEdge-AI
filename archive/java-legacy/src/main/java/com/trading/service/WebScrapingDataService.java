package com.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class WebScrapingDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public WebScrapingDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public LiveMarketData getRealTimeData(String symbol) {
        try {
            // Try to get data from a reliable source
            return fetchFromReliableSource(symbol);
        } catch (Exception e) {
            System.err.println("Web scraping failed for " + symbol + ": " + e.getMessage());
            return generateRealisticData(symbol);
        }
    }

    private LiveMarketData fetchFromReliableSource(String symbol) throws Exception {
        // Use a more reliable approach - try multiple sources
        try {
            // Try Yahoo Finance with different symbol formats
            return fetchFromYahooFinance(symbol);
        } catch (Exception e1) {
            try {
                // Try with .NS suffix for NSE
                return fetchFromYahooFinance(symbol + ".NS");
            } catch (Exception e2) {
                try {
                    // Try with .BO suffix for BSE
                    return fetchFromYahooFinance(symbol + ".BO");
                } catch (Exception e3) {
                    throw new Exception("All Yahoo Finance attempts failed");
                }
            }
        }
    }

    private LiveMarketData fetchFromYahooFinance(String symbol) throws Exception {
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s", symbol);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "cross-site");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode chart = root.get("chart");
            
            if (chart != null && chart.has("result") && chart.get("result").size() > 0) {
                JsonNode result = chart.get("result").get(0);
                JsonNode meta = result.get("meta");
                
                if (meta != null && meta.has("regularMarketPrice")) {
                    return parseYahooData(symbol, meta);
                }
            }
        }
        
        throw new RuntimeException("No data available from Yahoo Finance");
    }

    private LiveMarketData parseYahooData(String symbol, JsonNode meta) {
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
                .dataSource("Yahoo Finance Live Data")
                .build();
    }

    private LiveMarketData generateRealisticData(String symbol) {
        // Get realistic base prices for Indian stocks
        BigDecimal basePrice = getRealisticBasePrice(symbol);
        
        // Add some realistic market movement
        double volatility = 0.02; // 2% volatility
        double change = (random.nextGaussian() * volatility);
        BigDecimal currentPrice = basePrice.multiply(BigDecimal.valueOf(1 + change));
        
        // Generate realistic OHLC data
        BigDecimal open = basePrice.multiply(BigDecimal.valueOf(1 + (random.nextGaussian() * 0.01)));
        BigDecimal high = currentPrice.max(open).multiply(BigDecimal.valueOf(1 + Math.abs(random.nextGaussian() * 0.005)));
        BigDecimal low = currentPrice.min(open).multiply(BigDecimal.valueOf(1 - Math.abs(random.nextGaussian() * 0.005)));
        BigDecimal previousClose = basePrice;
        
        BigDecimal changeAmount = currentPrice.subtract(previousClose);
        BigDecimal changePercent = changeAmount.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        BigDecimal volume = BigDecimal.valueOf(random.nextInt(1000000) + 500000);

        return LiveMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(changeAmount.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.now())
                .dataSource("Realistic Market Simulation")
                .build();
    }

    private BigDecimal getRealisticBasePrice(String symbol) {
        // Updated realistic base prices for Indian stocks (as of 2024)
        switch (symbol.toUpperCase()) {
            case "RELIANCE": return new BigDecimal("2450.00");
            case "TCS": return new BigDecimal("3800.00");
            case "HDFCBANK": return new BigDecimal("1650.00");
            case "INFY": return new BigDecimal("1450.00");
            case "ICICIBANK": return new BigDecimal("950.00");
            case "HINDUNILVR": return new BigDecimal("2400.00");
            case "ITC": return new BigDecimal("450.00");
            case "KOTAKBANK": return new BigDecimal("1800.00");
            case "BHARTIARTL": return new BigDecimal("1200.00");
            case "LT": return new BigDecimal("3200.00");
            case "ASIANPAINT": return new BigDecimal("3200.00");
            case "AXISBANK": return new BigDecimal("1100.00");
            case "MARUTI": return new BigDecimal("10500.00");
            case "SUNPHARMA": return new BigDecimal("1200.00");
            case "NESTLEIND": return new BigDecimal("22000.00");
            case "ULTRACEMCO": return new BigDecimal("8500.00");
            case "WIPRO": return new BigDecimal("450.00");
            case "ONGC": return new BigDecimal("180.00");
            case "NTPC": return new BigDecimal("220.00");
            case "POWERGRID": return new BigDecimal("250.00");
            case "COALINDIA": return new BigDecimal("350.00");
            case "IRCTC": return new BigDecimal("850.00");
            case "IRFC": return new BigDecimal("120.00");
            case "TATASTEEL": return new BigDecimal("120.00");
            case "JSWSTEEL": return new BigDecimal("750.00");
            case "HINDALCO": return new BigDecimal("450.00");
            case "ADANIGREEN": return new BigDecimal("1800.00");
            case "ADANIPORTS": return new BigDecimal("1200.00");
            case "ADANIENT": return new BigDecimal("2500.00");
            case "GRASIM": return new BigDecimal("1800.00");
            case "TATAMOTORS": return new BigDecimal("650.00");
            case "BAJFINANCE": return new BigDecimal("7500.00");
            case "BAJAJFINSV": return new BigDecimal("1500.00");
            case "DRREDDY": return new BigDecimal("5500.00");
            case "DIVISLAB": return new BigDecimal("3500.00");
            case "CIPLA": return new BigDecimal("1200.00");
            case "APOLLOHOSP": return new BigDecimal("5500.00");
            case "HEROMOTOCO": return new BigDecimal("3500.00");
            case "EICHERMOT": return new BigDecimal("3500.00");
            case "M&M": return new BigDecimal("1500.00");
            case "BAJAJ-AUTO": return new BigDecimal("4500.00");
            case "TITAN": return new BigDecimal("3500.00");
            case "TATACONSUM": return new BigDecimal("1100.00");
            case "HDFCLIFE": return new BigDecimal("650.00");
            case "SBILIFE": return new BigDecimal("1200.00");
            case "ICICIGI": return new BigDecimal("1200.00");
            case "HDFCAMC": return new BigDecimal("3500.00");
            case "SHREECEM": return new BigDecimal("25000.00");
            case "INDUSINDBK": return new BigDecimal("1200.00");
            case "SBIN": return new BigDecimal("650.00");
            case "YESBANK": return new BigDecimal("25.00");
            case "FEDERALBNK": return new BigDecimal("150.00");
            case "BANDHANBNK": return new BigDecimal("200.00");
            case "IDFCFIRSTB": return new BigDecimal("100.00");
            case "PIDILITIND": return new BigDecimal("2500.00");
            case "GODREJCP": return new BigDecimal("800.00");
            default: return new BigDecimal("100.00");
        }
    }

    // Inner class for LiveMarketData
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
        public static LiveMarketDataBuilder builder() {
            return new LiveMarketDataBuilder();
        }

        public static class LiveMarketDataBuilder {
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

            public LiveMarketDataBuilder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public LiveMarketDataBuilder currentPrice(BigDecimal currentPrice) {
                this.currentPrice = currentPrice;
                return this;
            }

            public LiveMarketDataBuilder open(BigDecimal open) {
                this.open = open;
                return this;
            }

            public LiveMarketDataBuilder high(BigDecimal high) {
                this.high = high;
                return this;
            }

            public LiveMarketDataBuilder low(BigDecimal low) {
                this.low = low;
                return this;
            }

            public LiveMarketDataBuilder previousClose(BigDecimal previousClose) {
                this.previousClose = previousClose;
                return this;
            }

            public LiveMarketDataBuilder volume(BigDecimal volume) {
                this.volume = volume;
                return this;
            }

            public LiveMarketDataBuilder change(BigDecimal change) {
                this.change = change;
                return this;
            }

            public LiveMarketDataBuilder changePercent(BigDecimal changePercent) {
                this.changePercent = changePercent;
                return this;
            }

            public LiveMarketDataBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public LiveMarketDataBuilder dataSource(String dataSource) {
                this.dataSource = dataSource;
                return this;
            }

            public LiveMarketData build() {
                LiveMarketData data = new LiveMarketData();
                data.symbol = this.symbol;
                data.currentPrice = this.currentPrice;
                data.open = this.open;
                data.high = this.high;
                data.low = this.low;
                data.previousClose = this.previousClose;
                data.volume = this.volume;
                data.change = this.change;
                data.changePercent = this.changePercent;
                data.timestamp = this.timestamp;
                data.dataSource = this.dataSource;
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
