package com.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to fetch and maintain valid NSE stock symbols dynamically
 * Fetches symbols from various NSE indices and caches them
 */
@Service
public class NseSymbolService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache for NSE session cookies
    private final Map<String, String> nseSessionCache = new ConcurrentHashMap<>();
    private LocalDateTime lastNseSessionTime = LocalDateTime.now().minusHours(1);
    
    // Cache for valid symbols
    private final Set<String> validSymbols = ConcurrentHashMap.newKeySet();
    private final Map<String, StockInfo> symbolInfoMap = new ConcurrentHashMap<>();
    private LocalDateTime lastSymbolFetchTime = LocalDateTime.now().minusDays(1);
    
    // NSE indices to fetch symbols from
    private static final String[] NSE_INDICES = {
        "NIFTY 50",
        "NIFTY NEXT 50",
        "NIFTY 100",
        "NIFTY 200",
        "NIFTY 500",
        "NIFTY MIDCAP 150",
        "NIFTY SMALLCAP 250",
        "NIFTY MIDCAP 50",
        "NIFTY SMALLCAP 50",
        "NIFTY SMALLCAP 100",
        "NIFTY TOTAL MARKET"
    };
    
    
    public NseSymbolService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @PostConstruct
    public void initialize() {
        // Fetch symbols on startup
        try {
            fetchAllValidSymbols();
        } catch (Exception e) {
            System.err.println("Failed to fetch symbols on startup: " + e.getMessage());
            // Continue with empty set - will be populated on first request
        }
    }
    
    /**
     * Fetches all valid symbols from NSE indices
     * Scheduled to run daily at 6 AM IST
     */
    @Scheduled(cron = "0 0 6 * * ?") // Daily at 6 AM
    public void refreshSymbols() {
        try {
            System.out.println("Refreshing NSE symbols cache...");
            fetchAllValidSymbols();
            System.out.println("Symbols cache refreshed. Total symbols: " + validSymbols.size());
        } catch (Exception e) {
            System.err.println("Failed to refresh symbols: " + e.getMessage());
        }
    }
    
    /**
     * Fetches symbols from all NSE indices
     */
    public void fetchAllValidSymbols() throws Exception {
        Set<String> newSymbols = new HashSet<>();
        Map<String, StockInfo> newSymbolInfoMap = new ConcurrentHashMap<>();
        
        establishNseSession();
        
        for (String index : NSE_INDICES) {
            try {
                Set<String> indexSymbols = fetchSymbolsFromIndex(index);
                newSymbols.addAll(indexSymbols);
                
                // Fetch basic info for symbols from this index
                for (String symbol : indexSymbols) {
                    if (!newSymbolInfoMap.containsKey(symbol)) {
                        StockInfo info = fetchSymbolInfo(symbol, index);
                        if (info != null) {
                            newSymbolInfoMap.put(symbol, info);
                        }
                    }
                }
                
                System.out.println("Fetched " + indexSymbols.size() + " symbols from " + index);
                // Small delay to avoid rate limiting
                Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("Failed to fetch symbols from index " + index + ": " + e.getMessage());
                // Continue with other indices
            }
        }
        
        // Update caches
        validSymbols.clear();
        validSymbols.addAll(newSymbols);
        symbolInfoMap.clear();
        symbolInfoMap.putAll(newSymbolInfoMap);
        lastSymbolFetchTime = LocalDateTime.now();
        
        System.out.println("Total unique symbols fetched: " + validSymbols.size());
    }
    
    /**
     * Fetches symbols from a specific NSE index
     */
    private Set<String> fetchSymbolsFromIndex(String index) throws Exception {
        String url = String.format("https://www.nseindia.com/api/equity-stockIndices?index=%s", 
            java.net.URLEncoder.encode(index, "UTF-8"));
        
        HttpHeaders headers = createNseHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                Set<String> symbols = new HashSet<>();
                
                // NSE returns data in "data" array
                if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode item : root.get("data")) {
                        if (item.has("symbol")) {
                            String symbol = item.get("symbol").asText().trim().toUpperCase();
                            if (!symbol.isEmpty()) {
                                symbols.add(symbol);
                            }
                        }
                    }
                }
                
                return symbols;
            } else {
                throw new RuntimeException("NSE API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error fetching symbols from index " + index + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Fetches basic information for a symbol
     */
    private StockInfo fetchSymbolInfo(String symbol, String index) {
        try {
            // Try to get info from NSE quote endpoint
            String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
            HttpHeaders headers = createNseHeaders(symbol);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                String companyName = symbol;
                String sector = "";
                String industry = "";
                String cap = determineCapFromIndex(index);
                
                // Extract company info
                JsonNode info = root.get("info");
                if (info != null) {
                    if (info.has("companyName")) {
                        companyName = info.get("companyName").asText();
                    }
                    if (info.has("industry")) {
                        industry = info.get("industry").asText();
                    }
                    if (info.has("sector")) {
                        sector = info.get("sector").asText();
                    }
                }
                
                // Try metadata section
                JsonNode metadata = root.get("metadata");
                if (metadata != null) {
                    if (companyName.equals(symbol) && metadata.has("companyName")) {
                        companyName = metadata.get("companyName").asText();
                    }
                    if (industry.isEmpty() && metadata.has("industry")) {
                        industry = metadata.get("industry").asText();
                    }
                    if (sector.isEmpty() && metadata.has("sector")) {
                        sector = metadata.get("sector").asText();
                    }
                }
                
                return new StockInfo(symbol, companyName, "NSE", sector, cap);
            }
        } catch (Exception e) {
            // If fetching info fails, return basic info
            System.err.println("Could not fetch detailed info for " + symbol + ": " + e.getMessage());
        }
        
        // Return basic info if detailed fetch fails
        return new StockInfo(symbol, symbol, "NSE", "", determineCapFromIndex(index));
    }
    
    /**
     * Determines market cap category from index name
     */
    private String determineCapFromIndex(String index) {
        String indexUpper = index.toUpperCase();
        if (indexUpper.contains("50") && !indexUpper.contains("NEXT")) {
            return "Large Cap";
        } else if (indexUpper.contains("MIDCAP")) {
            return "Mid Cap";
        } else if (indexUpper.contains("SMALLCAP")) {
            return "Small Cap";
        } else if (indexUpper.contains("100") || indexUpper.contains("200") || indexUpper.contains("500")) {
            return "Large Cap";
        }
        return "Large Cap"; // Default
    }
    
    /**
     * Validates if a symbol is valid by checking cache or trying to fetch from NSE
     */
    public boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return false;
        }
        
        String upperSymbol = symbol.toUpperCase();
        
        // Check cache first
        if (validSymbols.contains(upperSymbol)) {
            return true;
        }
        
        // If cache is stale or empty, refresh it
        if (validSymbols.isEmpty() || lastSymbolFetchTime.isBefore(LocalDateTime.now().minusHours(6))) {
            try {
                fetchAllValidSymbols();
                if (validSymbols.contains(upperSymbol)) {
                    return true;
                }
            } catch (Exception e) {
                System.err.println("Failed to refresh symbols for validation: " + e.getMessage());
            }
        }
        
        // Try to validate by fetching data from NSE
        return validateSymbolByFetching(upperSymbol);
    }
    
    /**
     * Validates a symbol by attempting to fetch data from NSE
     */
    private boolean validateSymbolByFetching(String symbol) {
        try {
            establishNseSession();
            String url = String.format("https://www.nseindia.com/api/quote-equity?symbol=%s", symbol);
            HttpHeaders headers = createNseHeaders(symbol);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Check if response contains error
                if (root.has("error") || root.has("message")) {
                    return false;
                }
                
                // Check if response has price info (indicates valid symbol)
                if (root.has("priceInfo") || root.has("data")) {
                    // Add to cache
                    validSymbols.add(symbol);
                    
                    // Try to get symbol info
                    if (!symbolInfoMap.containsKey(symbol)) {
                        StockInfo info = fetchSymbolInfo(symbol, "");
                        if (info != null) {
                            symbolInfoMap.put(symbol, info);
                        }
                    }
                    
                    return true;
                }
            }
        } catch (Exception e) {
            // Symbol is likely invalid if fetch fails
            return false;
        }
        
        return false;
    }
    
    /**
     * Gets all valid symbols
     */
    public Set<String> getAllValidSymbols() {
        // Ensure cache is populated
        if (validSymbols.isEmpty() || lastSymbolFetchTime.isBefore(LocalDateTime.now().minusHours(6))) {
            try {
                fetchAllValidSymbols();
            } catch (Exception e) {
                System.err.println("Failed to fetch symbols: " + e.getMessage());
            }
        }
        return new HashSet<>(validSymbols);
    }
    
    /**
     * Gets symbol info
     */
    public StockInfo getSymbolInfo(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        
        // Check cache
        if (symbolInfoMap.containsKey(upperSymbol)) {
            return symbolInfoMap.get(upperSymbol);
        }
        
        // If symbol is valid but info not cached, try to fetch
        if (isValidSymbol(upperSymbol)) {
            StockInfo info = fetchSymbolInfo(upperSymbol, "");
            if (info != null) {
                symbolInfoMap.put(upperSymbol, info);
                return info;
            }
        }
        
        return null;
    }
    
    /**
     * Establishes NSE session
     */
    private void establishNseSession() throws Exception {
        // Check if we need to refresh the session (every 30 minutes)
        if (lastNseSessionTime.isAfter(LocalDateTime.now().minusMinutes(30))) {
            return; // Session still valid
        }
        
        try {
            String mainUrl = "https://www.nseindia.com/";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.5");
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
        } catch (Exception e) {
            System.err.println("Failed to establish NSE session: " + e.getMessage());
        }
    }
    
    /**
     * Creates NSE headers
     */
    private HttpHeaders createNseHeaders() {
        return createNseHeaders(null);
    }
    
    /**
     * Creates NSE headers with symbol for referer
     */
    private HttpHeaders createNseHeaders(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Connection", "keep-alive");
        
        if (symbol != null) {
            headers.set("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol);
        } else {
            headers.set("Referer", "https://www.nseindia.com/");
        }
        
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
     * StockInfo inner class
     */
    public static class StockInfo {
        private final String symbol;
        private final String name;
        private final String exchange;
        private final String sector;
        private final String cap;
        
        public StockInfo(String symbol, String name, String exchange, String sector, String cap) {
            this.symbol = symbol;
            this.name = name;
            this.exchange = exchange;
            this.sector = sector;
            this.cap = cap;
        }
        
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public String getExchange() { return exchange; }
        public String getSector() { return sector; }
        public String getCap() { return cap; }
    }
}

