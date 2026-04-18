package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Complete chart analysis result across all timeframes
 */
public class ChartAnalysisResult {
    private String symbol;
    private String trend; // "BULLISH", "BEARISH", "NEUTRAL"
    private BigDecimal confidence; // 0-100
    private String summary;
    private Map<String, TimeframeAnalysis> timeframes; // Key: "15m", "1h", etc.
    private LocalDateTime analysisTime;
    
    // Pattern analysis
    private List<String> allPatternsDetected; // All unique patterns found across all timeframes
    private List<String> primaryPatterns; // Main patterns the stock is currently matching
    private Map<String, List<String>> patternsByTimeframe; // Patterns grouped by timeframe
    private Map<String, String> patternClassification; // Which pattern category the stock falls under
    
    public ChartAnalysisResult() {
        this.timeframes = new HashMap<>();
        this.allPatternsDetected = new ArrayList<>();
        this.primaryPatterns = new ArrayList<>();
        this.patternsByTimeframe = new HashMap<>();
        this.patternClassification = new HashMap<>();
    }
    
    public ChartAnalysisResult(String symbol) {
        this();
        this.symbol = symbol;
        this.analysisTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
    
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    
    public Map<String, TimeframeAnalysis> getTimeframes() { return timeframes; }
    public void setTimeframes(Map<String, TimeframeAnalysis> timeframes) { this.timeframes = timeframes; }
    
    public LocalDateTime getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(LocalDateTime analysisTime) { this.analysisTime = analysisTime; }
    
    public void addTimeframeAnalysis(String timeframe, TimeframeAnalysis analysis) {
        this.timeframes.put(timeframe, analysis);
    }
    
    // Pattern analysis getters and setters
    public List<String> getAllPatternsDetected() { return allPatternsDetected; }
    public void setAllPatternsDetected(List<String> allPatternsDetected) { this.allPatternsDetected = allPatternsDetected; }
    
    public List<String> getPrimaryPatterns() { return primaryPatterns; }
    public void setPrimaryPatterns(List<String> primaryPatterns) { this.primaryPatterns = primaryPatterns; }
    
    public Map<String, List<String>> getPatternsByTimeframe() { return patternsByTimeframe; }
    public void setPatternsByTimeframe(Map<String, List<String>> patternsByTimeframe) { this.patternsByTimeframe = patternsByTimeframe; }
    
    public Map<String, String> getPatternClassification() { return patternClassification; }
    public void setPatternClassification(Map<String, String> patternClassification) { this.patternClassification = patternClassification; }
}

