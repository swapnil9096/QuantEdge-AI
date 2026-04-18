# Market Status-Aware Strategy Feature

## Overview

The trading strategy now intelligently checks whether the NSE market is open or closed and adapts its data fetching and analysis accordingly.

## Features

### 1. Market Status Detection

**Service**: `MarketStatusService`

- Checks if NSE market is currently open (9:15 AM - 3:30 PM IST, Monday-Friday)
- Handles weekends and holidays
- Provides detailed market status information
- Calculates time until market opens/closes

**Market Hours**:
- **Open**: 9:15 AM IST
- **Close**: 3:30 PM IST
- **Days**: Monday to Friday (closed on weekends)

### 2. Adaptive Data Fetching

#### When Market is OPEN:
- **Real-time Data**: Fetches live price data from NSE
- **Real-time Technical Indicators**: Uses current market data for RSI, MACD, etc.
- **Latest News**: Fetches most recent news articles
- **Live Analysis**: Provides up-to-the-minute analysis

#### When Market is CLOSED:
- **Historical Data**: Uses last trading day's closing data
- **Historical Technical Indicators**: Uses historical data for analysis
- **Pre-market/After-hours News**: Fetches news from last trading day and pre-market updates
- **Historical Analysis**: Provides analysis based on last known data

### 3. Strategy Behavior

#### Comprehensive Buy Strategy (`ComprehensiveBuyStrategyService`)

1. **Checks Market Status First**
   ```java
   MarketStatusService.MarketStatus marketStatus = marketStatusService.getMarketStatus();
   boolean isMarketOpen = marketStatus.isOpen();
   ```

2. **Adapts Data Fetching**
   - Real-time data when market is open
   - Historical data when market is closed
   - Adds warnings/reasons about market status

3. **Technical Analysis**
   - Real-time indicators during market hours
   - Historical indicators when market is closed
   - Logs which type of data is being used

4. **News Analysis**
   - Latest news during market hours
   - Historical/pre-market news when closed
   - Adjusts news fetching strategy accordingly

### 4. Backtesting Service

- Always uses historical data (regardless of current market status)
- Logs market status for informational purposes
- Fetches real historical data from NSE when available
- Falls back to simulated data if NSE is unavailable

## API Response Changes

### Analyze Endpoint Response

When market is closed, the response includes:
```json
{
  "warnings": [
    "Market is currently closed. Analysis based on last trading day data."
  ],
  "reasons": [
    "Market Status: Market closed at 3:30 PM IST. Opens tomorrow at 9:15 AM IST"
  ]
}
```

When market is open:
```json
{
  "reasons": [
    "Using real-time technical indicators (Market is open)",
    "Fetching real-time news (Market is open)"
  ]
}
```

## Usage Examples

### Check Market Status

```java
MarketStatusService marketStatusService = ...;
MarketStatusService.MarketStatus status = marketStatusService.getMarketStatus();

System.out.println("Market is: " + (status.isOpen() ? "OPEN" : "CLOSED"));
System.out.println(status.getStatusMessage());
```

### In Strategy Analysis

The strategy automatically checks market status:

```bash
# During market hours (9:15 AM - 3:30 PM IST)
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE
# Returns: Real-time analysis with live data

# After market hours
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE
# Returns: Historical analysis with last trading day data
```

## Benefits

1. **Accurate Analysis**: Uses appropriate data source based on market status
2. **Clear Communication**: Users know whether they're seeing real-time or historical data
3. **Efficient**: Doesn't waste resources trying to fetch real-time data when market is closed
4. **Robust**: Handles edge cases like weekends, holidays, and market hours

## Technical Details

### Market Status Service Methods

- `isMarketOpen()` - Check if market is currently open
- `isMarketOpen(LocalDateTime)` - Check if market was open at specific time
- `wasMarketOpenOnDate(LocalDateTime)` - Check if market was open on a date
- `getMarketStatus()` - Get detailed market status information
- `shouldUseRealTimeData()` - Determine if real-time data should be used
- `getMostRecentTradingDay()` - Get the most recent trading day

### Integration Points

1. **ComprehensiveBuyStrategyService**: Checks market status before analysis
2. **NewsAnalysisService**: Adapts news fetching based on market status
3. **BacktestingService**: Uses market status for logging (always uses historical data)
4. **ChartAnalysisService**: Can be enhanced to use market status

## Configuration

Market hours are configured in `MarketStatusService`:
- `MARKET_OPEN_TIME`: 9:15 AM IST
- `MARKET_CLOSE_TIME`: 3:30 PM IST
- Timezone: Asia/Kolkata (IST)

## Future Enhancements

1. **Holiday Calendar**: Integrate NSE holiday calendar for accurate trading day detection
2. **Pre-market/After-hours**: Separate handling for extended hours
3. **Market Status API**: Expose market status as a separate endpoint
4. **Notifications**: Alert users when market opens/closes

## Testing

Test market status awareness:

```bash
# Test during market hours (9:15 AM - 3:30 PM IST)
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE

# Test after market hours
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE

# Check logs for market status messages
```

The strategy now intelligently adapts to market status, providing accurate analysis whether the market is open or closed!

