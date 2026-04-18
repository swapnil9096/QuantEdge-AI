package com.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.NseStockData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComprehensiveNseDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache for NSE session cookies
    private final java.util.Map<String, String> nseSessionCache = new java.util.concurrent.ConcurrentHashMap<>();
    private java.time.LocalDateTime lastNseSessionTime = java.time.LocalDateTime.now().minusHours(1);
    
    @Value("${api.nse.enabled:true}")
    private boolean nseEnabled;

    public ComprehensiveNseDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Establishes a session with NSE by visiting the homepage to get cookies
     */
    private void establishNseSession() throws Exception {
        // Check if we need to refresh the session (every 15 minutes)
        if (lastNseSessionTime.isAfter(java.time.LocalDateTime.now().minusMinutes(15))) {
            return; // Session still valid
        }
        
        try {
            // Clear old session cache
            nseSessionCache.clear();
            
            // Add delay to avoid rate limiting
            Thread.sleep(1000);
            
            // Visit the main NSE page to get session cookies
            String mainUrl = "https://www.nseindia.com/";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Accept-Encoding", "gzip, deflate, br");
            headers.set("Connection", "keep-alive");
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set("Sec-Fetch-Dest", "document");
            headers.set("Sec-Fetch-Mode", "navigate");
            headers.set("Sec-Fetch-Site", "none");
            headers.set("Sec-Fetch-User", "?1");
            headers.set("Sec-Ch-Ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
            headers.set("Sec-Ch-Ua-Mobile", "?0");
            headers.set("Sec-Ch-Ua-Platform", "\"macOS\"");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(mainUrl, HttpMethod.GET, entity, String.class);
            
            // Extract cookies from response headers
            if (response.getHeaders().containsKey("Set-Cookie")) {
                response.getHeaders().get("Set-Cookie").forEach(cookie -> {
                    String[] parts = cookie.split(";")[0].split("=", 2);
                    if (parts.length == 2) {
                        nseSessionCache.put(parts[0].trim(), parts[1].trim());
                    }
                });
            }
            
            // Also try to get cookies from response body if Set-Cookie header is missing
            if (nseSessionCache.isEmpty() && response.getBody() != null) {
                // Sometimes cookies are set via JavaScript, try visiting market-data page
                Thread.sleep(500);
                String marketUrl = "https://www.nseindia.com/market-data";
                ResponseEntity<String> marketResponse = restTemplate.exchange(marketUrl, HttpMethod.GET, entity, String.class);
                if (marketResponse.getHeaders().containsKey("Set-Cookie")) {
                    marketResponse.getHeaders().get("Set-Cookie").forEach(cookie -> {
                        String[] parts = cookie.split(";")[0].split("=", 2);
                        if (parts.length == 2) {
                            nseSessionCache.put(parts[0].trim(), parts[1].trim());
                        }
                    });
                }
            }
            
            lastNseSessionTime = java.time.LocalDateTime.now();
            System.out.println("ComprehensiveNseDataService - NSE session established with cookies: " + nseSessionCache.size());
            
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Failed to establish NSE session: " + e.getMessage());
            // Continue without session - some endpoints might still work
        }
    }
    
    /**
     * Creates HTTP headers with session cookies for NSE API requests
     */
    private HttpHeaders createNseHeaders(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        headers.set("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol);
        headers.set("Origin", "https://www.nseindia.com");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("Sec-Ch-Ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"macOS\"");
        headers.set("Priority", "u=1, i");
        
        // Add session cookies if available
        if (!nseSessionCache.isEmpty()) {
            StringBuilder cookieHeader = new StringBuilder();
            nseSessionCache.forEach((key, value) -> {
                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                cookieHeader.append(key).append("=").append(value);
            });
            headers.set("Cookie", cookieHeader.toString());
            System.out.println("ComprehensiveNseDataService - Using session cookies: " + cookieHeader.toString().substring(0, Math.min(50, cookieHeader.length())));
        }
        
        return headers;
    }

    public NseStockData getComprehensiveStockData(String symbol) {
        try {
            if (nseEnabled) {
                System.out.println("ComprehensiveNseDataService - Starting fetch for " + symbol);
                return fetchComprehensiveNseData(symbol);
            }
            throw new RuntimeException("NSE API is disabled");
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Error fetching comprehensive NSE data for " + symbol + ": " + e.getMessage());
            System.err.println("ComprehensiveNseDataService - Exception type: " + e.getClass().getName());
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch comprehensive stock data: " + e.getMessage(), e);
        }
    }

    private NseStockData fetchComprehensiveNseData(String symbol) throws Exception {
        // Establish NSE session first
        establishNseSession();
        
        // Add delay to avoid rate limiting
        Thread.sleep(500);
        
        // NSE API endpoint for comprehensive stock data with trade info
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s&section=trade_info", symbol);
        System.out.println("ComprehensiveNseDataService - Fetching from: " + url);
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                
                System.out.println("ComprehensiveNseDataService - Response status: " + response.getStatusCode() + " (Attempt " + attempt + ")");
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    if (response.getBody() == null || response.getBody().isEmpty()) {
                        throw new RuntimeException("Empty response body from NSE API");
                    }
                    JsonNode root = objectMapper.readTree(response.getBody());
                    return parseComprehensiveNseData(symbol, root);
                } else if (response.getStatusCode() == HttpStatus.FORBIDDEN || response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    // Session expired, refresh and retry
                    System.out.println("ComprehensiveNseDataService - Session expired (403/401), refreshing session...");
                    lastNseSessionTime = java.time.LocalDateTime.now().minusHours(1);
                    nseSessionCache.clear();
                    establishNseSession();
                    
                    if (attempt < maxRetries) {
                        Thread.sleep(2000 * attempt); // Exponential backoff
                        headers = createNseHeaders(symbol);
                        entity = new HttpEntity<>(headers);
                        continue;
                    } else {
                        // Last attempt failed, try fallback
                        return fetchNseDataFallback(symbol);
                    }
                } else {
                    System.err.println("ComprehensiveNseDataService - Unexpected status: " + response.getStatusCode());
                    if (attempt < maxRetries) {
                        Thread.sleep(1000 * attempt);
                        continue;
                    } else {
                        return fetchNseDataFallback(symbol);
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
                System.err.println("ComprehensiveNseDataService - 403 Forbidden on attempt " + attempt);
                if (attempt < maxRetries) {
                    lastNseSessionTime = java.time.LocalDateTime.now().minusHours(1);
                    nseSessionCache.clear();
                    establishNseSession();
                    Thread.sleep(2000 * attempt);
                    headers = createNseHeaders(symbol);
                    entity = new HttpEntity<>(headers);
                } else {
                    System.err.println("ComprehensiveNseDataService - NSE API blocked after " + maxRetries + " attempts. Trying fallback...");
                    return fetchNseDataFallback(symbol);
                }
            } catch (Exception e) {
                System.err.println("ComprehensiveNseDataService - Error fetching NSE data for " + symbol + " (attempt " + attempt + "): " + e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(1000 * attempt);
                    continue;
                } else {
                    e.printStackTrace();
                    return fetchNseDataFallback(symbol);
                }
            }
        }
        
        // If we get here, all retries failed
        return fetchNseDataFallback(symbol);
    }
    
    private NseStockData fetchNseDataFallback(String symbol) throws Exception {
        // Ensure session is established
        establishNseSession();
        
        // Fallback NSE API endpoint without section parameter
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
        System.out.println("ComprehensiveNseDataService - Fallback fetching from: " + url);
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            System.out.println("ComprehensiveNseDataService - Fallback response status: " + response.getStatusCode());
        
        if (response.getStatusCode() == HttpStatus.OK) {
                if (response.getBody() == null || response.getBody().isEmpty()) {
                    throw new RuntimeException("Empty response body from NSE API fallback");
                }
                
                // Log response for debugging
                System.out.println("ComprehensiveNseDataService - Response body length: " + response.getBody().length());
                if (response.getBody().length() < 1000) {
                    System.out.println("ComprehensiveNseDataService - Response body: " + response.getBody());
                } else {
                    System.out.println("ComprehensiveNseDataService - Response body preview: " + response.getBody().substring(0, 500));
                }
                
            JsonNode root = objectMapper.readTree(response.getBody());
            return parseComprehensiveNseData(symbol, root);
            } else {
                System.err.println("ComprehensiveNseDataService - Fallback failed with status: " + response.getStatusCode());
                if (response.getBody() != null) {
                    System.err.println("ComprehensiveNseDataService - Error response: " + response.getBody());
                }
                throw new RuntimeException("NSE API fallback returned status: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("ComprehensiveNseDataService - HTTP error: " + e.getStatusCode() + " - " + e.getMessage());
            if (e.getResponseBodyAsString() != null) {
                System.err.println("ComprehensiveNseDataService - Error body: " + e.getResponseBodyAsString());
            }
            throw new RuntimeException("NSE API error: " + e.getStatusCode() + " - " + e.getMessage());
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Exception in fallback: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch NSE data: " + e.getMessage(), e);
        }
    }

    private NseStockData parseComprehensiveNseData(String symbol, JsonNode root) {
        // Log response structure for debugging
        System.out.println("ComprehensiveNseDataService - NSE Response structure for " + symbol + ":");
        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        root.fieldNames().forEachRemaining(fieldNames::add);
        System.out.println("Root keys: " + fieldNames);
        
        // Check for error response first
        if (root.has("error") || root.has("message")) {
            String errorMsg = root.has("error") ? root.get("error").asText() : 
                (root.has("message") ? root.get("message").asText() : "Unknown error");
            throw new RuntimeException("NSE API returned error: " + errorMsg);
        }
        
        // Try multiple possible structures - same logic as McpDataService
        JsonNode priceInfo = root.get("priceInfo");
        JsonNode data = root.get("data");
        JsonNode tradeInfo = root.get("tradeInfo");
        JsonNode info = null;
        
        // Priority: data > priceInfo > tradeInfo > root (if fields are at root level)
        // Check if data exists and has any price-related fields
        if (data != null && !data.isNull() && 
            (data.has("lastPrice") || data.has("priceInfo") || data.has("open") || 
             data.has("dayHigh") || data.has("dayLow") || data.has("previousClose") ||
             data.has("close") || data.has("high") || data.has("low"))) {
            // Check if data has nested priceInfo
            if (data.has("priceInfo") && !data.get("priceInfo").isNull()) {
                info = data.get("priceInfo");
            } else {
                info = data;
            }
            System.out.println("ComprehensiveNseDataService - Using 'data' section for " + symbol);
        } else if (priceInfo != null && !priceInfo.isNull()) {
            info = priceInfo;
            System.out.println("ComprehensiveNseDataService - Using 'priceInfo' section for " + symbol);
        } else if (tradeInfo != null && !tradeInfo.isNull()) {
            // Check if tradeInfo has price fields or if we need to use it alongside priceInfo
            info = tradeInfo;
            System.out.println("ComprehensiveNseDataService - Using 'tradeInfo' section for " + symbol);
        } else if (root.has("lastPrice") || root.has("open")) {
            // Try root level if it has price fields directly
            info = root;
            System.out.println("ComprehensiveNseDataService - Using root level for " + symbol);
        }
        
        if (info == null) {
            // Log the actual response for debugging
            System.err.println("ComprehensiveNseDataService - ERROR: No valid data section found for " + symbol);
            System.err.println("ComprehensiveNseDataService - Available root keys: " + fieldNames);
            System.err.println("ComprehensiveNseDataService - Data node exists: " + (data != null));
            System.err.println("ComprehensiveNseDataService - PriceInfo node exists: " + (priceInfo != null));
            
            // Try to log the actual response structure
            String responseBody = root.toString();
            System.err.println("ComprehensiveNseDataService - Full NSE response for " + symbol + " (first 2000 chars):");
            System.err.println(responseBody.length() > 2000 ? 
                responseBody.substring(0, 2000) + "..." : responseBody);
            
            // If we have data, priceInfo, tradeInfo but they're empty, try to use them anyway
            if (data != null && !data.isNull()) {
                System.err.println("ComprehensiveNseDataService - Data node is not null, trying to use it anyway");
                info = data;
            } else if (priceInfo != null && !priceInfo.isNull()) {
                System.err.println("ComprehensiveNseDataService - PriceInfo node is not null, trying to use it anyway");
                info = priceInfo;
            } else if (tradeInfo != null && !tradeInfo.isNull()) {
                System.err.println("ComprehensiveNseDataService - TradeInfo node is not null, trying to use it anyway");
                info = tradeInfo;
            } else {
                // Last resort: try to use root if it has any numeric fields
                if (root.size() > 0) {
                    System.err.println("ComprehensiveNseDataService - Using root as fallback");
                    info = root;
                } else {
                    throw new RuntimeException("No valid data section found in NSE API response. Available keys: " + fieldNames + 
                        ". Response preview: " + (responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody));
                }
            }
        }

        // Parse basic price information from info node
        BigDecimal lastPrice = parseBigDecimal(info, "lastPrice");
        BigDecimal open = parseBigDecimal(info, "open");
        BigDecimal dayHigh = parseBigDecimal(info, "dayHigh");
        BigDecimal dayLow = parseBigDecimal(info, "dayLow");
        BigDecimal previousClose = parseBigDecimal(info, "previousClose");
        
        // Try alternative field names if standard ones are missing
        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
            lastPrice = parseBigDecimal(info, "price");
            if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
                lastPrice = parseBigDecimal(info, "currentPrice");
            }
            if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
                lastPrice = parseBigDecimal(info, "close");
            }
        }
        
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            previousClose = parseBigDecimal(info, "prevClose");
            if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
                previousClose = parseBigDecimal(info, "yesterdayClose");
            }
        }
        
        // Handle intraday high/low from newer structure
        if (dayHigh.compareTo(BigDecimal.ZERO) == 0 && info.has("intraDayHighLow")) {
            JsonNode intra = info.get("intraDayHighLow");
            if (intra != null) {
                dayHigh = parseBigDecimal(intra, "max");
                dayLow = parseBigDecimal(intra, "min");
            }
        }
        
        // Try alternative high/low field names
        if (dayHigh.compareTo(BigDecimal.ZERO) == 0) {
            dayHigh = parseBigDecimal(info, "high");
        }
        if (dayLow.compareTo(BigDecimal.ZERO) == 0) {
            dayLow = parseBigDecimal(info, "low");
        }
        
        // Validate we have at least some price data
        if (lastPrice.compareTo(BigDecimal.ZERO) == 0 && open.compareTo(BigDecimal.ZERO) == 0 && 
            previousClose.compareTo(BigDecimal.ZERO) == 0) {
            System.err.println("ComprehensiveNseDataService - WARNING: No price data found in response for " + symbol);
            System.err.println("ComprehensiveNseDataService - Info node keys: " + getNodeKeys(info));
            // Don't throw error, try to proceed with zero values
        }
        
        // Parse volume and market cap - check multiple locations (including priceInfo and tradeInfo sections)
        // Volume represents LIVE DAILY VOLUME (cumulative volume for current trading day)
        // This is the total volume traded so far today, updated in real-time during market hours
        JsonNode priceInfoNode = root.get("priceInfo");
        if (priceInfoNode == null && data != null) {
            priceInfoNode = data.get("priceInfo");
        }
        
        JsonNode tradeInfoNode = root.get("tradeInfo");
        if (tradeInfoNode == null && data != null) {
            tradeInfoNode = data.get("tradeInfo");
        }
        
        BigDecimal totalTradedVolume = BigDecimal.ZERO;
        // First try tradeInfo section (newer NSE structure)
        if (tradeInfoNode != null) {
            totalTradedVolume = parseBigDecimalFromMultipleLocations(root, tradeInfoNode, "totalTradedVolume", 
                new String[]{"totalTradedVolume", "volume", "tradedVolume"});
            // If volume in tradeInfo is in crores (small decimal values), convert to actual volume
            if (totalTradedVolume.compareTo(BigDecimal.ZERO) > 0 && totalTradedVolume.compareTo(BigDecimal.valueOf(1000)) < 0) {
                // Likely in crores, convert: multiply by 10,000,000
                totalTradedVolume = totalTradedVolume.multiply(BigDecimal.valueOf(10_000_000));
                System.out.println("ComprehensiveNseDataService - Converted volume from crores to actual volume: " + totalTradedVolume);
            }
        }
        
        // Try priceInfo section (most common location for live daily volume)
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0 && priceInfoNode != null) {
            totalTradedVolume = parseBigDecimalFromMultipleLocations(root, priceInfoNode, "totalTradedVolume", 
                new String[]{"totalTradedVolume", "volume", "tradedVolume"});
        }
        
        // If still zero, try info section
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0) {
            totalTradedVolume = parseBigDecimalFromMultipleLocations(root, info, "totalTradedVolume", 
                new String[]{"totalTradedVolume", "volume", "tradedVolume"});
        }
        
        // If still zero, try data section directly
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0 && data != null) {
            totalTradedVolume = parseBigDecimalFromMultipleLocations(root, data, "totalTradedVolume", 
                new String[]{"totalTradedVolume", "volume", "tradedVolume"});
        }
        
        // If still zero, try preOpenMarket (pre-market session volume)
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0) {
            JsonNode preOpen = root.get("preOpenMarket");
            if (preOpen != null) {
                totalTradedVolume = parseBigDecimal(preOpen, "totalTradedVolume");
                if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0) {
                    totalTradedVolume = parseBigDecimal(preOpen, "volume");
                }
            }
        }
        
        // Calculate totalTradedValue first (needed for volume calculation fallback)
        BigDecimal totalTradedValue = BigDecimal.ZERO;
        // Try tradeInfo first
        if (tradeInfoNode != null) {
            totalTradedValue = parseBigDecimalFromMultipleLocations(root, tradeInfoNode, "totalTradedValue",
                new String[]{"totalTradedValue", "tradedValue", "turnover"});
            // If value in tradeInfo is in crores (small decimal values), convert to actual value
            if (totalTradedValue.compareTo(BigDecimal.ZERO) > 0 && totalTradedValue.compareTo(BigDecimal.valueOf(10000)) < 0) {
                // Likely in crores, convert: multiply by 10,000,000
                totalTradedValue = totalTradedValue.multiply(BigDecimal.valueOf(10_000_000));
                System.out.println("ComprehensiveNseDataService - Converted traded value from crores to actual value: " + totalTradedValue);
            }
        }
        // Fallback to other locations
        if (totalTradedValue.compareTo(BigDecimal.ZERO) == 0) {
            totalTradedValue = parseBigDecimalFromMultipleLocations(root, info, "totalTradedValue",
                new String[]{"totalTradedValue", "tradedValue", "turnover"});
        }
        
        // If volume is still zero, try calculating from traded value and price
        // This gives approximate daily volume from today's traded value
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0 && totalTradedValue.compareTo(BigDecimal.ZERO) > 0 
            && lastPrice.compareTo(BigDecimal.ZERO) > 0) {
            // Volume = Total Traded Value / Current Price (approximate daily volume)
            totalTradedVolume = totalTradedValue.divide(lastPrice, 0, RoundingMode.HALF_UP);
            System.out.println("ComprehensiveNseDataService - Calculated daily volume from traded value for " + symbol + ": " + totalTradedVolume);
        }
        
        // If volume is still zero, try fetching live daily volume from historical data API (today's data)
        // This ensures we get the current trading day's cumulative volume
        if (totalTradedVolume.compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("ComprehensiveNseDataService - Volume is zero, fetching live daily volume for " + symbol);
            try {
                totalTradedVolume = fetchDailyVolume(symbol);
            } catch (Exception e) {
                System.err.println("ComprehensiveNseDataService - Failed to fetch live daily volume: " + e.getMessage());
                // Volume remains zero if fetch fails
            }
        }
        
        System.out.println("ComprehensiveNseDataService - Final live daily volume extracted for " + symbol + ": " + totalTradedVolume);
        BigDecimal marketCap = parseBigDecimalFromMultipleLocations(root, info, "marketCap",
            new String[]{"marketCap", "marketCapitalization", "mCap"});
        
        // Calculate change and change percent
        BigDecimal change = lastPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ? 
            change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : 
            BigDecimal.ZERO;

        // Parse 52-week highs/lows - check metadata and info sections
        JsonNode metadata = root.get("metadata");
        if (metadata == null && data != null) {
            metadata = data.get("metadata");
        }
        
        BigDecimal high52Week = parseBigDecimalFromMultipleLocations(root, metadata != null ? metadata : info, "high52Week",
            new String[]{"high52Week", "52WeekHigh", "weekHigh52", "high52"});
        BigDecimal low52Week = parseBigDecimalFromMultipleLocations(root, metadata != null ? metadata : info, "low52Week",
            new String[]{"low52Week", "52WeekLow", "weekLow52", "low52"});
        BigDecimal close = parseBigDecimal(info, "close");
        BigDecimal yearHigh = parseBigDecimalFromMultipleLocations(root, metadata != null ? metadata : info, "yearHigh",
            new String[]{"yearHigh", "yearlyHigh", "high"});
        BigDecimal yearLow = parseBigDecimalFromMultipleLocations(root, metadata != null ? metadata : info, "yearLow",
            new String[]{"yearLow", "yearlyLow", "low"});
        
        // Parse company information - try multiple locations (info, metadata, data/info)
        JsonNode companyInfo = root.get("info");
        if (companyInfo == null && data != null) {
            companyInfo = data.get("info");
        }
        if (companyInfo == null) {
            companyInfo = metadata; // Use metadata as companyInfo if info not found
        }
        
        String companyName = symbol;
        String industry = "";
        String sector = "";
        String isin = "";
        String series = "";
        String faceValue = "";
        
        // Try to extract from companyInfo first
        if (companyInfo != null) {
            companyName = extractTextValueMulti(companyInfo, symbol, "companyName", "company", "name");
            industry = extractTextValueMulti(companyInfo, "", "industry", "industryName");
            sector = extractTextValueMulti(companyInfo, "", "sector", "sectorName");
            isin = extractTextValueMulti(companyInfo, "", "isin", "ISIN");
            series = extractTextValueMulti(companyInfo, "", "series", "seriesName");
            faceValue = extractTextValueMulti(companyInfo, "", "faceValue", "face", "faceVal");
        }
        
        // Fallback to info or metadata section if companyInfo not found
        if (companyName.equals(symbol)) {
            companyName = extractTextValueMulti(info, symbol, "companyName", "company", "name");
            if (companyName.equals(symbol) && metadata != null) {
                companyName = extractTextValueMulti(metadata, symbol, "companyName", "company", "name");
            }
            industry = extractTextValueMulti(info, industry, "industry", "industryName");
            if (industry.isEmpty() && metadata != null) {
                industry = extractTextValueMulti(metadata, "", "industry", "industryName");
            }
            sector = extractTextValueMulti(info, sector, "sector", "sectorName");
            if (sector.isEmpty() && metadata != null) {
                sector = extractTextValueMulti(metadata, "", "sector", "sectorName");
            }
            isin = extractTextValueMulti(info, isin, "isin", "ISIN");
            if (isin.isEmpty() && metadata != null) {
                isin = extractTextValueMulti(metadata, "", "isin", "ISIN");
            }
            series = extractTextValueMulti(info, series, "series", "seriesName");
            if (series.isEmpty() && metadata != null) {
                series = extractTextValueMulti(metadata, "", "series", "seriesName");
            }
            faceValue = extractTextValueMulti(info, faceValue, "faceValue", "face", "faceVal");
            if (faceValue.isEmpty() && metadata != null) {
                faceValue = extractTextValueMulti(metadata, "", "faceValue", "face", "faceVal");
            }
        }
        
        // Also try root level
        if (companyName.equals(symbol) && root.has("companyName")) {
            companyName = extractTextValue(root, "companyName", symbol);
        }
        
        // Parse financial metrics - check metadata section primarily
        JsonNode metricsNode = metadata != null ? metadata : info;
        BigDecimal pe = parseBigDecimalFromMultipleLocations(root, metricsNode, "pe",
            new String[]{"pe", "priceToEarnings", "pE", "PE"});
        BigDecimal pb = parseBigDecimalFromMultipleLocations(root, metricsNode, "pb",
            new String[]{"pb", "priceToBook", "pB", "PB", "priceToBookValue"});
        BigDecimal dividendYield = parseBigDecimalFromMultipleLocations(root, metricsNode, "dividendYield",
            new String[]{"dividendYield", "dividend", "yield", "divYield"});
        BigDecimal bookValue = parseBigDecimalFromMultipleLocations(root, metricsNode, "bookValue",
            new String[]{"bookValue", "book", "bv", "bookVal"});
        BigDecimal eps = parseBigDecimalFromMultipleLocations(root, metricsNode, "eps",
            new String[]{"eps", "earningsPerShare", "earnings", "EPS"});

        // Parse market depth if available
        List<NseStockData.BidAsk> bid = parseMarketDepth(root, "bid");
        List<NseStockData.BidAsk> ask = parseMarketDepth(root, "ask");

        return NseStockData.builder()
                .symbol(symbol)
                .companyName(companyName)
                .lastPrice(lastPrice.setScale(2, RoundingMode.HALF_UP))
                .open(open.setScale(2, RoundingMode.HALF_UP))
                .dayHigh(dayHigh.setScale(2, RoundingMode.HALF_UP))
                .dayLow(dayLow.setScale(2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(2, RoundingMode.HALF_UP))
                .totalTradedVolume(totalTradedVolume.setScale(0, RoundingMode.HALF_UP)) // Live daily volume (cumulative for current trading day)
                .totalTradedValue(totalTradedValue.setScale(2, RoundingMode.HALF_UP))
                .marketCap(marketCap.setScale(2, RoundingMode.HALF_UP))
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .changePercent(changePercent.setScale(2, RoundingMode.HALF_UP))
                .high52Week(high52Week.setScale(2, RoundingMode.HALF_UP))
                .low52Week(low52Week.setScale(2, RoundingMode.HALF_UP))
                .close(close.setScale(2, RoundingMode.HALF_UP))
                .yearHigh(yearHigh.setScale(2, RoundingMode.HALF_UP))
                .yearLow(yearLow.setScale(2, RoundingMode.HALF_UP))
                .industry(industry)
                .sector(sector)
                .isin(isin)
                .series(series)
                .faceValue(faceValue)
                .pe(pe.setScale(2, RoundingMode.HALF_UP))
                .pb(pb.setScale(2, RoundingMode.HALF_UP))
                .dividendYield(dividendYield.setScale(2, RoundingMode.HALF_UP))
                .bookValue(bookValue.setScale(2, RoundingMode.HALF_UP))
                .eps(eps.setScale(2, RoundingMode.HALF_UP))
                .bid(bid)
                .ask(ask)
                .timestamp(LocalDateTime.now())
                .dataSource("NSE Comprehensive Data")
                .build();
    }

    private BigDecimal parseBigDecimal(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            try {
                String value = node.get(fieldName).asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return new BigDecimal(value);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing BigDecimal for field " + fieldName + ": " + e.getMessage());
            }
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Parses BigDecimal from multiple possible locations and field names
     */
    private BigDecimal parseBigDecimalFromMultipleLocations(JsonNode root, JsonNode primary, String primaryField, String[] alternativeFields) {
        // Try primary node with primary field name
        if (primary != null) {
            BigDecimal value = parseBigDecimal(primary, primaryField);
            if (value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            // Try alternative field names in primary node
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = parseBigDecimal(primary, altField);
                    if (value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        // Try root level
        if (root != null) {
            BigDecimal value = parseBigDecimal(root, primaryField);
            if (value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = parseBigDecimal(root, altField);
                    if (value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        // Try data section
        JsonNode dataNode = root != null ? root.get("data") : null;
        if (dataNode != null) {
            BigDecimal value = parseBigDecimal(dataNode, primaryField);
            if (value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = parseBigDecimal(dataNode, altField);
                    if (value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Gets all keys from a JSON node for debugging
     */
    private java.util.List<String> getNodeKeys(JsonNode node) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (node != null) {
            node.fieldNames().forEachRemaining(keys::add);
        }
        return keys;
    }
    
    /**
     * Extracts a text value from JSON node with fallback
     */
    private String extractTextValue(JsonNode node, String fieldName, String defaultValue) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            try {
                String value = node.get(fieldName).asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return value;
                }
            } catch (Exception e) {
                // Ignore and return default
            }
        }
        return defaultValue;
    }
    
    /**
     * Extracts text value trying multiple field names
     */
    private String extractTextValueMulti(JsonNode node, String defaultValue, String... fieldNames) {
        if (node == null) {
            return defaultValue;
        }
        for (String fieldName : fieldNames) {
            String value = extractTextValue(node, fieldName, null);
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                return value;
            }
        }
        return defaultValue;
    }

    private List<NseStockData.BidAsk> parseMarketDepth(JsonNode root, String depthType) {
        List<NseStockData.BidAsk> depthList = new ArrayList<>();
        
        // Try multiple locations for market depth
        JsonNode marketDepth = root.get("marketDepth");
        if (marketDepth == null) {
            JsonNode dataNode = root.get("data");
            if (dataNode != null) {
                marketDepth = dataNode.get("marketDepth");
            }
        }
        
        if (marketDepth != null && marketDepth.has(depthType)) {
            JsonNode depthArray = marketDepth.get(depthType);
            if (depthArray != null && depthArray.isArray()) {
                for (JsonNode item : depthArray) {
                    if (item != null && (item.has("price") || item.has("price1")) && 
                        (item.has("quantity") || item.has("quantity1"))) {
                        BigDecimal price = parseBigDecimal(item, "price");
                        if (price.compareTo(BigDecimal.ZERO) == 0) {
                            price = parseBigDecimal(item, "price1");
                        }
                        BigDecimal quantity = parseBigDecimal(item, "quantity");
                        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                            quantity = parseBigDecimal(item, "quantity1");
                        }
                        Integer orders = item.has("orders") ? item.get("orders").asInt() : 
                            (item.has("orderCount") ? item.get("orderCount").asInt() : 0);
                        
                        if (price.compareTo(BigDecimal.ZERO) > 0 && quantity.compareTo(BigDecimal.ZERO) > 0) {
                            depthList.add(NseStockData.BidAsk.builder()
                                    .price(price)
                                    .quantity(quantity)
                                    .orders(orders)
                                    .build());
                        }
                    }
                }
            }
        }
        
        return depthList;
    }
    
    /**
     * Fetches volume from multiple timeframes (daily, weekly, monthly) to get real-time volume
     * Tries historical data APIs when current day volume is not available
     * NOTE: This method is deprecated in favor of fetchDailyVolume() which prioritizes live daily volume
     */
    @Deprecated
    private BigDecimal fetchVolumeFromMultipleTimeframes(String symbol) {
        BigDecimal volume = BigDecimal.ZERO;
        
        // 1. Try to fetch daily volume from historical data API (today's data)
        try {
            volume = fetchDailyVolume(symbol);
            if (volume.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("ComprehensiveNseDataService - Got daily volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Failed to fetch daily volume: " + e.getMessage());
        }
        
        // 2. Try to fetch weekly average volume
        try {
            volume = fetchWeeklyAverageVolume(symbol);
            if (volume.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("ComprehensiveNseDataService - Got weekly average volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Failed to fetch weekly volume: " + e.getMessage());
        }
        
        // 3. Try to fetch monthly average volume
        try {
            volume = fetchMonthlyAverageVolume(symbol);
            if (volume.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("ComprehensiveNseDataService - Got monthly average volume for " + symbol + ": " + volume);
                return volume;
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Failed to fetch monthly volume: " + e.getMessage());
        }
        
        System.err.println("ComprehensiveNseDataService - Could not fetch volume from any timeframe for " + symbol);
        return BigDecimal.ZERO;
    }
    
    /**
     * Fetches LIVE DAILY VOLUME from NSE historical data API for the current trading day
     * This returns the cumulative volume traded so far today (real-time during market hours)
     * Tries multiple NSE endpoints and formats to get today's live volume
     * 
     * @param symbol Stock symbol
     * @return Live daily volume (cumulative volume for current trading day)
     */
    private BigDecimal fetchDailyVolume(String symbol) throws Exception {
        // Try multiple NSE historical endpoints
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter nseDateFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = today.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        // Try endpoint 1: /api/historical/equity/{symbol}
        String url1 = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        BigDecimal volume = tryFetchVolumeFromUrl(symbol, url1, "historical/equity");
        if (volume.compareTo(BigDecimal.ZERO) > 0) {
            return volume;
        }
        
        // Try endpoint 2: /api/historical/equity-data/{symbol} (alternative format)
        String url2 = String.format("https://www.nseindia.com/api/historical/equity-data/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        volume = tryFetchVolumeFromUrl(symbol, url2, "historical/equity-data");
        if (volume.compareTo(BigDecimal.ZERO) > 0) {
            return volume;
        }
        
        // Try endpoint 3: /api/chart-databyindex with symbol
        String url3 = String.format("https://www.nseindia.com/api/chart-databyindex?index=%s", symbol);
        volume = tryFetchVolumeFromChartData(symbol, url3);
        if (volume.compareTo(BigDecimal.ZERO) > 0) {
            return volume;
        }
        
        System.err.println("ComprehensiveNseDataService - All daily volume endpoints failed for " + symbol);
        return BigDecimal.ZERO;
    }
    
    /**
     * Helper method to fetch volume from a URL
     */
    private BigDecimal tryFetchVolumeFromUrl(String symbol, String url, String endpointName) {
        try {
            System.out.println("ComprehensiveNseDataService - Trying " + endpointName + " for " + symbol);
            HttpHeaders headers = createNseHeaders(symbol);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Try different response structures
                if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                    JsonNode firstDay = root.get("data").get(0);
                    
                    // Try all possible volume field names
                    String[] volumeFields = {"CH_TOT_TRADED_QTY", "TOTTRDQTY", "volume", "TOTALTRADEDQTY", 
                                             "TOTAL_TRADED_QTY", "tradedVolume", "totalVolume"};
                    for (String field : volumeFields) {
                        if (firstDay.has(field) && !firstDay.get(field).isNull()) {
                            BigDecimal vol = parseBigDecimal(firstDay, field);
                            if (vol.compareTo(BigDecimal.ZERO) > 0) {
                                System.out.println("ComprehensiveNseDataService - Found volume in " + field + ": " + vol);
                                return vol;
                            }
                        }
                    }
                }
                
                // Try root level fields
                String[] volumeFields = {"volume", "totalTradedVolume", "tradedVolume", "CH_TOT_TRADED_QTY"};
                for (String field : volumeFields) {
                    if (root.has(field) && !root.get(field).isNull()) {
                        BigDecimal vol = parseBigDecimal(root, field);
                        if (vol.compareTo(BigDecimal.ZERO) > 0) {
                            System.out.println("ComprehensiveNseDataService - Found volume in root." + field + ": " + vol);
                            return vol;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Error in " + endpointName + ": " + e.getMessage());
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Helper method to fetch volume from chart data endpoint
     */
    private BigDecimal tryFetchVolumeFromChartData(String symbol, String url) {
        try {
            System.out.println("ComprehensiveNseDataService - Trying chart-databyindex for " + symbol);
            HttpHeaders headers = createNseHeaders(symbol);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Chart data might have different structure
                if (root.has("grapthData") && root.get("grapthData").isArray() && root.get("grapthData").size() > 0) {
                    JsonNode latest = root.get("grapthData").get(root.get("grapthData").size() - 1);
                    if (latest.isArray() && latest.size() >= 5) {
                        // Volume might be in the array: [timestamp, open, high, low, close, volume]
                        BigDecimal vol = new BigDecimal(latest.get(5).asText());
                        if (vol.compareTo(BigDecimal.ZERO) > 0) {
                            return vol;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Error in chart-databyindex: " + e.getMessage());
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Fetches weekly average volume from NSE historical data API (last 7 days)
     */
    private BigDecimal fetchWeeklyAverageVolume(String symbol) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekAgo = today.minusDays(7);
        // NSE uses DD-MMM-YYYY format
        java.time.format.DateTimeFormatter nseDateFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = weekAgo.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        System.out.println("ComprehensiveNseDataService - Fetching weekly volume from: " + url);
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray()) {
                    BigDecimal totalVolume = BigDecimal.ZERO;
                    int count = 0;
                    
                    for (JsonNode day : root.get("data")) {
                        // Try all possible volume field names
                        String[] volumeFields = {"CH_TOT_TRADED_QTY", "TOTTRDQTY", "volume", "TOTALTRADEDQTY", 
                                                 "TOTAL_TRADED_QTY", "tradedVolume", "totalVolume"};
                        BigDecimal dayVolume = BigDecimal.ZERO;
                        
                        for (String field : volumeFields) {
                            if (day.has(field) && !day.get(field).isNull()) {
                                dayVolume = parseBigDecimal(day, field);
                                if (dayVolume.compareTo(BigDecimal.ZERO) > 0) {
                                    break;
                                }
                            }
                        }
                        
                        if (dayVolume.compareTo(BigDecimal.ZERO) > 0) {
                            totalVolume = totalVolume.add(dayVolume);
                            count++;
                        }
                    }
                    
                    if (count > 0) {
                        BigDecimal avg = totalVolume.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                        System.out.println("ComprehensiveNseDataService - Weekly average volume: " + avg);
                        return avg;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Error fetching weekly volume: " + e.getMessage());
            throw e;
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Fetches monthly average volume from NSE historical data API (last 30 days)
     */
    private BigDecimal fetchMonthlyAverageVolume(String symbol) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate monthAgo = today.minusDays(30);
        // NSE uses DD-MMM-YYYY format
        java.time.format.DateTimeFormatter nseDateFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = monthAgo.format(nseDateFormat);
        String toDate = today.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        System.out.println("ComprehensiveNseDataService - Fetching monthly volume from: " + url);
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray()) {
                    BigDecimal totalVolume = BigDecimal.ZERO;
                    int count = 0;
                    
                    for (JsonNode day : root.get("data")) {
                        // Try all possible volume field names
                        String[] volumeFields = {"CH_TOT_TRADED_QTY", "TOTTRDQTY", "volume", "TOTALTRADEDQTY", 
                                                 "TOTAL_TRADED_QTY", "tradedVolume", "totalVolume"};
                        BigDecimal dayVolume = BigDecimal.ZERO;
                        
                        for (String field : volumeFields) {
                            if (day.has(field) && !day.get(field).isNull()) {
                                dayVolume = parseBigDecimal(day, field);
                                if (dayVolume.compareTo(BigDecimal.ZERO) > 0) {
                                    break;
                                }
                            }
                        }
                        
                        if (dayVolume.compareTo(BigDecimal.ZERO) > 0) {
                            totalVolume = totalVolume.add(dayVolume);
                            count++;
                        }
                    }
                    
                    if (count > 0) {
                        BigDecimal avg = totalVolume.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                        System.out.println("ComprehensiveNseDataService - Monthly average volume: " + avg);
                        return avg;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ComprehensiveNseDataService - Error fetching monthly volume: " + e.getMessage());
            throw e;
        }
        
        return BigDecimal.ZERO;
    }
}
