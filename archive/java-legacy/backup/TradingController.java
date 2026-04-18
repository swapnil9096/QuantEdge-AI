package com.trading.controller;

import com.trading.model.*;
import com.trading.service.TradingAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/trading")
@CrossOrigin(origins = "*")
@Slf4j
public class TradingController {

    @Autowired
    private TradingAnalysisService tradingAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyzeStock(@Valid @RequestBody StockAnalysisRequest request) {
        try {
            log.info("Received analysis request for stock: {}", request.getSymbol());
            
            // Create stock object from request
            Stock stock = createStockFromRequest(request);
            
            // Perform comprehensive analysis
            AnalysisResult result = tradingAnalysisService.performComprehensiveAnalysis(stock, request.getPriceHistory());
            
            log.info("Analysis completed for stock: {} with overall score: {}", 
                    request.getSymbol(), result.getOverallScore());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error analyzing stock: {}", request.getSymbol(), e);
            throw new RuntimeException("Failed to analyze stock: " + e.getMessage());
        }
    }

    @PostMapping("/multibagger-check")
    public ResponseEntity<MultibaggerCheckResponse> checkMultibaggerPotential(@Valid @RequestBody StockAnalysisRequest request) {
        try {
            log.info("Checking multibagger potential for stock: {}", request.getSymbol());
            
            Stock stock = createStockFromRequest(request);
            boolean isMultibagger = tradingAnalysisService.isMultibaggerCandidate(stock);
            
            MultibaggerCheckResponse response = MultibaggerCheckResponse.builder()
                    .symbol(request.getSymbol())
                    .isMultibaggerCandidate(isMultibagger)
                    .message(isMultibagger ? "This stock has multibagger potential" : "This stock does not meet multibagger criteria")
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking multibagger potential for stock: {}", request.getSymbol(), e);
            throw new RuntimeException("Failed to check multibagger potential: " + e.getMessage());
        }
    }

    @PostMapping("/swing-trading-check")
    public ResponseEntity<SwingTradingCheckResponse> checkSwingTradingPotential(@Valid @RequestBody StockAnalysisRequest request) {
        try {
            log.info("Checking swing trading potential for stock: {}", request.getSymbol());
            
            Stock stock = createStockFromRequest(request);
            boolean isSwingTradingCandidate = tradingAnalysisService.isSwingTradingCandidate(stock, request.getPriceHistory());
            
            SwingTradingCheckResponse response = SwingTradingCheckResponse.builder()
                    .symbol(request.getSymbol())
                    .isSwingTradingCandidate(isSwingTradingCandidate)
                    .message(isSwingTradingCandidate ? "This stock is suitable for swing trading" : "This stock is not suitable for swing trading")
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking swing trading potential for stock: {}", request.getSymbol(), e);
            throw new RuntimeException("Failed to check swing trading potential: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .message("Multibagger Swing Trading Strategy API is running")
                .build());
    }

    private Stock createStockFromRequest(StockAnalysisRequest request) {
        return Stock.builder()
                .symbol(request.getSymbol())
                .name(request.getName())
                .exchange(request.getExchange())
                .sector(request.getSector())
                .industry(request.getIndustry())
                .currentPrice(request.getCurrentPrice())
                .marketCap(request.getMarketCap())
                .volume(request.getVolume())
                .peRatio(request.getPeRatio())
                .pbRatio(request.getPbRatio())
                .debtToEquity(request.getDebtToEquity())
                .roe(request.getRoe())
                .roa(request.getRoa())
                .revenueGrowth(request.getRevenueGrowth())
                .profitGrowth(request.getProfitGrowth())
                .lastUpdated(java.time.LocalDateTime.now())
                .build();
    }

    // Request/Response DTOs
    public static class StockAnalysisRequest {
        private String symbol;
        private String name;
        private String exchange;
        private String sector;
        private String industry;
        private java.math.BigDecimal currentPrice;
        private java.math.BigDecimal marketCap;
        private java.math.BigDecimal volume;
        private java.math.BigDecimal peRatio;
        private java.math.BigDecimal pbRatio;
        private java.math.BigDecimal debtToEquity;
        private java.math.BigDecimal roe;
        private java.math.BigDecimal roa;
        private java.math.BigDecimal revenueGrowth;
        private java.math.BigDecimal profitGrowth;
        private List<PriceData> priceHistory;

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        
        public java.math.BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(java.math.BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        
        public java.math.BigDecimal getMarketCap() { return marketCap; }
        public void setMarketCap(java.math.BigDecimal marketCap) { this.marketCap = marketCap; }
        
        public java.math.BigDecimal getVolume() { return volume; }
        public void setVolume(java.math.BigDecimal volume) { this.volume = volume; }
        
        public java.math.BigDecimal getPeRatio() { return peRatio; }
        public void setPeRatio(java.math.BigDecimal peRatio) { this.peRatio = peRatio; }
        
        public java.math.BigDecimal getPbRatio() { return pbRatio; }
        public void setPbRatio(java.math.BigDecimal pbRatio) { this.pbRatio = pbRatio; }
        
        public java.math.BigDecimal getDebtToEquity() { return debtToEquity; }
        public void setDebtToEquity(java.math.BigDecimal debtToEquity) { this.debtToEquity = debtToEquity; }
        
        public java.math.BigDecimal getRoe() { return roe; }
        public void setRoe(java.math.BigDecimal roe) { this.roe = roe; }
        
        public java.math.BigDecimal getRoa() { return roa; }
        public void setRoa(java.math.BigDecimal roa) { this.roa = roa; }
        
        public java.math.BigDecimal getRevenueGrowth() { return revenueGrowth; }
        public void setRevenueGrowth(java.math.BigDecimal revenueGrowth) { this.revenueGrowth = revenueGrowth; }
        
        public java.math.BigDecimal getProfitGrowth() { return profitGrowth; }
        public void setProfitGrowth(java.math.BigDecimal profitGrowth) { this.profitGrowth = profitGrowth; }
        
        public List<PriceData> getPriceHistory() { return priceHistory; }
        public void setPriceHistory(List<PriceData> priceHistory) { this.priceHistory = priceHistory; }
    }

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

    public static class HealthResponse {
        private String status;
        private String message;

        public static HealthResponseBuilder builder() {
            return new HealthResponseBuilder();
        }

        public static class HealthResponseBuilder {
            private String status;
            private String message;

            public HealthResponseBuilder status(String status) {
                this.status = status;
                return this;
            }

            public HealthResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public HealthResponse build() {
                HealthResponse response = new HealthResponse();
                response.status = this.status;
                response.message = this.message;
                return response;
            }
        }

        // Getters and Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
