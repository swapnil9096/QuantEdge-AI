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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP (Model Context Protocol) Data Service
 * Fetches comprehensive live market data from NSE India website
 * Extracts all available stock details including price, volume, market depth, company info, and metrics
 */
@Service
public class McpDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache for NSE session cookies
    private final Map<String, String> nseSessionCache = new ConcurrentHashMap<>();
    private LocalDateTime lastNseSessionTime = LocalDateTime.now().minusHours(1);
    
    @Value("${api.mcp.enabled:false}")
    private boolean mcpEnabled;
    
    @Value("${api.mcp.base-url:}")
    private String mcpBaseUrl;
    
    @Value("${api.mcp.endpoint-pattern:{symbol}}")
    private String endpointPattern;
    
    @Value("${api.mcp.api-key:}")
    private String mcpApiKey;
    
    @Value("${api.mcp.api-key-header:X-API-Key}")
    private String apiKeyHeader;
    
    @Value("${api.mcp.timeout:5000}")
    private int timeoutMs;
    
    // JSON path mappings for different data fields
    @Value("${api.mcp.json-path.current-price:price}")
    private String currentPricePath;
    
    @Value("${api.mcp.json-path.open:open}")
    private String openPath;
    
    @Value("${api.mcp.json-path.high:high}")
    private String highPath;
    
    @Value("${api.mcp.json-path.low:low}")
    private String lowPath;
    
    @Value("${api.mcp.json-path.previous-close:previousClose}")
    private String previousClosePath;
    
    @Value("${api.mcp.json-path.volume:volume}")
    private String volumePath;
    
    @Value("${api.mcp.json-path.symbol:symbol}")
    private String symbolPath;

    public McpDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches comprehensive live market data for a given symbol from NSE India
     * 
     * @param symbol Stock symbol to fetch data for
     * @return LiveMarketData object containing the market data
     * @throws Exception if data cannot be fetched or parsed
     */
    public LiveMarketData getLiveMarketData(String symbol) throws Exception {
        if (!mcpEnabled) {
            throw new IllegalStateException("MCP data service is not enabled");
        }
        
        // For NSE, use the comprehensive endpoint
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
        System.out.println("Fetching comprehensive NSE data from: " + url);
        
        // Establish NSE session first
        establishNseSession();
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseNseComprehensiveResponse(symbol, response.getBody());
            } else {
                throw new RuntimeException("NSE API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error fetching NSE data for " + symbol + ": " + e.getMessage());
            throw new Exception("Failed to fetch data from NSE: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fetches comprehensive NSE stock data including all available fields
     * 
     * @param symbol Stock symbol to fetch data for
     * @return NseStockData object containing comprehensive stock information
     * @throws Exception if data cannot be fetched or parsed
     */
    public NseStockData getComprehensiveNseData(String symbol) throws Exception {
        if (!mcpEnabled) {
            throw new IllegalStateException("MCP data service is not enabled");
        }
        
        String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
        System.out.println("Fetching comprehensive NSE data from: " + url);
        
        // Establish NSE session first
        establishNseSession();
        
        HttpHeaders headers = createNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseFullNseData(symbol, response.getBody());
            } else {
                throw new RuntimeException("NSE API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error fetching comprehensive NSE data for " + symbol + ": " + e.getMessage());
            throw new Exception("Failed to fetch comprehensive data from NSE: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the full URL for the API request
     */
    private String buildUrl(String symbol) {
        String url = mcpBaseUrl;
        
        // Replace {symbol} placeholder in URL or endpoint pattern
        if (url.contains("{symbol}")) {
            url = url.replace("{symbol}", symbol);
        } else if (endpointPattern.contains("{symbol}")) {
            String endpoint = endpointPattern.replace("{symbol}", symbol);
            url = url.endsWith("/") ? url + endpoint : url + "/" + endpoint;
        } else {
            // If no pattern, append symbol as query parameter
            url = url + (url.contains("?") ? "&" : "?") + "symbol=" + symbol;
        }
        
        return url;
    }

    /**
     * Creates HTTP headers specifically for NSE API requests
     */
    private HttpHeaders createNseHeaders(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        // Do NOT set Accept-Encoding - RestTemplate doesn't handle gzip decompression by default
        // This prevents NSE from sending compressed responses
        headers.set("Connection", "keep-alive");
        headers.set("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol);
        headers.set("Origin", "https://www.nseindia.com");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        
        // Add session cookies if available
        if (!nseSessionCache.isEmpty()) {
            StringBuilder cookieHeader = new StringBuilder();
            nseSessionCache.forEach((key, value) -> {
                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                cookieHeader.append(key).append("=").append(value);
            });
            headers.set("Cookie", cookieHeader.toString());
        }
        
        return headers;
    }
    
    /**
     * Establishes a session with NSE by visiting the homepage to get cookies
     */
    private void establishNseSession() throws Exception {
        // Check if we need to refresh the session (every 30 minutes)
        if (lastNseSessionTime.isAfter(LocalDateTime.now().minusMinutes(30))) {
            return; // Session still valid
        }
        
        try {
            // Visit the main NSE page to get session cookies
            String mainUrl = "https://www.nseindia.com/";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.5");
            // Do NOT set Accept-Encoding to prevent gzip compression
            headers.set("Connection", "keep-alive");
            headers.set("Upgrade-Insecure-Requests", "1");
            
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
            
            lastNseSessionTime = LocalDateTime.now();
            System.out.println("NSE session established with cookies: " + nseSessionCache.size());
            
        } catch (Exception e) {
            System.err.println("Failed to establish NSE session: " + e.getMessage());
            // Continue without session - some endpoints might still work
        }
    }

    /**
     * Parses the JSON response from the MCP server
     */
    private LiveMarketData parseResponse(String symbol, String responseBody) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // Try to extract data using configured JSON paths
            BigDecimal currentPrice = extractValue(root, currentPricePath);
            BigDecimal open = extractValue(root, openPath);
            BigDecimal high = extractValue(root, highPath);
            BigDecimal low = extractValue(root, lowPath);
            BigDecimal previousClose = extractValue(root, previousClosePath);
            BigDecimal volume = extractValue(root, volumePath);
            
            // Validate that we have at least current price
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
                throw new RuntimeException("Could not extract current price from MCP response. Check JSON path configuration.");
            }
            
            // Set defaults for missing values
            if (previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
                previousClose = currentPrice; // Use current price as fallback
            }
            if (open == null || open.compareTo(BigDecimal.ZERO) == 0) {
                open = previousClose;
            }
            if (high == null || high.compareTo(BigDecimal.ZERO) == 0) {
                high = currentPrice.max(open);
            }
            if (low == null || low.compareTo(BigDecimal.ZERO) == 0) {
                low = currentPrice.min(open);
            }
            if (volume == null) {
                volume = BigDecimal.ZERO;
            }
            
            // Calculate change and change percent
            BigDecimal change = currentPrice.subtract(previousClose);
            BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ?
                change.divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
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
                    .dataSource("MCP Server: " + mcpBaseUrl)
                    .build();
                    
        } catch (Exception e) {
            System.err.println("Error parsing MCP response: " + e.getMessage());
            System.err.println("Response body: " + responseBody);
            throw new Exception("Failed to parse MCP response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a BigDecimal value from JSON using a path (supports nested paths like "data.price")
     */
    private BigDecimal extractValue(JsonNode root, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = path.split("\\.");
            JsonNode node = root;
            
            for (String part : parts) {
                if (node == null) {
                    return null;
                }
                
                if (node.isArray() && part.matches("\\d+")) {
                    int index = Integer.parseInt(part);
                    node = node.get(index);
                } else {
                    node = node.get(part);
                }
            }
            
            if (node != null && !node.isNull()) {
                if (node.isNumber()) {
                    return new BigDecimal(node.asText());
                } else if (node.isTextual()) {
                    try {
                        return new BigDecimal(node.asText());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            // Path not found or invalid, return null
            System.err.println("Could not extract value from path: " + path + " - " + e.getMessage());
        }
        
        return null;
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
    
    /**
     * Extracts BigDecimal value from multiple possible locations and field names
     */
    private BigDecimal extractValueFromMultipleLocations(JsonNode root, JsonNode primary, String primaryField, String[] alternativeFields) {
        // Try primary node with primary field name
        if (primary != null) {
            BigDecimal value = extractValue(primary, primaryField);
            if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            // Try alternative field names in primary node
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = extractValue(primary, altField);
                    if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        // Try root level
        if (root != null) {
            BigDecimal value = extractValue(root, primaryField);
            if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = extractValue(root, altField);
                    if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        // Try data section
        JsonNode dataNode = root != null ? root.get("data") : null;
        if (dataNode != null) {
            BigDecimal value = extractValue(dataNode, primaryField);
            if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                return value;
            }
            for (String altField : alternativeFields) {
                if (!altField.equals(primaryField)) {
                    value = extractValue(dataNode, altField);
                    if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                        return value;
                    }
                }
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Parses comprehensive NSE response for basic LiveMarketData
     */
    private LiveMarketData parseNseComprehensiveResponse(String symbol, String responseBody) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // Try priceInfo first (newer NSE structure)
            JsonNode priceInfo = root.get("priceInfo");
            if (priceInfo != null && priceInfo.has("lastPrice")) {
                return parseNsePriceInfo(symbol, priceInfo, root);
            }
            
            // Fallback to data section (legacy structure)
            JsonNode data = root.get("data");
            if (data != null && data.has("lastPrice")) {
                return parseNseDataSection(symbol, data);
            }
            
            throw new RuntimeException("Could not find price information in NSE response");
                    
        } catch (Exception e) {
            System.err.println("Error parsing NSE response: " + e.getMessage());
            throw new Exception("Failed to parse NSE response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parses NSE priceInfo section (newer structure)
     */
    private LiveMarketData parseNsePriceInfo(String symbol, JsonNode priceInfo, JsonNode root) {
        BigDecimal currentPrice = extractValue(priceInfo, "lastPrice");
        BigDecimal open = extractValue(priceInfo, "open");
        BigDecimal previousClose = extractValue(priceInfo, "previousClose");
        
        if (previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            previousClose = extractValue(priceInfo, "close");
        }
        
        BigDecimal high = currentPrice;
        BigDecimal low = currentPrice;
        JsonNode intra = priceInfo.get("intraDayHighLow");
        if (intra != null) {
            if (intra.has("max")) high = extractValue(intra, "max");
            if (intra.has("min")) low = extractValue(intra, "min");
        }
        
        BigDecimal volume = extractValue(priceInfo, "totalTradedVolume");
        if (volume == null || volume.compareTo(BigDecimal.ZERO) == 0) {
            JsonNode preOpen = root.get("preOpenMarket");
            if (preOpen != null) {
                volume = extractValue(preOpen, "totalTradedVolume");
            }
        }
        
        BigDecimal change = previousClose.compareTo(BigDecimal.ZERO) == 0 ? 
            BigDecimal.ZERO : currentPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ?
            change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
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
                .dataSource("NSE Comprehensive Data (MCP)")
                .build();
    }
    
    /**
     * Parses NSE data section (legacy structure)
     */
    private LiveMarketData parseNseDataSection(String symbol, JsonNode data) {
        BigDecimal currentPrice = extractValue(data, "lastPrice");
        BigDecimal open = extractValue(data, "open");
        BigDecimal high = extractValue(data, "dayHigh");
        BigDecimal low = extractValue(data, "dayLow");
        BigDecimal previousClose = extractValue(data, "previousClose");
        BigDecimal volume = extractValue(data, "totalTradedVolume");
        
        BigDecimal change = currentPrice.subtract(previousClose);
        BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) > 0 ?
            change.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
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
                .dataSource("NSE Comprehensive Data (MCP)")
                .build();
    }
    
    /**
     * Parses full NSE data including all available fields
     */
    private NseStockData parseFullNseData(String symbol, String responseBody) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // Log response structure for debugging
            System.out.println("NSE Response structure for " + symbol + ":");
            java.util.List<String> fieldNames = new java.util.ArrayList<>();
            root.fieldNames().forEachRemaining(fieldNames::add);
            System.out.println("Root keys: " + fieldNames);
            
            // Check for error response first
            if (root.has("error") || root.has("message")) {
                String errorMsg = root.has("error") ? root.get("error").asText() : 
                    (root.has("message") ? root.get("message").asText() : "Unknown error");
                throw new RuntimeException("NSE API returned error: " + errorMsg);
            }
            
            // Try multiple possible structures
            JsonNode priceInfo = root.get("priceInfo");
            JsonNode data = root.get("data");
            JsonNode info = null;
            
            // Priority: data > priceInfo > root (if fields are at root level)
            // Check if data exists and has any price-related fields
            if (data != null && !data.isNull() && 
                (data.has("lastPrice") || data.has("priceInfo") || data.has("open") || 
                 data.has("dayHigh") || data.has("dayLow") || data.has("previousClose"))) {
                // Check if data has nested priceInfo
                if (data.has("priceInfo") && !data.get("priceInfo").isNull()) {
                    info = data.get("priceInfo");
                } else {
                    info = data;
                }
                System.out.println("Using 'data' section for " + symbol);
            } else if (priceInfo != null && !priceInfo.isNull()) {
                info = priceInfo;
                System.out.println("Using 'priceInfo' section for " + symbol);
            } else if (root.has("lastPrice") || root.has("open")) {
                // Try root level if it has price fields directly
                info = root;
                System.out.println("Using root level for " + symbol);
            }
            
            if (info == null) {
                // Log the actual response structure for debugging
                System.err.println("McpDataService - ERROR: No valid data section found for " + symbol);
                System.err.println("McpDataService - Available root keys: " + fieldNames);
                System.err.println("McpDataService - Data node exists: " + (data != null));
                System.err.println("McpDataService - PriceInfo node exists: " + (priceInfo != null));
                
                // Try to log the actual response structure
                System.err.println("McpDataService - Full NSE response for " + symbol + " (first 2000 chars):");
                System.err.println(responseBody.length() > 2000 ? 
                    responseBody.substring(0, 2000) + "..." : responseBody);
                
                // If we have data or priceInfo but they're empty, try to use them anyway
                if (data != null && !data.isNull()) {
                    System.err.println("McpDataService - Data node is not null, trying to use it anyway");
                    info = data;
                } else if (priceInfo != null && !priceInfo.isNull()) {
                    System.err.println("McpDataService - PriceInfo node is not null, trying to use it anyway");
                    info = priceInfo;
                } else {
                    throw new RuntimeException("No valid data section found in NSE response. Available keys: " + fieldNames + 
                        ". Response preview: " + (responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody));
                }
            }
            
            // Parse basic price information
            BigDecimal lastPrice = extractValue(info, "lastPrice");
            BigDecimal open = extractValue(info, "open");
            BigDecimal dayHigh = extractValue(info, "dayHigh");
            BigDecimal dayLow = extractValue(info, "dayLow");
            BigDecimal previousClose = extractValue(info, "previousClose");
            
            // Handle intraday high/low from newer structure
            if (dayHigh == null || dayHigh.compareTo(BigDecimal.ZERO) == 0) {
                JsonNode intra = info.get("intraDayHighLow");
                if (intra != null) {
                    dayHigh = extractValue(intra, "max");
                    dayLow = extractValue(intra, "min");
                }
            }
            
            // Parse volume and market cap - check multiple locations
            BigDecimal totalTradedVolume = extractValueFromMultipleLocations(root, info, "totalTradedVolume",
                new String[]{"totalTradedVolume", "volume", "tradedVolume"});
            BigDecimal totalTradedValue = extractValueFromMultipleLocations(root, info, "totalTradedValue",
                new String[]{"totalTradedValue", "tradedValue", "turnover"});
            BigDecimal marketCap = extractValueFromMultipleLocations(root, info, "marketCap",
                new String[]{"marketCap", "marketCapitalization", "mCap"});
            
            // Calculate change and change percent
            BigDecimal change = (lastPrice != null && previousClose != null) ? 
                lastPrice.subtract(previousClose) : BigDecimal.ZERO;
            BigDecimal changePercent = (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) ? 
                change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : 
                BigDecimal.ZERO;

            // Parse 52-week highs/lows - check metadata and info sections
            JsonNode metadata = root.get("metadata");
            if (metadata == null && data != null) {
                metadata = data.get("metadata");
            }
            
            BigDecimal high52Week = extractValueFromMultipleLocations(root, metadata != null ? metadata : info, "high52Week",
                new String[]{"high52Week", "52WeekHigh", "weekHigh52", "high52"});
            BigDecimal low52Week = extractValueFromMultipleLocations(root, metadata != null ? metadata : info, "low52Week",
                new String[]{"low52Week", "52WeekLow", "weekLow52", "low52"});
            BigDecimal close = extractValue(info, "close");
            BigDecimal yearHigh = extractValueFromMultipleLocations(root, metadata != null ? metadata : info, "yearHigh",
                new String[]{"yearHigh", "yearlyHigh", "high"});
            BigDecimal yearLow = extractValueFromMultipleLocations(root, metadata != null ? metadata : info, "yearLow",
                new String[]{"yearLow", "yearlyLow", "low"});
            
            // Ensure null values become BigDecimal.ZERO
            if (lastPrice == null) lastPrice = BigDecimal.ZERO;
            if (open == null) open = BigDecimal.ZERO;
            if (dayHigh == null) dayHigh = BigDecimal.ZERO;
            if (dayLow == null) dayLow = BigDecimal.ZERO;
            if (previousClose == null) previousClose = BigDecimal.ZERO;
            if (totalTradedVolume == null) totalTradedVolume = BigDecimal.ZERO;
            if (totalTradedValue == null) totalTradedValue = BigDecimal.ZERO;
            if (marketCap == null) marketCap = BigDecimal.ZERO;
            if (high52Week == null) high52Week = BigDecimal.ZERO;
            if (low52Week == null) low52Week = BigDecimal.ZERO;
            if (close == null) close = BigDecimal.ZERO;
            if (yearHigh == null) yearHigh = BigDecimal.ZERO;
            if (yearLow == null) yearLow = BigDecimal.ZERO;
            
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
            BigDecimal pe = extractValueFromMultipleLocations(root, metricsNode, "pe",
                new String[]{"pe", "priceToEarnings", "pE", "PE"});
            BigDecimal pb = extractValueFromMultipleLocations(root, metricsNode, "pb",
                new String[]{"pb", "priceToBook", "pB", "PB", "priceToBookValue"});
            BigDecimal dividendYield = extractValueFromMultipleLocations(root, metricsNode, "dividendYield",
                new String[]{"dividendYield", "dividend", "yield", "divYield"});
            BigDecimal bookValue = extractValueFromMultipleLocations(root, metricsNode, "bookValue",
                new String[]{"bookValue", "book", "bv", "bookVal"});
            BigDecimal eps = extractValueFromMultipleLocations(root, metricsNode, "eps",
                new String[]{"eps", "earningsPerShare", "earnings", "EPS"});
            
            // Ensure null values become BigDecimal.ZERO
            if (pe == null) pe = BigDecimal.ZERO;
            if (pb == null) pb = BigDecimal.ZERO;
            if (dividendYield == null) dividendYield = BigDecimal.ZERO;
            if (bookValue == null) bookValue = BigDecimal.ZERO;
            if (eps == null) eps = BigDecimal.ZERO;
            
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
                    .totalTradedVolume(totalTradedVolume.setScale(0, RoundingMode.HALF_UP))
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
                    .dataSource("NSE Comprehensive Data (MCP)")
                    .build();
                    
        } catch (Exception e) {
            System.err.println("Error parsing full NSE data: " + e.getMessage());
            System.err.println("Response snippet: " + (responseBody != null && responseBody.length() > 500 ? 
                responseBody.substring(0, 500) : responseBody));
            throw new Exception("Failed to parse full NSE data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parses market depth (bid/ask) from NSE response
     */
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
                        BigDecimal price = extractValue(item, "price");
                        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
                            price = extractValue(item, "price1");
                        }
                        BigDecimal quantity = extractValue(item, "quantity");
                        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
                            quantity = extractValue(item, "quantity1");
                        }
                        Integer orders = item.has("orders") ? item.get("orders").asInt() : 
                            (item.has("orderCount") ? item.get("orderCount").asInt() : 0);
                        
                        if (price != null && price.compareTo(BigDecimal.ZERO) > 0 && 
                            quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
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
     * Inner class for LiveMarketData - matches the structure used by other services
     */
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

