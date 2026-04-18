# New API Endpoints - Comprehensive Trading Strategy

## Overview

Three new API endpoints have been created for the comprehensive buy strategy and backtesting functionality.

---

## 📊 API Endpoints

### 1. **Analyze Buy Decision**

**Endpoint**: `GET /api/comprehensive-strategy/analyze/{symbol}`

**Description**: Analyzes a stock to determine whether to buy it, evaluating all fundamental metrics, technical indicators, and news sentiment.

**URL**: `http://localhost:8080/api/comprehensive-strategy/analyze/{symbol}`

**Path Parameters**:
- `symbol` (required): Stock symbol (e.g., RELIANCE, TCS, INFY)

**Example Request**:
```bash
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE
```

**Response Structure**:
```json
{
  "success": true,
  "symbol": "RELIANCE",
  "companyName": "RELIANCE INDUSTRIES LTD",
  "sector": "Oil & Gas",
  "industry": "Refineries",
  "currentPrice": 2450.50,
  "decision": "BUY",
  "overallScore": 72.5,
  "fundamentalScore": 75.0,
  "technicalScore": 70.0,
  "newsScore": 65.0,
  "confidence": 75.0,
  "explanation": "=== BUY DECISION ANALYSIS FOR RELIANCE ===\n\nOverall Score: 72.5/100\n...",
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
  ],
  "analysisTime": "2024-01-15T10:30:00",
  "fundamentalEvaluation": {
    "peRatio": 15.5,
    "pbRatio": 2.1,
    "eps": 158.06,
    "score": 75.0,
    "reasons": [...],
    "warnings": [...]
  },
  "technicalEvaluation": {
    "rsi": 35.2,
    "macd": 12.5,
    "trend": "UPTREND",
    "pattern": "ASCENDING_TRIANGLE",
    "score": 70.0,
    "reasons": [...],
    "warnings": [...]
  },
  "newsEvaluation": {
    "sentiment": "POSITIVE",
    "score": 65.0,
    "reasons": [...],
    "warnings": [...],
    "newsCount": 8
  }
}
```

**Decision Values**:
- `STRONG_BUY` - Score ≥ 75
- `BUY` - Score 65-74
- `WEAK_BUY` - Score 55-64
- `HOLD` - Score 45-54
- `AVOID` - Score < 45

---

### 2. **Backtest Strategy**

**Endpoint**: `GET /api/comprehensive-strategy/backtest/{symbol}`

**Description**: Backtests the comprehensive buy strategy with historical data, simulating trades with configurable capital and calculating performance metrics.

**URL**: `http://localhost:8080/api/comprehensive-strategy/backtest/{symbol}`

**Path Parameters**:
- `symbol` (required): Stock symbol to backtest

**Query Parameters**:
- `capital` (optional): Initial capital for backtesting (default: 100000)
- `startDate` (optional): Start date in ISO format (default: 6 months ago)
- `endDate` (optional): End date in ISO format (default: now)

**Example Requests**:

**Basic backtest with default capital (100,000)**:
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE"
```

**Backtest with custom capital**:
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000"
```

**Backtest with custom dates**:
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/TCS?capital=100000&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59"
```

**Response Structure**:
```json
{
  "success": true,
  "symbol": "RELIANCE",
  "initialCapital": 100000,
  "startDate": "2024-01-01T00:00:00",
  "endDate": "2024-12-31T23:59:59",
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
  "totalTrades": 25,
  "sampleTrades": [
    {
      "symbol": "RELIANCE",
      "entryDate": "2024-01-15T09:30:00",
      "exitDate": "2024-01-17T15:30:00",
      "entryPrice": 2450.00,
      "exitPrice": 2572.50,
      "quantity": 8.16,
      "entryValue": 20000.00,
      "exitValue": 21000.00,
      "profit": 1000.00,
      "returnPercent": 5.00,
      "exitReason": "Target reached (+5%)",
      "buyDecision": "BUY",
      "buyDecisionScore": 72.5,
      "open": false
    }
  ]
}
```

**Performance Metrics Explained**:
- `overallReturn`: Total profit/loss in currency
- `overallReturnPercent`: Percentage return on initial capital
- `finalCapital`: Final capital after all trades
- `totalTrades`: Total number of trades executed
- `winningTrades`: Number of profitable trades
- `losingTrades`: Number of losing trades
- `winRate`: Percentage of winning trades
- `averageReturnPerTrade`: Average profit/loss per trade
- `averageWin`: Average profit from winning trades
- `averageLoss`: Average loss from losing trades
- `profitFactor`: Ratio of total wins to total losses (higher is better)
- `maxDrawdown`: Maximum peak-to-trough decline percentage
- `sharpeRatio`: Risk-adjusted return measure (higher is better)

**Backtesting Rules**:
- Position Size: 20% of capital per trade
- Maximum Positions: 3 concurrent positions
- Entry: BUY or STRONG_BUY signals only
- Exit Conditions:
  - Target: +5% gain
  - Stop Loss: -3% loss
  - Time-based: 10 days maximum holding period

---

### 3. **Health Check**

**Endpoint**: `GET /api/comprehensive-strategy/health`

**Description**: Health check endpoint to verify the comprehensive strategy API is running.

**URL**: `http://localhost:8080/api/comprehensive-strategy/health`

**Example Request**:
```bash
curl http://localhost:8080/api/comprehensive-strategy/health
```

**Response**:
```json
{
  "status": "UP",
  "message": "Comprehensive Strategy API is running"
}
```

---

## 📝 Usage Examples

### Python Example

```python
import requests

# Analyze buy decision
response = requests.get('http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE')
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
    'http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE',
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
fetch('http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE')
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      console.log('Decision:', data.decision);
      console.log('Score:', data.overallScore);
      console.log('Reasons:', data.reasons);
    }
  });

// Backtest strategy
fetch('http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000')
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      const metrics = data.performanceMetrics;
      console.log('Return:', metrics.overallReturnPercent + '%');
      console.log('Win Rate:', metrics.winRate + '%');
    }
  });
```

### cURL Examples

```bash
# Test all endpoints
echo "=== Health Check ==="
curl http://localhost:8080/api/comprehensive-strategy/health

echo -e "\n\n=== Analyze RELIANCE ==="
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE

echo -e "\n\n=== Backtest RELIANCE ==="
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000"

echo -e "\n\n=== Backtest TCS with dates ==="
curl "http://localhost:8080/api/comprehensive-strategy/backtest/TCS?capital=100000&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59"
```

---

## 🔍 What Each Endpoint Does

### Analyze Endpoint
1. Fetches comprehensive stock data using MCP servers
2. Evaluates fundamental metrics (P/E, P/B, EPS, Market Cap, etc.)
3. Evaluates technical indicators (RSI, MACD, Moving Averages, Chart Patterns)
4. Analyzes news sentiment
5. Calculates weighted overall score (40% fundamentals, 40% technicals, 20% news)
6. Generates buy decision with detailed explanation

### Backtest Endpoint
1. Simulates trading with historical data
2. Uses buy decisions from the analyze endpoint
3. Applies position sizing (20% of capital per trade)
4. Tracks entry/exit conditions
5. Calculates comprehensive performance metrics
6. Returns detailed trade history and statistics

---

## ⚠️ Error Responses

If an error occurs, the response will be:

```json
{
  "success": false,
  "error": "Error message describing what went wrong"
}
```

**Common HTTP Status Codes**:
- `200 OK` - Success
- `400 Bad Request` - Invalid parameters
- `500 Internal Server Error` - Server error

---

## 📚 Related Documentation

- See `COMPREHENSIVE_STRATEGY_GUIDE.md` for detailed strategy documentation
- See `HOW_TO_RUN.md` for instructions on running the application

---

## ✅ Quick Test

Test all endpoints quickly:

```bash
# 1. Health check
curl http://localhost:8080/api/comprehensive-strategy/health

# 2. Analyze a stock
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE | jq .

# 3. Backtest with 100,000 capital
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000" | jq .
```

Note: `jq` is optional for pretty JSON formatting. Install with `brew install jq` (macOS) or `sudo apt-get install jq` (Linux).

