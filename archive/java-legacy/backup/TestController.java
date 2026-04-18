package com.trading.controller;

import com.trading.model.*;
import com.trading.service.TradingAnalysisService;
import com.trading.util.SampleDataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
@Slf4j
public class TestController {

    @Autowired
    private TradingAnalysisService tradingAnalysisService;
    
    @Autowired
    private SampleDataGenerator sampleDataGenerator;

    @GetMapping("/sample-stocks")
    public ResponseEntity<List<Stock>> getSampleStocks() {
        List<Stock> stocks = sampleDataGenerator.generateSampleStocks();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<AnalysisResult> analyzeSampleStock(@PathVariable String symbol) {
        try {
            log.info("Analyzing sample stock: {}", symbol);
            
            // Generate sample stock data
            Stock stock = sampleDataGenerator.generateSampleStock(symbol);
            
            // Generate sample price history
            List<PriceData> priceHistory = sampleDataGenerator.generateSamplePriceHistory(symbol, 100);
            
            // Perform analysis
            AnalysisResult result = tradingAnalysisService.performComprehensiveAnalysis(stock, priceHistory);
            
            log.info("Analysis completed for sample stock: {} with score: {}", symbol, result.getOverallScore());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error analyzing sample stock: {}", symbol, e);
            throw new RuntimeException("Failed to analyze sample stock: " + e.getMessage());
        }
    }

    @GetMapping("/multibagger-check/{symbol}")
    public ResponseEntity<MultibaggerCheckResponse> checkMultibaggerSample(@PathVariable String symbol) {
        try {
            log.info("Checking multibagger potential for sample stock: {}", symbol);
            
            Stock stock = sampleDataGenerator.generateSampleStock(symbol);
            boolean isMultibagger = tradingAnalysisService.isMultibaggerCandidate(stock);
            
            MultibaggerCheckResponse response = MultibaggerCheckResponse.builder()
                    .symbol(symbol)
                    .isMultibaggerCandidate(isMultibagger)
                    .message(isMultibagger ? "This stock has multibagger potential" : "This stock does not meet multibagger criteria")
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking multibagger potential for sample stock: {}", symbol, e);
            throw new RuntimeException("Failed to check multibagger potential: " + e.getMessage());
        }
    }

    @GetMapping("/swing-trading-check/{symbol}")
    public ResponseEntity<SwingTradingCheckResponse> checkSwingTradingSample(@PathVariable String symbol) {
        try {
            log.info("Checking swing trading potential for sample stock: {}", symbol);
            
            Stock stock = sampleDataGenerator.generateSampleStock(symbol);
            List<PriceData> priceHistory = sampleDataGenerator.generateSamplePriceHistory(symbol, 100);
            boolean isSwingTradingCandidate = tradingAnalysisService.isSwingTradingCandidate(stock, priceHistory);
            
            SwingTradingCheckResponse response = SwingTradingCheckResponse.builder()
                    .symbol(symbol)
                    .isSwingTradingCandidate(isSwingTradingCandidate)
                    .message(isSwingTradingCandidate ? "This stock is suitable for swing trading" : "This stock is not suitable for swing trading")
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking swing trading potential for sample stock: {}", symbol, e);
            throw new RuntimeException("Failed to check swing trading potential: " + e.getMessage());
        }
    }

    @GetMapping("/price-history/{symbol}")
    public ResponseEntity<List<PriceData>> getSamplePriceHistory(@PathVariable String symbol) {
        List<PriceData> priceHistory = sampleDataGenerator.generateSamplePriceHistory(symbol, 100);
        return ResponseEntity.ok(priceHistory);
    }

    @GetMapping("/demo")
    public ResponseEntity<DemoResponse> getDemo() {
        DemoResponse response = DemoResponse.builder()
                .message("Multibagger Swing Trading Strategy Demo")
                .endpoints(List.of(
                        "GET /api/test/sample-stocks - Get sample stocks",
                        "GET /api/test/analyze/{symbol} - Analyze sample stock",
                        "GET /api/test/multibagger-check/{symbol} - Check multibagger potential",
                        "GET /api/test/swing-trading-check/{symbol} - Check swing trading potential",
                        "GET /api/test/price-history/{symbol} - Get sample price history"
                ))
                .sampleSymbols(List.of("RELIANCE", "TCS", "INFY", "HDFC", "ICICIBANK", "WIPRO", "BHARTIARTL", "ITC", "SBIN", "KOTAKBANK"))
                .build();
        
        return ResponseEntity.ok(response);
    }

    // Response DTOs
    public static class MultibaggerCheckResponse {
        private String symbol;
        private boolean isMultibaggerCandidate;
        private String message;

        public static MultibaggerCheckResponseBuilder builder() {
            return new MultibaggerCheckResponseBuilder();
        }

        public static class MultibaggerCheckResponseBuilder {
            private String symbol;
            private boolean isMultibaggerCandidate;
            private String message;

            public MultibaggerCheckResponseBuilder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public MultibaggerCheckResponseBuilder isMultibaggerCandidate(boolean isMultibaggerCandidate) {
                this.isMultibaggerCandidate = isMultibaggerCandidate;
                return this;
            }

            public MultibaggerCheckResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public MultibaggerCheckResponse build() {
                MultibaggerCheckResponse response = new MultibaggerCheckResponse();
                response.symbol = this.symbol;
                response.isMultibaggerCandidate = this.isMultibaggerCandidate;
                response.message = this.message;
                return response;
            }
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public boolean isMultibaggerCandidate() { return isMultibaggerCandidate; }
        public void setMultibaggerCandidate(boolean isMultibaggerCandidate) { this.isMultibaggerCandidate = isMultibaggerCandidate; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class SwingTradingCheckResponse {
        private String symbol;
        private boolean isSwingTradingCandidate;
        private String message;

        public static SwingTradingCheckResponseBuilder builder() {
            return new SwingTradingCheckResponseBuilder();
        }

        public static class SwingTradingCheckResponseBuilder {
            private String symbol;
            private boolean isSwingTradingCandidate;
            private String message;

            public SwingTradingCheckResponseBuilder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public SwingTradingCheckResponseBuilder isSwingTradingCandidate(boolean isSwingTradingCandidate) {
                this.isSwingTradingCandidate = isSwingTradingCandidate;
                return this;
            }

            public SwingTradingCheckResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public SwingTradingCheckResponse build() {
                SwingTradingCheckResponse response = new SwingTradingCheckResponse();
                response.symbol = this.symbol;
                response.isSwingTradingCandidate = this.isSwingTradingCandidate;
                response.message = this.message;
                return response;
            }
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public boolean isSwingTradingCandidate() { return isSwingTradingCandidate; }
        public void setSwingTradingCandidate(boolean isSwingTradingCandidate) { this.isSwingTradingCandidate = isSwingTradingCandidate; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class DemoResponse {
        private String message;
        private List<String> endpoints;
        private List<String> sampleSymbols;

        public static DemoResponseBuilder builder() {
            return new DemoResponseBuilder();
        }

        public static class DemoResponseBuilder {
            private String message;
            private List<String> endpoints;
            private List<String> sampleSymbols;

            public DemoResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public DemoResponseBuilder endpoints(List<String> endpoints) {
                this.endpoints = endpoints;
                return this;
            }

            public DemoResponseBuilder sampleSymbols(List<String> sampleSymbols) {
                this.sampleSymbols = sampleSymbols;
                return this;
            }

            public DemoResponse build() {
                DemoResponse response = new DemoResponse();
                response.message = this.message;
                response.endpoints = this.endpoints;
                response.sampleSymbols = this.sampleSymbols;
                return response;
            }
        }

        // Getters and Setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public List<String> getEndpoints() { return endpoints; }
        public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
        
        public List<String> getSampleSymbols() { return sampleSymbols; }
        public void setSampleSymbols(List<String> sampleSymbols) { this.sampleSymbols = sampleSymbols; }
    }
}
