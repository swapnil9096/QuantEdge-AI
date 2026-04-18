package com.trading.controller;

import com.trading.model.NseStockData;
import com.trading.service.ComprehensiveNseDataService;
import com.trading.service.McpDataService;
import com.trading.util.VolumeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nse")
@CrossOrigin(origins = "*")
public class NseDataController {

    @Autowired
    private ComprehensiveNseDataService comprehensiveNseDataService;
    
    @Autowired
    private McpDataService mcpDataService;

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<Map<String, Object>> getStockData(@PathVariable String symbol) {
        try {
            NseStockData stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stockData);
            response.put("message", "Stock data retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve stock data for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/basic")
    public ResponseEntity<Map<String, Object>> getBasicStockData(@PathVariable String symbol) {
        try {
            NseStockData stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            
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
            basicData.put("volumeFormatted", VolumeFormatter.formatVolume(stockData.getTotalTradedVolume()));
            basicData.put("timestamp", stockData.getTimestamp());
            basicData.put("dataSource", stockData.getDataSource());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", basicData);
            response.put("message", "Basic stock data retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve basic stock data for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/market-depth")
    public ResponseEntity<Map<String, Object>> getMarketDepth(@PathVariable String symbol) {
        try {
            NseStockData stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            
            Map<String, Object> marketDepth = new HashMap<>();
            marketDepth.put("symbol", stockData.getSymbol());
            marketDepth.put("bid", stockData.getBid());
            marketDepth.put("ask", stockData.getAsk());
            marketDepth.put("timestamp", stockData.getTimestamp());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", marketDepth);
            response.put("message", "Market depth data retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve market depth for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/company-info")
    public ResponseEntity<Map<String, Object>> getCompanyInfo(@PathVariable String symbol) {
        try {
            NseStockData stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            
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
            response.put("message", "Company information retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve company information for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get comprehensive NSE stock data using MCP service
     * This endpoint fetches ALL available data from NSE India including:
     * - Price information (current, open, high, low, previous close)
     * - Volume and trading data (volume, value, market cap)
     * - Historical data (52-week highs/lows, year highs/lows)
     * - Company information (name, industry, sector, ISIN, etc.)
     * - Financial metrics (PE, PB, dividend yield, EPS, book value)
     * - Market depth (bid/ask orders)
     * 
     * @param symbol Stock symbol (e.g., RELIANCE, TCS, INFY)
     * @return Comprehensive stock data with all available fields
     */
    @GetMapping("/stock/{symbol}/mcp")
    public ResponseEntity<Map<String, Object>> getComprehensiveStockDataViaMcp(@PathVariable String symbol) {
        try {
            NseStockData stockData = mcpDataService.getComprehensiveNseData(symbol.toUpperCase());
            
            // Build comprehensive response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", buildComprehensiveResponse(stockData));
            response.put("message", "Comprehensive NSE stock data retrieved successfully via MCP");
            response.put("timestamp", stockData.getTimestamp());
            response.put("dataSource", stockData.getDataSource());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve comprehensive stock data for " + symbol + " via MCP");
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get comprehensive NSE stock data - Main endpoint (uses MCP if enabled, otherwise falls back)
     * This is the primary endpoint that returns all available stock information
     * 
     * @param symbol Stock symbol (e.g., RELIANCE, TCS, INFY)
     * @return Comprehensive stock data with all available fields
     */
    @GetMapping("/stock/{symbol}/comprehensive")
    public ResponseEntity<Map<String, Object>> getComprehensiveStockData(@PathVariable String symbol) {
        try {
            NseStockData stockData;
            String dataSource;
            
            // Try MCP service first if enabled
            try {
                System.out.println("Attempting to fetch " + symbol + " via MCP service...");
                stockData = mcpDataService.getComprehensiveNseData(symbol.toUpperCase());
                dataSource = stockData.getDataSource();
                System.out.println("Successfully fetched " + symbol + " via MCP service");
            } catch (Exception mcpError) {
                System.err.println("MCP service failed for " + symbol + ": " + mcpError.getMessage());
                
                // Fallback to comprehensive service
                try {
                    System.out.println("Attempting fallback to ComprehensiveNseDataService for " + symbol + "...");
                    stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol.toUpperCase());
                    dataSource = stockData.getDataSource();
                    System.out.println("Successfully fetched " + symbol + " via ComprehensiveNseDataService");
                } catch (Exception fallbackError) {
                    System.err.println("ComprehensiveNseDataService also failed for " + symbol + ": " + fallbackError.getMessage());
                    throw new RuntimeException("Both MCP and ComprehensiveNseDataService failed. MCP error: " + 
                        mcpError.getMessage() + ". Fallback error: " + fallbackError.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", buildComprehensiveResponse(stockData));
            response.put("message", "Comprehensive stock data retrieved successfully");
            response.put("timestamp", stockData.getTimestamp());
            response.put("dataSource", dataSource);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve comprehensive stock data for " + symbol);
            errorResponse.put("suggestion", "Please check if the symbol is correct and NSE API is accessible");
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Builds a comprehensive response map from NseStockData
     */
    private Map<String, Object> buildComprehensiveResponse(NseStockData stockData) {
        Map<String, Object> data = new HashMap<>();
        
        // Basic Information
        data.put("symbol", stockData.getSymbol());
        data.put("companyName", stockData.getCompanyName());
        
        // Price Information
        Map<String, Object> priceInfo = new HashMap<>();
        priceInfo.put("lastPrice", stockData.getLastPrice());
        priceInfo.put("open", stockData.getOpen());
        priceInfo.put("dayHigh", stockData.getDayHigh());
        priceInfo.put("dayLow", stockData.getDayLow());
        priceInfo.put("previousClose", stockData.getPreviousClose());
        priceInfo.put("close", stockData.getClose());
        priceInfo.put("change", stockData.getChange());
        priceInfo.put("changePercent", stockData.getChangePercent());
        data.put("priceInfo", priceInfo);
        
        // Volume & Trading Data
        Map<String, Object> volumeInfo = new HashMap<>();
        volumeInfo.put("totalTradedVolume", stockData.getTotalTradedVolume());
        volumeInfo.put("totalTradedVolumeFormatted", VolumeFormatter.formatVolume(stockData.getTotalTradedVolume()));
        volumeInfo.put("totalTradedValue", stockData.getTotalTradedValue());
        volumeInfo.put("totalTradedValueFormatted", VolumeFormatter.formatValue(stockData.getTotalTradedValue()));
        volumeInfo.put("marketCap", stockData.getMarketCap());
        volumeInfo.put("marketCapFormatted", VolumeFormatter.formatValue(stockData.getMarketCap()));
        data.put("volumeInfo", volumeInfo);
        
        // Historical Data
        Map<String, Object> historicalInfo = new HashMap<>();
        historicalInfo.put("high52Week", stockData.getHigh52Week());
        historicalInfo.put("low52Week", stockData.getLow52Week());
        historicalInfo.put("yearHigh", stockData.getYearHigh());
        historicalInfo.put("yearLow", stockData.getYearLow());
        data.put("historicalInfo", historicalInfo);
        
        // Company Information
        Map<String, Object> companyInfo = new HashMap<>();
        companyInfo.put("companyName", stockData.getCompanyName());
        companyInfo.put("industry", stockData.getIndustry());
        companyInfo.put("sector", stockData.getSector());
        companyInfo.put("isin", stockData.getIsin());
        companyInfo.put("series", stockData.getSeries());
        companyInfo.put("faceValue", stockData.getFaceValue());
        data.put("companyInfo", companyInfo);
        
        // Financial Metrics
        Map<String, Object> financialMetrics = new HashMap<>();
        financialMetrics.put("pe", stockData.getPe());
        financialMetrics.put("pb", stockData.getPb());
        financialMetrics.put("dividendYield", stockData.getDividendYield());
        financialMetrics.put("bookValue", stockData.getBookValue());
        financialMetrics.put("eps", stockData.getEps());
        data.put("financialMetrics", financialMetrics);
        
        // Market Depth
        Map<String, Object> marketDepth = new HashMap<>();
        marketDepth.put("bid", stockData.getBid());
        marketDepth.put("ask", stockData.getAsk());
        data.put("marketDepth", marketDepth);
        
        return data;
    }
}
