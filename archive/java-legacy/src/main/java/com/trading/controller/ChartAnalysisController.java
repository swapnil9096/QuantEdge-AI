package com.trading.controller;

import com.trading.model.ChartAnalysisResult;
import com.trading.model.TimeframeAnalysis;
import com.trading.service.ChartAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chart")
@CrossOrigin(origins = "*")
public class ChartAnalysisController {
    
    private final ChartAnalysisService chartAnalysisService;
    
    public ChartAnalysisController(ChartAnalysisService chartAnalysisService) {
        this.chartAnalysisService = chartAnalysisService;
    }
    
    /**
     * GET /api/chart/analyze/{symbol}
     * Perform comprehensive chart analysis across multiple timeframes
     * 
     * Example: /api/chart/analyze/RELIANCE.NS
     * Example: /api/chart/analyze/AAPL
     * Example: /api/chart/analyze/INFY.NS
     */
    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<Map<String, Object>> analyzeChart(@PathVariable String symbol) {
        // Validate input
        if (symbol == null || symbol.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Invalid symbol");
            errorResponse.put("message", "Stock symbol cannot be empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        try {
            ChartAnalysisResult result = chartAnalysisService.analyzeChart(symbol);
            
            // Validate result
            if (result == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Analysis failed");
                errorResponse.put("message", "Unable to analyze chart for symbol: " + symbol);
                return ResponseEntity.status(500).body(errorResponse);
            }
            
            // Convert to response format with proper null handling
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("symbol", result.getSymbol() != null ? result.getSymbol() : symbol);
            response.put("trend", result.getTrend() != null ? result.getTrend() : "NEUTRAL");
            response.put("confidence", result.getConfidence() != null ? 
                result.getConfidence() + "%" : "0%");
            response.put("summary", result.getSummary() != null ? result.getSummary() : "No analysis available");
            response.put("analysisTime", result.getAnalysisTime() != null ? 
                result.getAnalysisTime() : java.time.LocalDateTime.now());
            
            // Convert timeframe analyses to simplified format with null safety
            Map<String, Object> timeframes = new HashMap<>();
            if (result.getTimeframes() != null) {
                for (Map.Entry<String, TimeframeAnalysis> entry : result.getTimeframes().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        TimeframeAnalysis tf = entry.getValue();
                        Map<String, Object> tfData = new HashMap<>();
                        
                        // Safe null handling for all fields
                        tfData.put("currentPrice", tf.getCurrentPrice() != null ? tf.getCurrentPrice() : java.math.BigDecimal.ZERO);
                        tfData.put("ema200", tf.getEma200() != null ? tf.getEma200() : java.math.BigDecimal.ZERO);
                        tfData.put("emaPosition", tf.getEmaPosition() != null ? tf.getEmaPosition() : "NEAR");
                        tfData.put("trend", tf.getTrend() != null ? tf.getTrend() : "NEUTRAL");
                        tfData.put("trendStrength", tf.getTrendStrength() != null ? tf.getTrendStrength() : java.math.BigDecimal.ZERO);
                        tfData.put("usesLiveData", tf.isUsesLiveData());
                        
                        // Convert signals list with null safety
                        if (tf.getSignals() != null) {
                            tfData.put("signals", tf.getSignals());
                        } else {
                            tfData.put("signals", new java.util.ArrayList<>());
                        }
                        
                        // Convert patterns to simplified format with null safety
                        if (tf.getPatterns() != null && !tf.getPatterns().isEmpty()) {
                            tfData.put("patterns", tf.getPatterns().stream()
                                .filter(pattern -> pattern != null)
                                .map(pattern -> {
                                    Map<String, Object> p = new HashMap<>();
                                    p.put("type", pattern.getType() != null ? pattern.getType().name() : "UNKNOWN");
                                    p.put("description", pattern.getDescription() != null ? pattern.getDescription() : "");
                                    p.put("confidence", pattern.getConfidence() != null ? pattern.getConfidence() : java.math.BigDecimal.ZERO);
                                    if (pattern.getPriceLevel() != null) {
                                        p.put("priceLevel", pattern.getPriceLevel());
                                    }
                                    return p;
                                })
                                .collect(Collectors.toList()));
                        } else {
                            tfData.put("patterns", new java.util.ArrayList<>());
                        }
                        
                        timeframes.put(entry.getKey(), tfData);
                    }
                }
            }
            
            response.put("timeframes", timeframes);
            
            // Add pattern analysis information with null safety
            response.put("patternsDetected", result.getAllPatternsDetected() != null ? 
                result.getAllPatternsDetected() : new java.util.ArrayList<>());
            response.put("primaryPatterns", result.getPrimaryPatterns() != null ? 
                result.getPrimaryPatterns() : new java.util.ArrayList<>());
            response.put("patternsByTimeframe", result.getPatternsByTimeframe() != null ? 
                result.getPatternsByTimeframe() : new java.util.HashMap<>());
            response.put("patternClassification", result.getPatternClassification() != null ? 
                result.getPatternClassification() : new java.util.HashMap<>());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Invalid input");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("symbol", symbol);
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Chart analysis failed");
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
            errorResponse.put("symbol", symbol);
            System.err.println("Chart analysis error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * GET /api/chart/analyze/{symbol}/summary
     * Get simplified summary of chart analysis
     */
    @GetMapping("/analyze/{symbol}/summary")
    public ResponseEntity<Map<String, Object>> analyzeChartSummary(@PathVariable String symbol) {
        // Validate input
        if (symbol == null || symbol.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Invalid symbol");
            errorResponse.put("message", "Stock symbol cannot be empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        try {
            ChartAnalysisResult result = chartAnalysisService.analyzeChart(symbol);
            
            // Validate result
            if (result == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Analysis failed");
                errorResponse.put("message", "Unable to analyze chart for symbol: " + symbol);
                return ResponseEntity.status(500).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("symbol", result.getSymbol() != null ? result.getSymbol() : symbol);
            response.put("trend", result.getTrend() != null ? result.getTrend() : "NEUTRAL");
            response.put("confidence", result.getConfidence() != null ? 
                result.getConfidence() + "%" : "0%");
            response.put("summary", result.getSummary() != null ? result.getSummary() : "No analysis available");
            
            // Pattern information with null safety
            response.put("primaryPatterns", result.getPrimaryPatterns() != null ? 
                result.getPrimaryPatterns() : new java.util.ArrayList<>());
            response.put("patternClassification", result.getPatternClassification() != null ? 
                result.getPatternClassification() : new java.util.HashMap<>());
            
            // Simplified timeframe signals with null safety
            Map<String, Object> timeframeSignals = new HashMap<>();
            if (result.getTimeframes() != null) {
                for (Map.Entry<String, TimeframeAnalysis> entry : result.getTimeframes().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        timeframeSignals.put(entry.getKey(), 
                            entry.getValue().getSignals() != null ? 
                                entry.getValue().getSignals() : new java.util.ArrayList<>());
                    }
                }
            }
            response.put("timeframes", timeframeSignals);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Invalid input");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("symbol", symbol);
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Chart analysis failed");
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
            errorResponse.put("symbol", symbol);
            System.err.println("Chart analysis error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

