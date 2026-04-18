package com.trading.controller;

import com.trading.service.LiveMarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeTestController {

    private final LiveMarketDataService liveMarketDataService;

    public RealtimeTestController(LiveMarketDataService liveMarketDataService) {
        this.liveMarketDataService = liveMarketDataService;
    }

    @GetMapping("/test/{symbol}")
    public ResponseEntity<?> testRealtimeData(@PathVariable String symbol) {
        try {
            var data = liveMarketDataService.getLiveMarketData(symbol);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
