package com.trading.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class to format volume values with appropriate units (K, M, B, etc.)
 */
public class VolumeFormatter {
    
    /**
     * Formats volume with appropriate unit suffix
     * Examples:
     * - 1,000 -> "1.0K"
     * - 1,500,000 -> "1.5M"
     * - 1,000,000,000 -> "1.0B"
     * 
     * @param volume Volume as BigDecimal
     * @return Formatted volume string with unit
     */
    public static String formatVolume(BigDecimal volume) {
        if (volume == null || volume.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        
        BigDecimal absVolume = volume.abs();
        String sign = volume.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        
        // Billions
        if (absVolume.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            BigDecimal billions = absVolume.divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP);
            return sign + billions.stripTrailingZeros().toPlainString() + "B";
        }
        
        // Millions
        if (absVolume.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            BigDecimal millions = absVolume.divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP);
            return sign + millions.stripTrailingZeros().toPlainString() + "M";
        }
        
        // Thousands
        if (absVolume.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            BigDecimal thousands = absVolume.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP);
            return sign + thousands.stripTrailingZeros().toPlainString() + "K";
        }
        
        // Less than 1000, return as is
        return sign + absVolume.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
    
    /**
     * Formats volume with unit and returns both formatted string and raw value
     * 
     * @param volume Volume as BigDecimal
     * @return Map with "formatted" (string) and "raw" (BigDecimal) keys
     */
    public static VolumeInfo formatVolumeWithInfo(BigDecimal volume) {
        return new VolumeInfo(volume, formatVolume(volume));
    }
    
    /**
     * Formats value (for market cap, traded value, etc.) with appropriate unit
     * 
     * @param value Value as BigDecimal
     * @return Formatted value string with unit
     */
    public static String formatValue(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "₹0";
        }
        
        BigDecimal absValue = value.abs();
        String sign = value.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        
        // Crores (Indian numbering system - 1 Crore = 10 Million)
        if (absValue.compareTo(BigDecimal.valueOf(10_000_000)) >= 0) {
            BigDecimal crores = absValue.divide(BigDecimal.valueOf(10_000_000), 2, RoundingMode.HALF_UP);
            return sign + "₹" + crores.stripTrailingZeros().toPlainString() + "Cr";
        }
        
        // Lakhs (Indian numbering system - 1 Lakh = 100 Thousand)
        if (absValue.compareTo(BigDecimal.valueOf(100_000)) >= 0) {
            BigDecimal lakhs = absValue.divide(BigDecimal.valueOf(100_000), 2, RoundingMode.HALF_UP);
            return sign + "₹" + lakhs.stripTrailingZeros().toPlainString() + "L";
        }
        
        // Thousands
        if (absValue.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            BigDecimal thousands = absValue.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP);
            return sign + "₹" + thousands.stripTrailingZeros().toPlainString() + "K";
        }
        
        // Less than 1000
        return sign + "₹" + absValue.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    /**
     * Class to hold both formatted and raw volume information
     */
    public static class VolumeInfo {
        private final BigDecimal raw;
        private final String formatted;
        
        public VolumeInfo(BigDecimal raw, String formatted) {
            this.raw = raw;
            this.formatted = formatted;
        }
        
        public BigDecimal getRaw() {
            return raw;
        }
        
        public String getFormatted() {
            return formatted;
        }
    }
}

