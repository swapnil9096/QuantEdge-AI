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
import java.time.format.DateTimeFormatter;

@Service
public class RealTimeNseDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NseSessionManager sessionManager;
    private final LiveStockDataService liveStockDataService;
    private final ComprehensiveNseDataService comprehensiveNseDataService;
    private final McpDataService mcpDataService;
    
    @Value("${api.nse.enabled:true}")
    private boolean nseEnabled;
    
    @Value("${api.yahoo-finance.enabled:true}")
    private boolean yahooFinanceEnabled;
    
    @Value("${api.mcp.enabled:true}")
    private boolean mcpEnabled;

    public RealTimeNseDataService(NseSessionManager sessionManager, 
                                 LiveStockDataService liveStockDataService,
                                 ComprehensiveNseDataService comprehensiveNseDataService,
                                 McpDataService mcpDataService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.sessionManager = sessionManager;
        this.liveStockDataService = liveStockDataService;
        this.comprehensiveNseDataService = comprehensiveNseDataService;
        this.mcpDataService = mcpDataService;
    }

    public RealTimeMarketData getRealTimeData(String symbol) {
        try {
            // 1. Try Comprehensive NSE Data Service (most reliable with enhanced parsing)
            if (nseEnabled) {
                try {
                    System.out.println("Trying Comprehensive NSE Data Service for " + symbol);
                    com.trading.model.NseStockData nseData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
                    if (nseData != null && nseData.getLastPrice() != null && nseData.getLastPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
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
                    System.out.println("Trying MCP comprehensive data for " + symbol);
                    com.trading.model.NseStockData nseData = mcpDataService.getComprehensiveNseData(symbol);
                    if (nseData != null && nseData.getLastPrice() != null && nseData.getLastPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        System.out.println("MCP comprehensive data success for " + symbol);
                        return convertNseStockData(nseData);
                    }
                } catch (Exception e) {
                    System.err.println("MCP comprehensive data failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 3. Try MCP LiveMarketData
            if (mcpEnabled) {
                try {
                    System.out.println("Trying MCP LiveMarketData for " + symbol);
                    McpDataService.LiveMarketData mcpData = mcpDataService.getLiveMarketData(symbol);
                    return convertMcpData(mcpData);
                } catch (Exception e) {
                    System.err.println("MCP LiveMarketData failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 4. Try NSE API directly (fallback)
            if (nseEnabled) {
                try {
                    return fetchFromNseApi(symbol);
                } catch (Exception e) {
                    System.err.println("NSE API failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 5. Try Yahoo Finance as fallback
            if (yahooFinanceEnabled) {
                try {
                    return fetchFromYahooFinance(symbol);
                } catch (Exception e) {
                    System.err.println("Yahoo Finance failed for " + symbol + ": " + e.getMessage());
                }
            }
            
            // 6. Use the new LiveStockDataService for real-time data
            try {
                System.out.println("Trying LiveStockDataService for " + symbol);
                LiveStockDataService.LiveStockData liveData = liveStockDataService.getLiveStockData(symbol);
                
                // Convert to RealTimeMarketData
                return RealTimeMarketData.builder()
                        .symbol(liveData.getSymbol())
                        .currentPrice(liveData.getCurrentPrice())
                        .open(liveData.getOpen())
                        .high(liveData.getHigh())
                        .low(liveData.getLow())
                        .previousClose(liveData.getPreviousClose())
                        .volume(liveData.getVolume())
                        .change(liveData.getChange())
                        .changePercent(liveData.getChangePercent())
                        .timestamp(liveData.getTimestamp())
                        .dataSource(liveData.getDataSource())
                        .build();
            } catch (Exception e) {
                System.err.println("LiveStockDataService failed for " + symbol + ": " + e.getMessage());
            }
            
            // No fallback to static data - throw error to force live data usage
            throw new RuntimeException("All live APIs failed for " + symbol + ". Cannot use static data for real-time trading strategies!");
            
        } catch (Exception e) {
            System.err.println("Error fetching real-time data for " + symbol + ": " + e.getMessage());
            throw new RuntimeException("Cannot fetch live data for " + symbol + ". Real-time trading requires live market data!");
        }
    }
    
    private RealTimeMarketData convertNseStockData(com.trading.model.NseStockData nseData) {
        // Get volume - if zero, try to fetch from direct NSE API as fallback
        java.math.BigDecimal volume = nseData.getTotalTradedVolume();
        
        // If volume is zero, try to fetch from multiple timeframes (daily, weekly, monthly)
        if (volume == null || volume.compareTo(java.math.BigDecimal.ZERO) == 0) {
            try {
                System.out.println("Volume is zero for " + nseData.getSymbol() + ", trying multiple timeframes...");
                volume = fetchVolumeFromMultipleTimeframes(nseData.getSymbol());
                if (volume != null && volume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    System.out.println("Got volume from multiple timeframes: " + volume);
                } else {
                    // Fallback: try direct NSE API fetch
                    System.out.println("Trying direct NSE API fetch as fallback...");
                    RealTimeMarketData directData = fetchFromNseApi(nseData.getSymbol());
                    if (directData != null && directData.getVolume() != null && 
                        directData.getVolume().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        volume = directData.getVolume();
                        System.out.println("Got volume from direct NSE API: " + volume);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch volume from multiple timeframes: " + e.getMessage());
            }
        }
        
        // Ensure volume is not null
        if (volume == null) {
            volume = java.math.BigDecimal.ZERO;
        }
        
        return RealTimeMarketData.builder()
                .symbol(nseData.getSymbol())
                .currentPrice(nseData.getLastPrice())
                .open(nseData.getOpen())
                .high(nseData.getDayHigh())
                .low(nseData.getDayLow())
                .previousClose(nseData.getPreviousClose())
                .volume(volume)
                .change(nseData.getChange())
                .changePercent(nseData.getChangePercent())
                .timestamp(nseData.getTimestamp() != null ? nseData.getTimestamp() : java.time.LocalDateTime.now())
                .dataSource(nseData.getDataSource() != null ? nseData.getDataSource() : "NSE Comprehensive Data")
                .build();
    }
    
    private RealTimeMarketData convertMcpData(McpDataService.LiveMarketData mcpData) {
        return RealTimeMarketData.builder()
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

    private RealTimeMarketData fetchFromNseApi(String symbol) throws Exception {
        // NSE API endpoint for real-time stock data
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
        
        // Use proper browser headers that work in Postman
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "*/*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Referer", "https://www.nseindia.com/");
        headers.set("Origin", "https://www.nseindia.com");
        headers.set("Connection", "keep-alive");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            // Set timeout to prevent hanging
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Prefer new NSE quote structure: root.priceInfo.lastPrice
                JsonNode priceInfo = root.get("priceInfo");
                if (priceInfo != null && priceInfo.has("lastPrice")) {
                    RealTimeMarketData parsed = parseNsePriceInfo(symbol, priceInfo, root);
                    System.out.println("NSE priceInfo.lastPrice for " + symbol + ": " + parsed.getCurrentPrice());
                    return parsed;
                }

                // Legacy structure fallback (if any)
                JsonNode data = root.get("data");
                if (data != null && data.has("lastPrice")) {
                    return parseNseRealTimeData(symbol, data, root);
                }
            } else {
                System.err.println("NSE API returned status: " + response.getStatusCode());
                throw new RuntimeException("NSE API returned " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("NSE API failed for " + symbol + ": " + e.getMessage());
            throw e; // Re-throw to trigger fallback
        }
        
        throw new RuntimeException("No real-time data available from NSE API");
    }
    
    private RealTimeMarketData fetchFromNseApiWithRetry(String symbol) throws Exception {
        // Retry with exact same headers as your curl command
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
        
        HttpHeaders headers = new HttpHeaders();
        // Exact same headers as your curl command
        headers.set("Accept", "*/*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Priority", "u=1, i");
        headers.set("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol);
        headers.set("Sec-Ch-Ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"macOS\"");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
        
        // Same cookies as your curl command
        String cookies = "_ga=GA1.1.845952303.1745206525; AKA_A2=A; nsit=E2Clsnz1gzMBTFEV4RhieuLT; bm_mi=8AD3F2E8C7A6F77AE176F27AB6DCB767~YAAQRvTfFx58SOeZAQAADUX2AB1pdIlviP/TqS98yqA3sh+74XUqdKv5PACng9BjDOKfBkqXUqzpUAR2gpquss0maEx6eGHh0nIfmEb2Y/BUiI5HL4txx3eJRX6nYCWmjOGDrJA2czGL7LPltiFKeYaxa1zNk4PDPoslI0kXyWU6wWR0+OGqEwctfGS9WNw8k3HZwqFjOYqo2pGmwnq2t7eUn7S4TDliUJ563NbLEmKAwRwV7pDUlJRB0+I3Wu30+fKxBlVzMmcDcSHCc9yKAFIA42Kw4necpr2hfwArahk2ftxb5awn26dx2XzlQhOmnayeTF1/1dxMkaF+DbJu6PE=~1; nseQuoteSymbols=[{\"symbol\":\"" + symbol + "\",\"identifier\":\"\",\"type\":\"equity\"}]; bm_sv=78EDC83196A17CAF0E682358B96BC672~YAAQpvTfF08Y2NyZAQAAS132AB0WDocEXPcoQyaeaN24+Z9H85jLSRmFxEaCdCPPr9J5zdfh9pLU9QDS9Zx6cM2InKa6nCEQRzLDaYBKD/WMr2eag5iSvNlNQvt4zAVaFcHYewq04nK6RWDplXW6SLzJCPdTQIs/O9mCzF1pB/eaRatiaF210iKeuSJDJ+oM0m2OsqZbXphUDNSeo4F+c5TrGMn8G2+fe/Y3yNlpkPrBLrITFnu/G0kw1vv0PQFWvapp~1; ak_bmsc=E54958032AC3148D0D8C3D531C60D4DA~000000000000000000000000000000~YAAQbvTfFyGK8uiZAQAATYH2AB2n4vUF+W8oZRc6hPZBmIMgVjyD2uUtG4eqZGn1yJuqSWQrWpscmYWjY2mRfiP/YqiVJTGKb764GMBxAyMvNPuZzZNAqLeJBnL3KS/rpBgELpYxnvZRY/fFh2mexh10aLTHkMZSBXoee2rANJqXwm2WGo5a93kJQ7O2/YRqec44HozYFcitRV1fSzwn3AFTo4/prfyxsghQl2ilWpnlbeyLyntIBEP6Hr5VcW751ZTJ74fvUkqHhwfMEaU2zmFsByWxP+P3O4h1uFpM+GPdCqSwtKqlZGN2tEIwdTOiCU6XLiZZG67oR0FBhvvzCvn6i5yOUEkklYiXxq1qQ5q6eeuUy9AstFqFoN8ua52ZSaoow8Of/O3iP1OY0w8j6jmGFyqMET3/nTx1kgdX+21EXZOxnQGoAaK8UPLOk8jVQuhvc9Kku5s1yUyjxEkAf4L18lqU+0eFcekTDxg2iIfU51OYSwKtb0reWvQyxn9uDQ339ko0FA==; RT=\"z=1&dm=nseindia.com&si=0507906d-3a4b-461f-ac5c-06b8c5cecf08&ss=mgyxwvxx&sl=2&se=8c&tt=5jd&bcn=%2F%2F684d0d43.akstat.io%2F&obo=1&ld=1377\"; nseappid=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhcGkubnNlIiwiYXVkIjoiYXBpLm5zZSIsImlhdCI6MTc2MDk1MzA0NiwiZXhwIjoxNzYwOTYwMjQ2fQ.OcJ1ne9xHUsnnz2yKs0VJIKSGJ22ycx-0yrw8Afoeto; bm_sz=2FB5370F6C7F758B4E1DB7AD9BFC1641~YAAQRwkuF25u2+mZAQAA1Rb7AB35PfIkaf+U5uwGNQpKAwnzk6H2w+rBuKTX8U5XAbXGPippo2BB9aYEG5tpNuTQsOaBJwdPL66IalwPWkSHVTrbRpvdxQiznfVpR85IKmMi/zxO2VAn9RqCKELiAL5xp6CYwKcJmTKePytLAuDbqiNAOIyOhBs4bbylXdMZ+V86U87IJ7dG9RuqBC8vYBl1Hxm/88DHyb9d99iCptdIVH4lK3zmWnAJ0fCwswDIiCSW0M8MEimmcr4xVIbxwHlsd9zIbL/N9jXJtWFWcBFD1fMO7fhX0qxajuLNrnEDGt7N0I9d+zTM7nr/db84FRVBmOcdEgQTp26lmFeV9wGMzde1Vrn96SyzcSc/pvOmCXsKr+P2tvexY5bbbt7/G5uA8fecl9XcQGvu1slYZjwK0ktwDMTDYfs+Jrs9Uw5N~4473400~4274485; _ga_87M7PJ3R97=GS2.1.s1760952705$o55$g1$t1760953046$j60$l0$h0; _abck=54E88242B47BD4A980E5F5A294B80D4E~0~YAAQRwkuF+Ju2+mZAQAADhr7AA5SXvrP0kEYa491nlpOdo6eF+/0osrq34oZh38mYe5Ppa8dlSowinxdZCUe42Ur9nbDUiTJ2qvdH+vrLJ1rBixfg21NEWW5A7NvlbVMJUw7PDG19SSQV8pX4lPGrXUowi6b4HkQCo/AMMyH291ON4F4FhCSGseyHALx1BhfJpAt4yfhULf391JlL/NtxJYHxld5SU3+9lAoJqouJ6h5PEm2PXRiaSSnviGiGXlc2sai9m2irb0rje3PYVWmSIQd5Vf5yKFeqiGH91TLVTYxenUnTFTWpRtSAO2QO+HR11MBr4VSJkxFdQTHLxw7gR+GZkJZUfQMThiQzOAGvQ0s4+v8O7TFRfv4uWsJ1okvcwp3MF93NHjYFXS24sQsZy22dESuj1w0nVrKax43rPwtdCQhF6exlviW5p0S7zQDIB3oUvs3m3srT7ML2wj/aPx8J0hENsACDK+krVa1+EEA5Ne2Re3hGKNIKeI0s/rLVu4dVxR9FvL9itkPT6tzii52qTKhxzIqn/9m4KfYZ00=~-1~-1~-1~AAQAAAAE%2f%2f%2f%2f%2f%2fRNlAvqP5ASg97xesr27IS5Bt%2fVAXeJuabipCPIXP28OuwXw0sfH99rBugFqygeRc%2fCPpAdgGmJIQAaoJK%2flqzV8zO4XCKWeOQD~-1";
        headers.set("Cookie", cookies);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode priceInfo = root.get("priceInfo");
            if (priceInfo != null && priceInfo.has("lastPrice")) {
                RealTimeMarketData parsed = parseNsePriceInfo(symbol, priceInfo, root);
                System.out.println("NSE retry priceInfo.lastPrice for " + symbol + ": " + parsed.getCurrentPrice());
                return parsed;
            }

            JsonNode data = root.get("data");
            if (data != null && data.has("lastPrice")) {
                return parseNseRealTimeData(symbol, data, root);
            }
        }
        
        throw new RuntimeException("No real-time data available from NSE API retry");
    }

    private RealTimeMarketData fetchFromYahooFinance(String symbol) throws Exception {
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

    private RealTimeMarketData parseNseRealTimeData(String symbol, JsonNode data, JsonNode root) {
        // Extract real-time price data with current timestamp
        BigDecimal currentPrice = new BigDecimal(data.get("lastPrice").asText());
        BigDecimal open = new BigDecimal(data.get("open").asText());
        BigDecimal high = new BigDecimal(data.get("dayHigh").asText());
        BigDecimal low = new BigDecimal(data.get("dayLow").asText());
        BigDecimal previousClose = new BigDecimal(data.get("previousClose").asText());
        BigDecimal volume = new BigDecimal(data.get("totalTradedVolume").asText());
        
        // Calculate real-time change and percentage
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ? 
            change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : 
            BigDecimal.ZERO;

        // Get current timestamp for real-time data
        LocalDateTime currentTime = LocalDateTime.now();
        
        // Log real-time data fetch
        System.out.println("Real-time NSE data fetched for " + symbol + " at " + currentTime);
        System.out.println("Current Price: " + currentPrice + ", Change: " + change + " (" + changePercent + "%)");

        return RealTimeMarketData.builder()
                .symbol(symbol)
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .high(high.setScale(2, RoundingMode.HALF_UP))
                .low(low.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .volume(volume.setScale(0, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .timestamp(currentTime)
                .dataSource("NSE Real-Time Live Data")
                .build();
    }

    private RealTimeMarketData parseYahooFinanceData(String symbol, JsonNode meta) {
        BigDecimal currentPrice = new BigDecimal(meta.get("regularMarketPrice").asText());
        BigDecimal open = new BigDecimal(meta.get("regularMarketOpen").asText());
        BigDecimal high = new BigDecimal(meta.get("regularMarketDayHigh").asText());
        BigDecimal low = new BigDecimal(meta.get("regularMarketDayLow").asText());
        BigDecimal previousClose = new BigDecimal(meta.get("previousClose").asText());
        BigDecimal volume = new BigDecimal(meta.get("regularMarketVolume").asText());
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return RealTimeMarketData.builder()
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

    private RealTimeMarketData parseNsePriceInfo(String symbol, JsonNode priceInfo, JsonNode root) {
        // New NSE quote JSON shape:
        // root.priceInfo: { lastPrice, open, close (prevClose sometimes 0), vwap, intraDayHighLow {min,max}, weekHighLow{...} }
        BigDecimal currentPrice = asBigDecimal(priceInfo, "lastPrice");
        BigDecimal open = asBigDecimal(priceInfo, "open");
        BigDecimal previousClose = asBigDecimal(priceInfo, "previousClose");
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            // Some symbols report close instead of previousClose
            previousClose = asBigDecimal(priceInfo, "close");
        }

        BigDecimal high = currentPrice;
        BigDecimal low = currentPrice;
        JsonNode intra = priceInfo.get("intraDayHighLow");
        if (intra != null) {
            if (intra.has("max")) high = asBigDecimal(intra, "max");
            if (intra.has("min")) low = asBigDecimal(intra, "min");
        }

        // Volume: NSE top-level sometimes includes preOpenMarket.totalTradedVolume or priceInfo.totalTradedVolume
        // Also check data section, metadata, and other locations
        BigDecimal volume = BigDecimal.ZERO;
        
        // Try multiple locations for volume (enhanced parsing)
        if (priceInfo.has("totalTradedVolume")) {
            volume = asBigDecimal(priceInfo, "totalTradedVolume");
        }
        
        // Try data section
        if (volume.compareTo(BigDecimal.ZERO) == 0) {
            JsonNode data = root.get("data");
            if (data != null) {
                if (data.has("totalTradedVolume")) {
                    volume = asBigDecimal(data, "totalTradedVolume");
                }
                // Check if data has priceInfo
                if (volume.compareTo(BigDecimal.ZERO) == 0 && data.has("priceInfo")) {
                    JsonNode dataPriceInfo = data.get("priceInfo");
                    if (dataPriceInfo != null && dataPriceInfo.has("totalTradedVolume")) {
                        volume = asBigDecimal(dataPriceInfo, "totalTradedVolume");
                    }
                }
            }
        }
        
        // Try preOpenMarket
        if (volume.compareTo(BigDecimal.ZERO) == 0) {
            JsonNode preOpen = root.get("preOpenMarket");
            if (preOpen != null && preOpen.has("totalTradedVolume")) {
                volume = asBigDecimal(preOpen, "totalTradedVolume");
            }
        }
        
        // Try root level
        if (volume.compareTo(BigDecimal.ZERO) == 0 && root.has("totalTradedVolume")) {
            volume = asBigDecimal(root, "totalTradedVolume");
        }
        
        // Try alternative field names
        if (volume.compareTo(BigDecimal.ZERO) == 0) {
            if (priceInfo.has("volume")) {
                volume = asBigDecimal(priceInfo, "volume");
            } else if (priceInfo.has("tradedVolume")) {
                volume = asBigDecimal(priceInfo, "tradedVolume");
            }
        }
        
        System.out.println("RealTimeNseDataService - Volume extracted for " + symbol + ": " + volume);

        BigDecimal change = previousClose.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : currentPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ? 
                change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        return RealTimeMarketData.builder()
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
                // ignore and fallthrough
            }
        }
        return BigDecimal.ZERO;
    }

    // REMOVED: generateRealisticMarketData method - no static data for real-time trading

    // REMOVED: getCurrentMarketPrice method - no static data for real-time trading

    /**
     * Fetches volume from multiple timeframes (daily, weekly, monthly) to get real-time volume
     */
    private java.math.BigDecimal fetchVolumeFromMultipleTimeframes(String symbol) {
        java.math.BigDecimal volume = java.math.BigDecimal.ZERO;
        
        // 1. Try to fetch daily volume from historical data API (today's data)
        try {
            volume = fetchDailyVolume(symbol);
            if (volume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                System.out.println("RealTimeNseDataService - Got daily volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Failed to fetch daily volume: " + e.getMessage());
        }
        
        // 2. Try to fetch weekly average volume
        try {
            volume = fetchWeeklyAverageVolume(symbol);
            if (volume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                System.out.println("RealTimeNseDataService - Got weekly average volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Failed to fetch weekly volume: " + e.getMessage());
        }
        
        // 3. Try to fetch monthly average volume
        try {
            volume = fetchMonthlyAverageVolume(symbol);
            if (volume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                System.out.println("RealTimeNseDataService - Got monthly average volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Failed to fetch monthly volume: " + e.getMessage());
        }
        
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * Fetches daily volume from NSE historical data API
     */
    private java.math.BigDecimal fetchDailyVolume(String symbol) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        // NSE uses DD-MMM-YYYY format (e.g., "01-Jan-2024")
        DateTimeFormatter nseDateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = today.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        HttpHeaders headers = sessionManager.getNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                    JsonNode firstDay = root.get("data").get(0);
                    if (firstDay.has("CH_TOT_TRADED_QTY")) {
                        return new java.math.BigDecimal(firstDay.get("CH_TOT_TRADED_QTY").asText());
                    }
                    if (firstDay.has("TOTTRDQTY")) {
                        return new java.math.BigDecimal(firstDay.get("TOTTRDQTY").asText());
                    }
                    if (firstDay.has("volume")) {
                        return new java.math.BigDecimal(firstDay.get("volume").asText());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Error fetching daily volume: " + e.getMessage());
            throw e;
        }
        
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * Fetches weekly average volume from NSE historical data API (last 7 days)
     */
    private java.math.BigDecimal fetchWeeklyAverageVolume(String symbol) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekAgo = today.minusDays(7);
        // NSE uses DD-MMM-YYYY format
        DateTimeFormatter nseDateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = weekAgo.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        HttpHeaders headers = sessionManager.getNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray()) {
                    java.math.BigDecimal totalVolume = java.math.BigDecimal.ZERO;
                    int count = 0;
                    
                    for (JsonNode day : root.get("data")) {
                        java.math.BigDecimal dayVolume = java.math.BigDecimal.ZERO;
                        if (day.has("CH_TOT_TRADED_QTY")) {
                            dayVolume = new java.math.BigDecimal(day.get("CH_TOT_TRADED_QTY").asText());
                        } else if (day.has("TOTTRDQTY")) {
                            dayVolume = new java.math.BigDecimal(day.get("TOTTRDQTY").asText());
                        } else if (day.has("volume")) {
                            dayVolume = new java.math.BigDecimal(day.get("volume").asText());
                        }
                        
                        if (dayVolume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            totalVolume = totalVolume.add(dayVolume);
                            count++;
                        }
                    }
                    
                    if (count > 0) {
                        return totalVolume.divide(java.math.BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Error fetching weekly volume: " + e.getMessage());
            throw e;
        }
        
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * Fetches monthly average volume from NSE historical data API (last 30 days)
     */
    private java.math.BigDecimal fetchMonthlyAverageVolume(String symbol) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate monthAgo = today.minusDays(30);
        // NSE uses DD-MMM-YYYY format
        DateTimeFormatter nseDateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = monthAgo.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        HttpHeaders headers = sessionManager.getNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray()) {
                    java.math.BigDecimal totalVolume = java.math.BigDecimal.ZERO;
                    int count = 0;
                    
                    for (JsonNode day : root.get("data")) {
                        java.math.BigDecimal dayVolume = java.math.BigDecimal.ZERO;
                        if (day.has("CH_TOT_TRADED_QTY")) {
                            dayVolume = new java.math.BigDecimal(day.get("CH_TOT_TRADED_QTY").asText());
                        } else if (day.has("TOTTRDQTY")) {
                            dayVolume = new java.math.BigDecimal(day.get("TOTTRDQTY").asText());
                        } else if (day.has("volume")) {
                            dayVolume = new java.math.BigDecimal(day.get("volume").asText());
                        }
                        
                        if (dayVolume.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            totalVolume = totalVolume.add(dayVolume);
                            count++;
                        }
                    }
                    
                    if (count > 0) {
                        return totalVolume.divide(java.math.BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("RealTimeNseDataService - Error fetching monthly volume: " + e.getMessage());
            throw e;
        }
        
        return java.math.BigDecimal.ZERO;
    }
    
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

    public static class RealTimeMarketData {
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
            private RealTimeMarketData data = new RealTimeMarketData();
            
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
            
            public RealTimeMarketData build() { return data; }
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
