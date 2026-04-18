package com.trading.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a detected chart pattern
 */
public class ChartPattern {
    public enum PatternType {
        // Gap Patterns
        FAIR_VALUE_GAP,
        
        // Order Blocks
        ORDER_BLOCK_BULLISH,
        ORDER_BLOCK_BEARISH,
        
        // Engulfing Patterns
        BULLISH_ENGULFING,
        BEARISH_ENGULFING,
        
        // Reversal Patterns
        W_PATTERN,  // Double Bottom
        M_PATTERN,  // Double Top
        HEAD_AND_SHOULDERS,
        INVERSE_HEAD_AND_SHOULDERS,
        
        // Single Candlestick Patterns
        HAMMER,              // Bullish reversal
        SHOOTING_STAR,       // Bearish reversal
        HANGING_MAN,         // Bearish reversal
        INVERTED_HAMMER,     // Bullish reversal
        DOJI,                // Indecision
        
        // Two-Candle Patterns
        BULLISH_HARAMI,      // Bullish reversal
        BEARISH_HARAMI,      // Bearish reversal
        PIERCING_PATTERN,    // Bullish reversal
        DARK_CLOUD_COVER,    // Bearish reversal
        
        // Three-Candle Patterns
        MORNING_STAR,        // Bullish reversal
        EVENING_STAR,        // Bearish reversal
        THREE_WHITE_SOLDIERS, // Strong bullish
        THREE_BLACK_CROWS,    // Strong bearish
        
        // Continuation Patterns
        ASCENDING_TRIANGLE,
        DESCENDING_TRIANGLE,
        SYMMETRICAL_TRIANGLE,
        BULLISH_FLAG,
        BEARISH_FLAG,
        BULLISH_PENNANT,
        BEARISH_PENNANT,
        RISING_WEDGE,        // Bearish
        FALLING_WEDGE,       // Bullish
        
        // Other Patterns
        CUP_AND_HANDLE,
        ROUNDING_BOTTOM,
        ROUNDING_TOP,
        
        // Trend Indicators
        ABOVE_200_EMA,
        BELOW_200_EMA
    }
    
    private PatternType type;
    private String description;
    private BigDecimal confidence; // 0-100
    private List<CandleData> involvedCandles;
    private BigDecimal priceLevel; // Key price level for the pattern
    
    public ChartPattern() {}
    
    public ChartPattern(PatternType type, String description, BigDecimal confidence) {
        this.type = type;
        this.description = description;
        this.confidence = confidence;
    }
    
    // Getters and Setters
    public PatternType getType() { return type; }
    public void setType(PatternType type) { this.type = type; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    
    public List<CandleData> getInvolvedCandles() { return involvedCandles; }
    public void setInvolvedCandles(List<CandleData> involvedCandles) { this.involvedCandles = involvedCandles; }
    
    public BigDecimal getPriceLevel() { return priceLevel; }
    public void setPriceLevel(BigDecimal priceLevel) { this.priceLevel = priceLevel; }
}

