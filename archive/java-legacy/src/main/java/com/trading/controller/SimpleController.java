package com.trading.controller;

import com.trading.service.RealTimeAnalysisService;
import com.trading.service.StockValidationService;
import com.trading.service.RealTimeNseDataService;
import com.trading.service.RealTimeAnalysisService.RealTimeAnalysisResult;
import com.trading.util.VolumeFormatter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simple")
@CrossOrigin(origins = "*")
public class SimpleController {
    
    private final RealTimeAnalysisService realTimeAnalysisService;
    private final StockValidationService stockValidationService;
    private final RealTimeNseDataService realTimeNseDataService;
    
    public SimpleController(RealTimeAnalysisService realTimeAnalysisService, 
                           StockValidationService stockValidationService,
                           RealTimeNseDataService realTimeNseDataService) {
        this.realTimeAnalysisService = realTimeAnalysisService;
        this.stockValidationService = stockValidationService;
        this.realTimeNseDataService = realTimeNseDataService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Multibagger Swing Trading Strategy API is running");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/demo")
    public ResponseEntity<Map<String, Object>> getDemo() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Multibagger Swing Trading Strategy Demo");
        response.put("endpoints", new String[]{
            "GET /api/simple/health - Health check",
            "GET /api/simple/demo - Demo information",
            "GET /api/simple/analyze/{symbol} - Analyze stock with real-time data",
            "GET /api/simple/multibagger-check/{symbol} - Check multibagger potential",
            "GET /api/simple/swing-trading-check/{symbol} - Check swing trading suitability",
            "GET /api/simple/valid-symbols - Get all valid stock symbols"
        });
        response.put("sampleSymbols", stockValidationService.getAllValidSymbols());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<Map<String, Object>> analyzeStock(@PathVariable String symbol) {
        try {
            // Validate stock symbol first
            if (!stockValidationService.isValidStock(symbol)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid stock symbol");
                errorResponse.put("message", "Stock symbol '" + symbol + "' is not valid. Please use a valid NSE/BSE stock symbol.");
                errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Perform real-time analysis
            RealTimeAnalysisResult analysisResult = realTimeAnalysisService.analyzeStock(symbol);
            
            // Convert to response map
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", analysisResult.getSymbol());
            response.put("name", analysisResult.getName());
            response.put("exchange", analysisResult.getExchange());
            response.put("sector", analysisResult.getSector());
            response.put("cap", analysisResult.getCap());
            response.put("currentPrice", analysisResult.getCurrentPrice());
            response.put("change", analysisResult.getChange());
            response.put("changePercent", analysisResult.getChangePercent());
            // Volume represents LIVE DAILY VOLUME (cumulative volume for current trading day)
            response.put("volume", analysisResult.getVolume());
            response.put("volumeFormatted", VolumeFormatter.formatVolume(analysisResult.getVolume()));
            response.put("analysisTime", analysisResult.getAnalysisTime());
            response.put("overallScore", analysisResult.getOverallScore());
            response.put("confidence", analysisResult.getConfidence());
            response.put("recommendation", analysisResult.getRecommendation());
            
            // Technical Analysis
            Map<String, Object> technicalAnalysis = new HashMap<>();
            technicalAnalysis.put("rsi", analysisResult.getTechnicalAnalysis().getRsi());
            technicalAnalysis.put("macd", analysisResult.getTechnicalAnalysis().getMacd());
            technicalAnalysis.put("macdSignal", analysisResult.getTechnicalAnalysis().getMacdSignal());
            technicalAnalysis.put("macdHistogram", analysisResult.getTechnicalAnalysis().getMacdHistogram());
            technicalAnalysis.put("sma20", analysisResult.getTechnicalAnalysis().getSma20());
            technicalAnalysis.put("sma50", analysisResult.getTechnicalAnalysis().getSma50());
            technicalAnalysis.put("bollingerUpper", analysisResult.getTechnicalAnalysis().getBollingerUpper());
            technicalAnalysis.put("bollingerMiddle", analysisResult.getTechnicalAnalysis().getBollingerMiddle());
            technicalAnalysis.put("bollingerLower", analysisResult.getTechnicalAnalysis().getBollingerLower());
            technicalAnalysis.put("stochasticK", analysisResult.getTechnicalAnalysis().getStochasticK());
            technicalAnalysis.put("stochasticD", analysisResult.getTechnicalAnalysis().getStochasticD());
            technicalAnalysis.put("williamsR", analysisResult.getTechnicalAnalysis().getWilliamsR());
            technicalAnalysis.put("atr", analysisResult.getTechnicalAnalysis().getAtr());
            technicalAnalysis.put("trend", analysisResult.getTechnicalAnalysis().getTrend());
            technicalAnalysis.put("pattern", analysisResult.getTechnicalAnalysis().getPattern());
            technicalAnalysis.put("technicalScore", analysisResult.getTechnicalAnalysis().getTechnicalScore());
            response.put("technicalAnalysis", technicalAnalysis);
            
            // Fundamental Analysis
            Map<String, Object> fundamentalAnalysis = new HashMap<>();
            fundamentalAnalysis.put("peRatio", analysisResult.getFundamentalAnalysis().getPeRatio());
            fundamentalAnalysis.put("pbRatio", analysisResult.getFundamentalAnalysis().getPbRatio());
            fundamentalAnalysis.put("debtToEquity", analysisResult.getFundamentalAnalysis().getDebtToEquity());
            fundamentalAnalysis.put("roe", analysisResult.getFundamentalAnalysis().getRoe());
            fundamentalAnalysis.put("roa", analysisResult.getFundamentalAnalysis().getRoa());
            fundamentalAnalysis.put("revenueGrowth", analysisResult.getFundamentalAnalysis().getRevenueGrowth());
            fundamentalAnalysis.put("profitGrowth", analysisResult.getFundamentalAnalysis().getProfitGrowth());
            fundamentalAnalysis.put("marketCap", analysisResult.getFundamentalAnalysis().getMarketCap());
            fundamentalAnalysis.put("fundamentalScore", analysisResult.getFundamentalAnalysis().getFundamentalScore());
            response.put("fundamentalAnalysis", fundamentalAnalysis);
            
            // Multibagger Analysis
            Map<String, Object> multibaggerAnalysis = new HashMap<>();
            multibaggerAnalysis.put("growthPotential", analysisResult.getMultibaggerAnalysis().getGrowthPotential());
            multibaggerAnalysis.put("marketCapGrowth", analysisResult.getMultibaggerAnalysis().getMarketCapGrowth());
            multibaggerAnalysis.put("revenueGrowth", analysisResult.getMultibaggerAnalysis().getRevenueGrowth());
            multibaggerAnalysis.put("profitGrowth", analysisResult.getMultibaggerAnalysis().getProfitGrowth());
            multibaggerAnalysis.put("growthPhase", analysisResult.getMultibaggerAnalysis().getGrowthPhase());
            multibaggerAnalysis.put("multibaggerScore", analysisResult.getMultibaggerAnalysis().getMultibaggerScore());
            response.put("multibaggerAnalysis", multibaggerAnalysis);
            
            // Risk Assessment
            Map<String, Object> riskAssessment = new HashMap<>();
            riskAssessment.put("volatility", analysisResult.getRiskAssessment().getVolatility());
            riskAssessment.put("beta", analysisResult.getRiskAssessment().getBeta());
            riskAssessment.put("riskLevel", analysisResult.getRiskAssessment().getRiskLevel());
            riskAssessment.put("riskScore", analysisResult.getRiskAssessment().getRiskScore());
            response.put("riskAssessment", riskAssessment);
            
            // Trading Signal
            Map<String, Object> tradingSignal = new HashMap<>();
            tradingSignal.put("signalType", analysisResult.getTradingSignal().getSignalType());
            tradingSignal.put("strength", analysisResult.getTradingSignal().getStrength());
            tradingSignal.put("entryPrice", analysisResult.getTradingSignal().getEntryPrice());
            tradingSignal.put("targetPrice", analysisResult.getTradingSignal().getTargetPrice());
            tradingSignal.put("stopLoss", analysisResult.getTradingSignal().getStopLoss());
            tradingSignal.put("expectedReturn", analysisResult.getTradingSignal().getExpectedReturn());
            tradingSignal.put("confidence", analysisResult.getTradingSignal().getConfidence());
            response.put("tradingSignal", tradingSignal);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid stock symbol");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Analysis failed");
            errorResponse.put("message", "Unable to analyze stock: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/multibagger-check/{symbol}")
    public ResponseEntity<Map<String, Object>> checkMultibagger(@PathVariable String symbol) {
        try {
            // Validate stock symbol first
            if (!stockValidationService.isValidStock(symbol)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid stock symbol");
                errorResponse.put("message", "Stock symbol '" + symbol + "' is not valid. Please use a valid NSE/BSE stock symbol.");
                errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Get NSE real-time price first
            RealTimeNseDataService.RealTimeMarketData nseData = realTimeNseDataService.getRealTimeData(symbol);
            
            // Perform real-time analysis
            RealTimeAnalysisResult analysisResult = realTimeAnalysisService.analyzeStock(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("name", analysisResult.getName());
            response.put("sector", analysisResult.getSector());
            response.put("cap", analysisResult.getCap());
            response.put("currentPrice", nseData.getCurrentPrice()); // Use NSE exact price
            
            // Multibagger analysis
            boolean isMultibaggerCandidate = analysisResult.getMultibaggerAnalysis().getMultibaggerScore()
                    .compareTo(new java.math.BigDecimal("70")) > 0;
            response.put("isMultibaggerCandidate", isMultibaggerCandidate);
            response.put("multibaggerScore", analysisResult.getMultibaggerAnalysis().getMultibaggerScore());
            response.put("growthPotential", analysisResult.getMultibaggerAnalysis().getGrowthPotential());
            response.put("growthPhase", analysisResult.getMultibaggerAnalysis().getGrowthPhase());
            
            if (isMultibaggerCandidate) {
                response.put("message", "This stock has strong multibagger potential with " + 
                    analysisResult.getMultibaggerAnalysis().getMultibaggerScore() + "% score");
            } else {
                response.put("message", "This stock has limited multibagger potential with " + 
                    analysisResult.getMultibaggerAnalysis().getMultibaggerScore() + "% score");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid stock symbol");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Analysis failed");
            errorResponse.put("message", "Unable to analyze stock: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/swing-trading-check/{symbol}")
    public ResponseEntity<Map<String, Object>> checkSwingTrading(@PathVariable String symbol) {
        try {
            // Validate stock symbol first
            if (!stockValidationService.isValidStock(symbol)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid stock symbol");
                errorResponse.put("message", "Stock symbol '" + symbol + "' is not valid. Please use a valid NSE/BSE stock symbol.");
                errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Perform real-time analysis
            RealTimeAnalysisResult analysisResult = realTimeAnalysisService.analyzeStock(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("name", analysisResult.getName());
            response.put("sector", analysisResult.getSector());
            response.put("cap", analysisResult.getCap());
            response.put("currentPrice", analysisResult.getCurrentPrice());
            response.put("change", analysisResult.getChange());
            response.put("changePercent", analysisResult.getChangePercent());
            
            // Swing trading analysis
            boolean isSwingTradingCandidate = analysisResult.getTradingSignal().getExpectedReturn()
                    .compareTo(new java.math.BigDecimal("2")) > 0 && 
                    analysisResult.getOverallScore().compareTo(new java.math.BigDecimal("65")) > 0;
            response.put("isSwingTradingCandidate", isSwingTradingCandidate);
            response.put("expectedReturn", analysisResult.getTradingSignal().getExpectedReturn());
            response.put("signalType", analysisResult.getTradingSignal().getSignalType());
            response.put("strength", analysisResult.getTradingSignal().getStrength());
            response.put("overallScore", analysisResult.getOverallScore());
            response.put("confidence", analysisResult.getConfidence());
            
            if (isSwingTradingCandidate) {
                response.put("message", "This stock is suitable for swing trading with " + 
                    analysisResult.getTradingSignal().getExpectedReturn() + "% expected return");
            } else {
                response.put("message", "This stock may not be suitable for swing trading. Expected return: " + 
                    analysisResult.getTradingSignal().getExpectedReturn() + "%");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid stock symbol");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("validSymbols", stockValidationService.getAllValidSymbols());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Analysis failed");
            errorResponse.put("message", "Unable to analyze stock: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    @GetMapping("/valid-symbols")
    public ResponseEntity<Map<String, Object>> getValidSymbols() {
        Map<String, Object> response = new HashMap<>();
        response.put("validSymbols", stockValidationService.getAllValidSymbols());
        response.put("totalCount", stockValidationService.getAllValidSymbols().size());
        response.put("message", "All valid NSE/BSE stock symbols for analysis");
        return ResponseEntity.ok(response);
    }
}
