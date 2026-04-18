package com.trading.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Analysis results for a specific timeframe
 */
public class TimeframeAnalysis {
    private String timeframe; // "15m", "1h", "4h", "1d", "1w", "1M"
    private BigDecimal currentPrice;
    private BigDecimal ema200;
    private String emaPosition; // "ABOVE", "BELOW", "NEAR"
    private List<ChartPattern> patterns;
    private String trend; // "BULLISH", "BEARISH", "NEUTRAL"
    private BigDecimal trendStrength; // 0-100
    private List<String> signals; // Summary signals
    private boolean usesLiveData; // Indicates if live market data was used
    
    public TimeframeAnalysis() {
        this.patterns = new ArrayList<>();
        this.signals = new ArrayList<>();
        this.usesLiveData = false;
    }
    
    public TimeframeAnalysis(String timeframe) {
        this();
        this.timeframe = timeframe;
    }
    
    // Getters and Setters
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    
    public BigDecimal getEma200() { return ema200; }
    public void setEma200(BigDecimal ema200) { this.ema200 = ema200; }
    
    public String getEmaPosition() { return emaPosition; }
    public void setEmaPosition(String emaPosition) { this.emaPosition = emaPosition; }
    
    public List<ChartPattern> getPatterns() { return patterns; }
    public void setPatterns(List<ChartPattern> patterns) { this.patterns = patterns; }
    
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
    
    public BigDecimal getTrendStrength() { return trendStrength; }
    public void setTrendStrength(BigDecimal trendStrength) { this.trendStrength = trendStrength; }
    
    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }
    
    public boolean isUsesLiveData() { return usesLiveData; }
    public void setUsesLiveData(boolean usesLiveData) { this.usesLiveData = usesLiveData; }
    
    public void addPattern(ChartPattern pattern) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<>();
        }
        this.patterns.add(pattern);
    }
    
    public void addSignal(String signal) {
        if (this.signals == null) {
            this.signals = new ArrayList<>();
        }
        this.signals.add(signal);
    }
}

