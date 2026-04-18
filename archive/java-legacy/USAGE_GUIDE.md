# Multibagger Swing Trading Strategy - Usage Guide

## 🚀 Quick Start Guide

### 1. Starting the Application

```bash
# Navigate to project directory
cd /Users/swapnilbobade1/Documents/Trading

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 2. Testing the API

#### Health Check
```bash
curl http://localhost:8080/api/trading/health
```

#### Demo Endpoints
```bash
# Get demo information
curl http://localhost:8080/api/test/demo

# Get sample stocks
curl http://localhost:8080/api/test/sample-stocks

# Analyze a sample stock
curl http://localhost:8080/api/test/analyze/RELIANCE

# Check multibagger potential
curl http://localhost:8080/api/test/multibagger-check/TCS

# Check swing trading potential
curl http://localhost:8080/api/test/swing-trading-check/INFY
```

## 📊 Step-by-Step Implementation

### Step 1: Prepare Your Stock Data

You need to provide the following data for each stock:

#### Basic Information
- **Symbol**: Stock symbol (e.g., "RELIANCE", "TCS")
- **Name**: Company name
- **Exchange**: Stock exchange (e.g., "NSE", "BSE")
- **Sector**: Business sector
- **Industry**: Specific industry

#### Financial Metrics
- **Current Price**: Current stock price
- **Market Cap**: Market capitalization in crores
- **Volume**: Trading volume
- **P/E Ratio**: Price-to-Earnings ratio
- **P/B Ratio**: Price-to-Book ratio
- **Debt-to-Equity**: Debt to equity ratio
- **ROE**: Return on Equity (%)
- **ROA**: Return on Assets (%)
- **Revenue Growth**: Revenue growth rate (%)
- **Profit Growth**: Profit growth rate (%)

#### Price History
- **OHLC Data**: Open, High, Low, Close prices
- **Volume**: Trading volume for each period
- **Timestamp**: Date and time of each data point
- **Minimum**: 50 data points for accurate analysis

### Step 2: API Request Format

```json
{
  "symbol": "RELIANCE",
  "name": "Reliance Industries Ltd",
  "exchange": "NSE",
  "sector": "Oil & Gas",
  "industry": "Refineries",
  "currentPrice": 2500.00,
  "marketCap": 1690000.00,
  "volume": 1500000,
  "peRatio": 15.5,
  "pbRatio": 2.1,
  "debtToEquity": 0.3,
  "roe": 18.5,
  "roa": 12.3,
  "revenueGrowth": 25.5,
  "profitGrowth": 30.2,
  "priceHistory": [
    {
      "timestamp": "2024-01-01T09:15:00",
      "open": 2450.00,
      "high": 2520.00,
      "low": 2440.00,
      "close": 2500.00,
      "volume": 1500000
    }
    // ... more price data (minimum 50 points)
  ]
}
```

### Step 3: Making API Calls

#### Comprehensive Analysis
```bash
curl -X POST http://localhost:8080/api/trading/analyze \
  -H "Content-Type: application/json" \
  -d @stock_data.json
```

#### Multibagger Check
```bash
curl -X POST http://localhost:8080/api/trading/multibagger-check \
  -H "Content-Type: application/json" \
  -d @stock_data.json
```

#### Swing Trading Check
```bash
curl -X POST http://localhost:8080/api/trading/swing-trading-check \
  -H "Content-Type: application/json" \
  -d @stock_data.json
```

### Step 4: Understanding the Response

#### Analysis Result Structure
```json
{
  "stockSymbol": "RELIANCE",
  "stockName": "Reliance Industries Ltd",
  "currentPrice": 2500.00,
  "analysisTime": "2024-01-15T10:30:00",
  "overallScore": 85.5,
  "recommendation": "STRONG_BUY",
  "confidence": 92.3,
  "technicalAnalysis": {
    "rsi": 35.2,
    "macd": 12.5,
    "sma20": 2480.00,
    "sma50": 2450.00,
    "trend": "UPTREND",
    "pattern": "ASCENDING_TRIANGLE",
    "technicalScore": 88.5,
    "technicalSignals": ["RSI_OVERSOLD_BUY_SIGNAL", "MACD_BULLISH_CROSSOVER"]
  },
  "fundamentalAnalysis": {
    "peRatio": 15.5,
    "pbRatio": 2.1,
    "roe": 18.5,
    "revenueGrowth": 25.5,
    "fundamentalScore": 82.3,
    "fundamentalSignals": ["PE_RATIO_UNDERVALUED", "HIGH_ROE_EXCELLENT_PROFITABILITY"]
  },
  "multibaggerAnalysis": {
    "growthPhase": "ACCELERATED_GROWTH",
    "growthPotential": 45.2,
    "multibaggerScore": 78.9,
    "multibaggerFactors": ["HIGH_REVENUE_GROWTH", "HIGH_PROFIT_GROWTH", "SMALL_CAP_GOOD_POTENTIAL"]
  },
  "tradingSignal": {
    "signalType": "BUY",
    "strength": "STRONG",
    "entryPrice": 2500.00,
    "targetPrice": 2600.00,
    "stopLoss": 2450.00,
    "expectedReturn": 4.0,
    "confidence": 92.3,
    "reasoning": "Swing Trading Signal Analysis: ...",
    "technicalAnalysis": "Technical Indicators Summary: ...",
    "fundamentalAnalysis": "Fundamental Analysis Summary: ..."
  },
  "riskAssessment": {
    "volatility": 18.5,
    "beta": 1.2,
    "riskLevel": "MEDIUM",
    "riskFactors": ["HIGH_VOLATILITY", "SMALL_CAP_RISK"],
    "riskScore": 65.0
  }
}
```

## 🎯 Trading Strategy Implementation

### 1. Signal Interpretation

#### Signal Types
- **BUY**: Strong bullish signal
- **SELL**: Strong bearish signal
- **HOLD**: Neutral signal

#### Signal Strength
- **VERY_STRONG**: Highest confidence (90%+)
- **STRONG**: High confidence (80-90%)
- **MODERATE**: Medium confidence (70-80%)
- **WEAK**: Low confidence (<70%)

#### Recommendations
- **STRONG_BUY**: Overall score >75%, BUY signal
- **BUY**: Overall score >65%, BUY signal
- **STRONG_SELL**: Overall score >75%, SELL signal
- **SELL**: Overall score >65%, SELL signal
- **HOLD**: Overall score 50-65%
- **AVOID**: Overall score <50%

### 2. Entry and Exit Strategy

#### Entry Criteria
- **Signal Type**: BUY or SELL
- **Overall Score**: >75%
- **Confidence**: >80%
- **Expected Return**: >2%
- **Risk Level**: LOW to MEDIUM

#### Exit Criteria
- **Target Price**: 4% gain (BUY) or 4% decline (SELL)
- **Stop Loss**: 2% loss
- **Time-based**: Maximum 2 days holding
- **Signal Change**: If signal changes to opposite

### 3. Risk Management

#### Position Sizing
- **Low Risk**: 5-10% of portfolio
- **Medium Risk**: 3-5% of portfolio
- **High Risk**: 1-3% of portfolio

#### Diversification
- **Maximum Positions**: 5 stocks
- **Sector Diversification**: Max 2 stocks per sector
- **Market Cap Mix**: 60% large cap, 40% mid/small cap

## 📈 Real-World Usage Examples

### Example 1: Analyzing Reliance Industries

```bash
# Create stock_data.json
cat > stock_data.json << EOF
{
  "symbol": "RELIANCE",
  "name": "Reliance Industries Ltd",
  "exchange": "NSE",
  "sector": "Oil & Gas",
  "industry": "Refineries",
  "currentPrice": 2500.00,
  "marketCap": 1690000.00,
  "volume": 1500000,
  "peRatio": 15.5,
  "pbRatio": 2.1,
  "debtToEquity": 0.3,
  "roe": 18.5,
  "roa": 12.3,
  "revenueGrowth": 25.5,
  "profitGrowth": 30.2,
  "priceHistory": [
    {
      "timestamp": "2024-01-01T09:15:00",
      "open": 2450.00,
      "high": 2520.00,
      "low": 2440.00,
      "close": 2500.00,
      "volume": 1500000
    }
  ]
}
EOF

# Analyze the stock
curl -X POST http://localhost:8080/api/trading/analyze \
  -H "Content-Type: application/json" \
  -d @stock_data.json
```

### Example 2: Batch Analysis Script

```bash
#!/bin/bash
# analyze_stocks.sh

STOCKS=("RELIANCE" "TCS" "INFY" "HDFC" "ICICIBANK")

for stock in "${STOCKS[@]}"; do
  echo "Analyzing $stock..."
  curl -X GET "http://localhost:8080/api/test/analyze/$stock" \
    -H "Content-Type: application/json" \
    | jq '.overallScore, .recommendation, .tradingSignal.signalType'
  echo "---"
done
```

### Example 3: Python Integration

```python
import requests
import json

def analyze_stock(symbol, stock_data):
    url = "http://localhost:8080/api/trading/analyze"
    response = requests.post(url, json=stock_data)
    return response.json()

# Example usage
stock_data = {
    "symbol": "TCS",
    "name": "Tata Consultancy Services Ltd",
    "currentPrice": 3500.00,
    "marketCap": 1200000.00,
    "peRatio": 25.5,
    "pbRatio": 6.2,
    "debtToEquity": 0.1,
    "roe": 22.5,
    "roa": 15.8,
    "revenueGrowth": 12.5,
    "profitGrowth": 18.2,
    "priceHistory": []  # Add your price data
}

result = analyze_stock("TCS", stock_data)
print(f"Overall Score: {result['overallScore']}")
print(f"Recommendation: {result['recommendation']}")
print(f"Signal: {result['tradingSignal']['signalType']}")
```

## 🔧 Configuration and Customization

### 1. Strategy Parameters

Edit `src/main/resources/application.yml`:

```yaml
trading:
  strategy:
    min-return-percentage: 2.0      # Minimum return target
    max-holding-days: 2             # Maximum holding period
    accuracy-threshold: 95.0        # Minimum accuracy required
    rsi-oversold: 30               # RSI oversold level
    rsi-overbought: 70              # RSI overbought level
    macd-signal-period: 9           # MACD signal line period
    sma-short-period: 20            # Short-term SMA period
    sma-long-period: 50             # Long-term SMA period
    bollinger-period: 20            # Bollinger Bands period
    bollinger-standard-deviation: 2  # Bollinger Bands std dev
```

### 2. Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:tradingdb
    driverClassName: org.h2.Driver
    username: sa
    password: password
  
  h2:
    console:
      enabled: true
      path: /h2-console
```

### 3. Logging Configuration

```yaml
logging:
  level:
    com.trading: DEBUG
    org.springframework.web: DEBUG
```

## 📊 Monitoring and Performance

### 1. Key Metrics to Monitor

- **Signal Generation Rate**: Number of signals per day
- **Accuracy Rate**: Percentage of correct signals
- **Average Return**: Average return per signal
- **Win Rate**: Percentage of profitable trades
- **Maximum Drawdown**: Largest peak-to-trough decline

### 2. Performance Optimization

- **Data Quality**: Ensure accurate and complete data
- **Regular Updates**: Update price data frequently
- **Backtesting**: Test strategies on historical data
- **Risk Management**: Monitor risk scores continuously

### 3. Troubleshooting

#### Common Issues

1. **Insufficient Data Error**
   - Ensure at least 50 price data points
   - Check data quality and completeness

2. **Low Confidence Scores**
   - Verify fundamental data accuracy
   - Check technical indicator calculations

3. **No Signals Generated**
   - Adjust strategy parameters
   - Lower accuracy thresholds for testing

#### Debug Mode

Enable debug logging:
```yaml
logging:
  level:
    com.trading: DEBUG
```

## 🚨 Important Notes

### 1. Data Requirements
- **Minimum Price History**: 50 data points
- **Data Quality**: Accurate OHLC data
- **Frequency**: Daily or intraday data
- **Completeness**: All required fields must be present

### 2. Risk Considerations
- **Market Risk**: Stock prices can be volatile
- **System Risk**: Technical analysis is not foolproof
- **Data Risk**: Inaccurate data leads to wrong signals
- **Execution Risk**: Market conditions may change

### 3. Best Practices
- **Paper Trading**: Test strategies before live trading
- **Position Sizing**: Never risk more than you can afford to lose
- **Diversification**: Don't put all eggs in one basket
- **Regular Monitoring**: Check signals and market conditions

## 📞 Support and Help

### 1. API Documentation
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`

### 2. Sample Data
- Use test endpoints for sample data
- Generate realistic test scenarios
- Validate with real market data

### 3. Performance Testing
- Load test with multiple requests
- Monitor response times
- Check memory usage

---

**Remember**: This system is for educational and research purposes. Always consult with financial advisors before making investment decisions.
