package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single candlestick/bar data point
 */
public class CandleData {
    private LocalDateTime timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    
    public CandleData() {}
    
    public CandleData(LocalDateTime timestamp, BigDecimal open, BigDecimal high, 
                     BigDecimal low, BigDecimal close, BigDecimal volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
    
    // Getters and Setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    
    /**
     * Check if candle is bullish (close > open)
     */
    public boolean isBullish() {
        return close != null && open != null && close.compareTo(open) > 0;
    }
    
    /**
     * Check if candle is bearish (close < open)
     */
    public boolean isBearish() {
        return close != null && open != null && close.compareTo(open) < 0;
    }
    
    /**
     * Get candle body size
     */
    public BigDecimal getBodySize() {
        if (open == null || close == null) return BigDecimal.ZERO;
        return close.subtract(open).abs();
    }
    
    /**
     * Get upper wick size
     */
    public BigDecimal getUpperWick() {
        if (high == null || open == null || close == null) return BigDecimal.ZERO;
        BigDecimal top = open.compareTo(close) > 0 ? open : close;
        return high.subtract(top);
    }
    
    /**
     * Get lower wick size
     */
    public BigDecimal getLowerWick() {
        if (low == null || open == null || close == null) return BigDecimal.ZERO;
        BigDecimal bottom = open.compareTo(close) < 0 ? open : close;
        return bottom.subtract(low);
    }
}

