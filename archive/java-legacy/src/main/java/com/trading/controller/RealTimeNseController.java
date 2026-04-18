package com.trading.controller;

import com.trading.service.RealTimeNseDataService;
import com.trading.service.RealTimeNseDataService.RealTimeMarketData;
import com.trading.util.VolumeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/realtime-nse")
@CrossOrigin(origins = "*")
public class RealTimeNseController {

    @Autowired
    private RealTimeNseDataService realTimeNseDataService;

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<Map<String, Object>> getRealTimeStockData(@PathVariable String symbol) {
        try {
            RealTimeMarketData stockData = realTimeNseDataService.getRealTimeData(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stockData);
            response.put("message", "Real-time stock data retrieved successfully for " + symbol);
            response.put("timestamp", stockData.getTimestamp());
            response.put("dataSource", stockData.getDataSource());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve real-time stock data for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/price")
    public ResponseEntity<Map<String, Object>> getRealTimePrice(@PathVariable String symbol) {
        try {
            RealTimeMarketData stockData = realTimeNseDataService.getRealTimeData(symbol);
            
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("symbol", stockData.getSymbol());
            priceData.put("currentPrice", stockData.getCurrentPrice());
            priceData.put("change", stockData.getChange());
            priceData.put("changePercent", stockData.getChangePercent());
            priceData.put("timestamp", stockData.getTimestamp());
            priceData.put("dataSource", stockData.getDataSource());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", priceData);
            response.put("message", "Real-time price data for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve real-time price for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/stock/{symbol}/summary")
    public ResponseEntity<Map<String, Object>> getRealTimeSummary(@PathVariable String symbol) {
        try {
            RealTimeMarketData stockData = realTimeNseDataService.getRealTimeData(symbol);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("symbol", stockData.getSymbol());
            summary.put("currentPrice", stockData.getCurrentPrice());
            summary.put("open", stockData.getOpen());
            summary.put("high", stockData.getHigh());
            summary.put("low", stockData.getLow());
            summary.put("previousClose", stockData.getPreviousClose());
            summary.put("change", stockData.getChange());
            summary.put("changePercent", stockData.getChangePercent());
            summary.put("volume", stockData.getVolume());
            summary.put("volumeFormatted", VolumeFormatter.formatVolume(stockData.getVolume()));
            summary.put("timestamp", stockData.getTimestamp());
            summary.put("dataSource", stockData.getDataSource());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("message", "Real-time summary for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Failed to retrieve real-time summary for " + symbol);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
