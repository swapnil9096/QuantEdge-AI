# Comprehensive Buy Strategy & Backtesting Guide

## Overview

This document describes the new comprehensive trading strategy that evaluates all fundamental metrics, technical indicators, and relevant news to determine whether to buy a stock, along with a complete backtesting system.

## Features

### 1. Comprehensive Buy Strategy (`ComprehensiveBuyStrategyService`)

The strategy evaluates three key areas:

#### Fundamental Analysis (40% weight)
- **P/E Ratio**: Evaluates valuation (< 15 = undervalued, 15-25 = fair, > 25 = overvalued)
- **P/B Ratio**: Assesses book value (< 1 = strong value, 1-3 = reasonable, > 3 = elevated)
- **EPS**: Measures profitability (> 50 = strong, > 20 = decent)
- **Market Cap**: Categorizes stock size (Large/Mid/Small/Micro cap)
- **Dividend Yield**: Evaluates income potential (> 3% = attractive)
- **52-Week Position**: Analyzes price relative to yearly range

#### Technical Analysis (40% weight)
- **RSI**: Identifies overbought/oversold conditions
- **MACD**: Detects momentum and trend changes
- **Moving Averages**: SMA20 and SMA50 for trend analysis
- **Chart Patterns**: Identifies bullish/bearish patterns across multiple timeframes
- **Volume Analysis**: Assesses market interest
- **Trend Analysis**: Determines overall market direction

#### News Analysis (20% weight)
- **Sentiment Analysis**: Analyzes news articles for positive/negative sentiment
- **News Count**: Considers volume of recent news
- **Keyword Analysis**: Identifies positive/negative keywords in news

### 2. Backtesting Service (`BacktestingService`)

Simulates trading with historical data:
- **Initial Capital**: Default 100,000 (configurable)
- **Position Sizing**: 20% of capital per trade
- **Maximum Positions**: 3 concurrent positions
- **Exit Conditions**:
  - Target: +5% gain
  - Stop Loss: -3% loss
  - Time-based: 10 days maximum holding period

### 3. Performance Metrics

The backtesting system calculates:
- **Overall Return**: Total profit/loss
- **Overall Return %**: Percentage return on initial capital
- **Win Rate**: Percentage of winning trades
- **Average Return per Trade**: Mean profit/loss per trade
- **Average Win/Loss**: Mean values for winning and losing trades
- **Profit Factor**: Ratio of total wins to total losses
- **Maximum Drawdown**: Largest peak-to-trough decline
- **Sharpe Ratio**: Risk-adjusted return measure

## API Endpoints

### 1. Analyze Buy Decision

**Endpoint**: `GET /api/comprehensive-strategy/analyze/{symbol}`

**Example**:
```bash
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE
```

**Response**:
```json
{
  "success": true,
  "symbol": "RELIANCE",
  "companyName": "RELIANCE INDUSTRIES LTD",
  "decision": "BUY",
  "overallScore": 72.5,
  "fundamentalScore": 75.0,
  "technicalScore": 70.0,
  "newsScore": 65.0,
  "confidence": 75.0,
  "explanation": "=== BUY DECISION ANALYSIS...",
  "reasons": [
    "P/E ratio of 15.5 indicates reasonable valuation",
    "RSI of 35.2 indicates oversold condition",
    "Positive news sentiment (60% positive news)"
  ],
  "warnings": [
    "Stock is near 52-week high (85%), may be overbought"
  ],
  "strengths": [
    "Strong fundamental metrics",
    "Strong technical indicators"
  ]
}
```

### 2. Backtest Strategy

**Endpoint**: `GET /api/comprehensive-strategy/backtest/{symbol}?capital=100000&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59`

**Parameters**:
- `capital`: Initial capital (default: 100000)
- `startDate`: Start date for backtesting (ISO format, default: 6 months ago)
- `endDate`: End date for backtesting (ISO format, default: now)

**Example**:
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000"
```

**Response**:
```json
{
  "success": true,
  "symbol": "RELIANCE",
  "initialCapital": 100000,
  "performanceMetrics": {
    "overallReturn": 12500.50,
    "overallReturnPercent": 12.50,
    "finalCapital": 112500.50,
    "totalTrades": 25,
    "winningTrades": 18,
    "losingTrades": 7,
    "winRate": 72.00,
    "averageReturnPerTrade": 500.02,
    "averageWin": 1200.50,
    "averageLoss": -450.30,
    "profitFactor": 2.67,
    "maxDrawdown": 5.20,
    "sharpeRatio": 1.85
  },
  "totalTrades": 25
}
```

### 3. Health Check

**Endpoint**: `GET /api/comprehensive-strategy/health`

## Decision Logic

The strategy generates one of five decisions:

1. **STRONG_BUY** (Score ≥ 75): Strong fundamentals, positive technicals, favorable news
2. **BUY** (Score 65-74): Good potential with balanced signals
3. **WEAK_BUY** (Score 55-64): Some positive signals but with concerns
4. **HOLD** (Score 45-54): Mixed signals, wait for clearer direction
5. **AVOID** (Score < 45): Multiple concerns, avoid buying

## Configuration

Add to `application.yml`:

```yaml
api:
  news:
    enabled: true
    source: mock  # Options: mock, web, mcp
```

## Usage Examples

### Python Example

```python
import requests

# Analyze buy decision
response = requests.get('http://localhost:8080/api/comprehensive-strategy/analyze/TCS')
data = response.json()

if data['success']:
    print(f"Decision: {data['decision']}")
    print(f"Overall Score: {data['overallScore']}")
    print(f"Confidence: {data['confidence']}%")
    print("\nReasons:")
    for reason in data['reasons']:
        print(f"  - {reason}")

# Backtest strategy
response = requests.get(
    'http://localhost:8080/api/comprehensive-strategy/backtest/TCS',
    params={'capital': 100000}
)
data = response.json()

if data['success']:
    metrics = data['performanceMetrics']
    print(f"\nBacktest Results:")
    print(f"Overall Return: {metrics['overallReturnPercent']}%")
    print(f"Win Rate: {metrics['winRate']}%")
    print(f"Total Trades: {metrics['totalTrades']}")
    print(f"Profit Factor: {metrics['profitFactor']}")
```

### JavaScript Example

```javascript
// Analyze buy decision
fetch('http://localhost:8080/api/comprehensive-strategy/analyze/INFY')
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      console.log('Decision:', data.decision);
      console.log('Score:', data.overallScore);
      console.log('Reasons:', data.reasons);
    }
  });

// Backtest strategy
fetch('http://localhost:8080/api/comprehensive-strategy/backtest/INFY?capital=100000')
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      const metrics = data.performanceMetrics;
      console.log('Return:', metrics.overallReturnPercent + '%');
      console.log('Win Rate:', metrics.winRate + '%');
    }
  });
```

## Architecture

### Services

1. **ComprehensiveBuyStrategyService**: Main strategy evaluation service
2. **NewsAnalysisService**: News fetching and sentiment analysis
3. **BacktestingService**: Historical simulation and performance calculation
4. **ComprehensiveNseDataService**: Data fetching via MCP/NSE APIs

### Data Flow

```
Stock Symbol
    ↓
ComprehensiveNseDataService (MCP) → Stock Data
    ↓
ComprehensiveBuyStrategyService
    ├── Fundamental Evaluation
    ├── Technical Evaluation (via RealTimeAnalysisService)
    └── News Evaluation (via NewsAnalysisService)
    ↓
Buy Decision Result
    ↓
BacktestingService (if backtesting)
    ↓
Performance Metrics
```

## Notes

- The strategy uses MCP servers for data fetching as configured
- News analysis currently uses mock data for testing (configurable)
- Backtesting generates simulated historical data if real historical data is unavailable
- All calculations use BigDecimal for precision
- The system is designed to be extensible for additional indicators and metrics

## Future Enhancements

- Real news API integration (NewsAPI, Alpha Vantage, etc.)
- Historical fundamental data fetching
- More sophisticated sentiment analysis using NLP
- Additional technical indicators
- Portfolio-level backtesting
- Risk-adjusted position sizing
- Real-time strategy monitoring

