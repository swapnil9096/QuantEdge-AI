package com.trading.controller;

import com.trading.service.LiveMarketDataService;
import com.trading.service.StockValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class LiveDataController {

    private final LiveMarketDataService liveMarketDataService;
    private final StockValidationService stockValidationService;

    public LiveDataController(LiveMarketDataService liveMarketDataService, StockValidationService stockValidationService) {
        this.liveMarketDataService = liveMarketDataService;
        this.stockValidationService = stockValidationService;
    }

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<?> getLiveQuote(@PathVariable String symbol) {
        if (!stockValidationService.isValidStock(symbol)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid stock symbol");
            error.put("message", "Stock symbol '" + symbol + "' is not valid. Please use a valid NSE/BSE stock symbol.");
            error.put("validSymbols", stockValidationService.getAllValidSymbols());
            return ResponseEntity.badRequest().body(error);
        }

        try {
            LiveMarketDataService.LiveMarketData liveData = liveMarketDataService.getLiveMarketData(symbol);
            return ResponseEntity.ok(liveData);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to fetch live data");
            error.put("message", "Unable to fetch live market data for " + symbol + ": " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/test/{symbol}")
    public ResponseEntity<?> testLiveData(@PathVariable String symbol) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LiveMarketDataService.LiveMarketData liveData = liveMarketDataService.getLiveMarketData(symbol);
            
            response.put("symbol", symbol);
            response.put("currentPrice", liveData.getCurrentPrice());
            response.put("change", liveData.getChange());
            response.put("changePercent", liveData.getChangePercent());
            response.put("dataSource", liveData.getDataSource());
            response.put("timestamp", liveData.getTimestamp());
            response.put("status", "SUCCESS");
            response.put("message", "Live market data fetched successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("symbol", symbol);
            response.put("status", "ERROR");
            response.put("message", "Failed to fetch live data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
