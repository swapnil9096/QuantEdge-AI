package com.trading.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;

/**
 * Service to check NSE market status (open/closed)
 */
@Service
@Slf4j
public class MarketStatusService {
    
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 15); // 9:15 AM IST
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30); // 3:30 PM IST
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    
    /**
     * Check if NSE market is currently open
     * 
     * @return true if market is open, false otherwise
     */
    public boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now(IST_ZONE);
        return isMarketOpen(now);
    }
    
    /**
     * Check if NSE market is open at a specific time
     * 
     * @param dateTime The date and time to check
     * @return true if market is open at that time, false otherwise
     */
    public boolean isMarketOpen(LocalDateTime dateTime) {
        // Convert to IST if needed
        LocalDateTime istDateTime = dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(IST_ZONE)
                .toLocalDateTime();
        
        DayOfWeek dayOfWeek = istDateTime.getDayOfWeek();
        LocalTime time = istDateTime.toLocalTime();
        
        // Market is closed on weekends
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            log.debug("Market is closed - Weekend ({}), Time: {}", dayOfWeek, time);
            return false;
        }
        
        // Check if time is within market hours (9:15 AM to 3:30 PM IST)
        boolean isOpen = !time.isBefore(MARKET_OPEN_TIME) && !time.isAfter(MARKET_CLOSE_TIME);
        
        if (isOpen) {
            log.debug("Market is OPEN - Day: {}, Time: {} IST", dayOfWeek, time);
        } else {
            log.debug("Market is CLOSED - Day: {}, Time: {} IST (Market hours: {} to {})", 
                    dayOfWeek, time, MARKET_OPEN_TIME, MARKET_CLOSE_TIME);
        }
        
        return isOpen;
    }
    
    /**
     * Check if market was open on a specific date
     * 
     * @param date The date to check
     * @return true if market was open on that date, false otherwise
     */
    public boolean wasMarketOpenOnDate(LocalDateTime date) {
        LocalDateTime istDate = date.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(IST_ZONE)
                .toLocalDateTime();
        
        DayOfWeek dayOfWeek = istDate.getDayOfWeek();
        
        // Market is closed on weekends
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        // Check if it's a trading day (not a holiday)
        // Note: This is a simplified check. In production, you'd check against NSE holiday calendar
        return true;
    }
    
    /**
     * Get market status information
     * 
     * @return MarketStatus object with detailed information
     */
    public MarketStatus getMarketStatus() {
        LocalDateTime now = LocalDateTime.now(IST_ZONE);
        boolean isOpen = isMarketOpen(now);
        
        MarketStatus status = MarketStatus.builder()
                .isOpen(isOpen)
                .currentTime(now)
                .marketOpenTime(MARKET_OPEN_TIME)
                .marketCloseTime(MARKET_CLOSE_TIME)
                .dayOfWeek(now.getDayOfWeek())
                .build();
        
        if (!isOpen) {
            // Calculate time until market opens or closes
            if (now.toLocalTime().isBefore(MARKET_OPEN_TIME)) {
                status.setTimeUntilOpen(java.time.Duration.between(now.toLocalTime(), MARKET_OPEN_TIME));
                status.setStatusMessage("Market opens at " + MARKET_OPEN_TIME + " IST");
            } else if (now.toLocalTime().isAfter(MARKET_CLOSE_TIME)) {
                status.setStatusMessage("Market closed at " + MARKET_CLOSE_TIME + " IST. Opens tomorrow at " + MARKET_OPEN_TIME + " IST");
            } else if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
                status.setStatusMessage("Market is closed - Weekend");
            }
        } else {
            status.setTimeUntilClose(java.time.Duration.between(now.toLocalTime(), MARKET_CLOSE_TIME));
            status.setStatusMessage("Market is open until " + MARKET_CLOSE_TIME + " IST");
        }
        
        return status;
    }
    
    /**
     * Check if we should use real-time data or historical data
     * 
     * @return true if should use real-time data (market is open), false for historical
     */
    public boolean shouldUseRealTimeData() {
        return isMarketOpen();
    }
    
    /**
     * Get the most recent trading day
     * 
     * @return LocalDateTime of the most recent trading day's market close
     */
    public LocalDateTime getMostRecentTradingDay() {
        LocalDateTime now = LocalDateTime.now(IST_ZONE);
        
        // If market is open today, return today's date
        if (isMarketOpen(now)) {
            return now;
        }
        
        // Otherwise, go back to find the last trading day
        LocalDateTime checkDate = now;
        int daysBack = 0;
        while (daysBack < 10) { // Don't go back more than 10 days
            if (wasMarketOpenOnDate(checkDate)) {
                // Return the market close time of that day
                return checkDate.toLocalDate().atTime(MARKET_CLOSE_TIME);
            }
            checkDate = checkDate.minusDays(1);
            daysBack++;
        }
        
        // Fallback to yesterday
        return now.minusDays(1).toLocalDate().atTime(MARKET_CLOSE_TIME);
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MarketStatus {
        private boolean isOpen;
        private LocalDateTime currentTime;
        private LocalTime marketOpenTime;
        private LocalTime marketCloseTime;
        private DayOfWeek dayOfWeek;
        private java.time.Duration timeUntilOpen;
        private java.time.Duration timeUntilClose;
        private String statusMessage;
    }
}

