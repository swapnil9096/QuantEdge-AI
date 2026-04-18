package com.trading.controller;

import com.trading.service.QuoteStreamService;
import com.trading.service.StockValidationService;
import com.trading.service.RealTimeDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final QuoteStreamService quoteStreamService;
    private final StockValidationService stockValidationService;

    public StreamController(QuoteStreamService quoteStreamService, StockValidationService stockValidationService) {
        this.quoteStreamService = quoteStreamService;
        this.stockValidationService = stockValidationService;
    }

    @GetMapping("/latest/{symbol}")
    public ResponseEntity<?> getLatest(@PathVariable String symbol) {
        if (!stockValidationService.isValidStock(symbol)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid stock symbol");
            error.put("validSymbols", stockValidationService.getAllValidSymbols());
            return ResponseEntity.badRequest().body(error);
        }
        RealTimeDataService.RealTimeStockData data = quoteStreamService.getLatestQuote(symbol);
        return ResponseEntity.ok(data);
    }
}


