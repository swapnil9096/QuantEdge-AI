package com.trading.controller;

import com.trading.service.BacktestingService;
import com.trading.service.ComprehensiveBuyStrategyService;
import com.trading.service.ComprehensiveBuyStrategyService.BuyDecisionResult;
import com.trading.service.BacktestingService.BacktestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Comprehensive Buy Strategy and Backtesting
 */
@RestController
@RequestMapping("/api/comprehensive-strategy")
@CrossOrigin(origins = "*")
@Slf4j
public class ComprehensiveStrategyController {
    
    private final ComprehensiveBuyStrategyService buyStrategyService;
    private final BacktestingService backtestingService;
    
    public ComprehensiveStrategyController(
            ComprehensiveBuyStrategyService buyStrategyService,
            BacktestingService backtestingService) {
        this.buyStrategyService = buyStrategyService;
        this.backtestingService = backtestingService;
    }
    
    /**
     * Analyze buy decision for a stock
     * 
     * GET /api/comprehensive-strategy/analyze/{symbol}
     */
    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<Map<String, Object>> analyzeBuyDecision(@PathVariable String symbol) {
        log.info("Analyzing buy decision for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            BuyDecisionResult result = buyStrategyService.analyzeBuyDecision(symbol);
            
            response.put("success", true);
            response.put("symbol", result.getSymbol());
            response.put("companyName", result.getCompanyName());
            response.put("sector", result.getSector());
            response.put("industry", result.getIndustry());
            response.put("currentPrice", result.getCurrentPrice());
            response.put("decision", result.getDecision());
            response.put("overallScore", result.getOverallScore());
            response.put("fundamentalScore", result.getFundamentalScore());
            response.put("technicalScore", result.getTechnicalScore());
            response.put("newsScore", result.getNewsScore());
            response.put("confidence", result.getConfidence());
            response.put("explanation", result.getExplanation());
            response.put("reasons", result.getReasons());
            response.put("warnings", result.getWarnings());
            response.put("strengths", result.getStrengths());
            response.put("analysisTime", result.getAnalysisTime());
            
            // Include detailed evaluations
            if (result.getFundamentalEvaluation() != null) {
                Map<String, Object> fundamental = new HashMap<>();
                fundamental.put("peRatio", result.getFundamentalEvaluation().getPeRatio());
                fundamental.put("pbRatio", result.getFundamentalEvaluation().getPbRatio());
                fundamental.put("eps", result.getFundamentalEvaluation().getEps());
                fundamental.put("score", result.getFundamentalEvaluation().getScore());
                fundamental.put("reasons", result.getFundamentalEvaluation().getReasons());
                fundamental.put("warnings", result.getFundamentalEvaluation().getWarnings());
                response.put("fundamentalEvaluation", fundamental);
            }
            
            if (result.getTechnicalEvaluation() != null) {
                Map<String, Object> technical = new HashMap<>();
                technical.put("rsi", result.getTechnicalEvaluation().getRsi());
                technical.put("macd", result.getTechnicalEvaluation().getMacd());
                technical.put("trend", result.getTechnicalEvaluation().getTrend());
                technical.put("pattern", result.getTechnicalEvaluation().getPattern());
                technical.put("score", result.getTechnicalEvaluation().getScore());
                technical.put("reasons", result.getTechnicalEvaluation().getReasons());
                technical.put("warnings", result.getTechnicalEvaluation().getWarnings());
                response.put("technicalEvaluation", technical);
            }
            
            if (result.getNewsEvaluation() != null) {
                Map<String, Object> news = new HashMap<>();
                news.put("sentiment", result.getNewsEvaluation().getSentiment());
                news.put("score", result.getNewsEvaluation().getScore());
                news.put("reasons", result.getNewsEvaluation().getReasons());
                news.put("warnings", result.getNewsEvaluation().getWarnings());
                news.put("newsCount", result.getNewsEvaluation().getNewsItems() != null ? 
                        result.getNewsEvaluation().getNewsItems().size() : 0);
                response.put("newsEvaluation", news);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error analyzing buy decision for {}: {}", symbol, e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Backtest the comprehensive buy strategy
     * 
     * GET /api/comprehensive-strategy/backtest/{symbol}?capital=100000&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
     */
    @GetMapping("/backtest/{symbol}")
    public ResponseEntity<Map<String, Object>> backtestStrategy(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100000") BigDecimal capital,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Backtesting strategy for {} with capital {}", symbol, capital);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Default to last 6 months if dates not provided
            if (startDate == null) {
                startDate = LocalDateTime.now().minusMonths(6);
            }
            if (endDate == null) {
                endDate = LocalDateTime.now();
            }
            
            BacktestResult result = backtestingService.backtestStrategy(symbol, capital, startDate, endDate);
            
            response.put("success", true);
            response.put("symbol", result.getSymbol());
            response.put("initialCapital", result.getInitialCapital());
            response.put("startDate", result.getStartDate());
            response.put("endDate", result.getEndDate());
            
            if (result.getError() != null) {
                response.put("error", result.getError());
                response.put("success", false);
                return ResponseEntity.status(500).body(response);
            }
            
            // Performance metrics
            if (result.getPerformanceMetrics() != null) {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("overallReturn", result.getPerformanceMetrics().getOverallReturn());
                metrics.put("overallReturnPercent", result.getPerformanceMetrics().getOverallReturnPercent());
                metrics.put("finalCapital", result.getPerformanceMetrics().getFinalCapital());
                metrics.put("totalTrades", result.getPerformanceMetrics().getTotalTrades());
                metrics.put("winningTrades", result.getPerformanceMetrics().getWinningTrades());
                metrics.put("losingTrades", result.getPerformanceMetrics().getLosingTrades());
                metrics.put("winRate", result.getPerformanceMetrics().getWinRate());
                metrics.put("averageReturnPerTrade", result.getPerformanceMetrics().getAverageReturnPerTrade());
                metrics.put("averageWin", result.getPerformanceMetrics().getAverageWin());
                metrics.put("averageLoss", result.getPerformanceMetrics().getAverageLoss());
                metrics.put("profitFactor", result.getPerformanceMetrics().getProfitFactor());
                metrics.put("maxDrawdown", result.getPerformanceMetrics().getMaxDrawdown());
                metrics.put("sharpeRatio", result.getPerformanceMetrics().getSharpeRatio());
                
                response.put("performanceMetrics", metrics);
            }
            
            // Trade summary
            response.put("totalTrades", result.getTrades() != null ? result.getTrades().size() : 0);
            
            // Sample of trades (first 10)
            if (result.getTrades() != null && !result.getTrades().isEmpty()) {
                int sampleSize = Math.min(10, result.getTrades().size());
                response.put("sampleTrades", result.getTrades().subList(0, sampleSize));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error backtesting strategy for {}: {}", symbol, e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Comprehensive Strategy API is running");
        return ResponseEntity.ok(response);
    }
}

