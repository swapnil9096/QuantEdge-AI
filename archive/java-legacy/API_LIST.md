# 📋 Complete API List - Trading Backend

**Base URL**: `http://localhost:8080`

---

## 🎯 **1. Simple API Endpoints** (`/api/simple`)

### Health & Info
- **GET** `/api/simple/health`
  - Health check endpoint
  - Returns: `{status, message, version}`

- **GET** `/api/simple/demo`
  - Demo information and available endpoints
  - Returns: `{message, endpoints[], sampleSymbols[]}`

- **GET** `/api/simple/valid-symbols`
  - Get all valid stock symbols
  - Returns: `{validSymbols[], totalCount, message}`

### Stock Analysis
- **GET** `/api/simple/analyze/{symbol}`
  - Comprehensive real-time stock analysis
  - Returns: Complete analysis with technical, fundamental, multibagger, risk, and trading signal
  - Example: `/api/simple/analyze/RELIANCE`

- **GET** `/api/simple/multibagger-check/{symbol}`
  - Check if stock has multibagger potential
  - Returns: `{symbol, name, sector, cap, currentPrice, isMultibaggerCandidate, multibaggerScore, growthPotential, growthPhase, message}`
  - Example: `/api/simple/multibagger-check/TCS`

- **GET** `/api/simple/swing-trading-check/{symbol}`
  - Check if stock is suitable for swing trading
  - Returns: `{symbol, name, sector, cap, currentPrice, change, changePercent, isSwingTradingCandidate, expectedReturn, signalType, strength, overallScore, confidence, message}`
  - Example: `/api/simple/swing-trading-check/INFY`

---

## 📊 **2. Real-Time NSE Data** (`/api/realtime-nse`)

- **GET** `/api/realtime-nse/stock/{symbol}`
  - Get complete real-time stock data
  - Returns: Full market data object
  - Example: `/api/realtime-nse/stock/RELIANCE`

- **GET** `/api/realtime-nse/stock/{symbol}/price`
  - Get real-time price only
  - Returns: `{symbol, currentPrice, change, changePercent, timestamp, dataSource}`
  - Example: `/api/realtime-nse/stock/RELIANCE/price`

- **GET** `/api/realtime-nse/stock/{symbol}/summary`
  - Get real-time price summary
  - Returns: `{symbol, currentPrice, open, high, low, previousClose, change, changePercent, volume, timestamp, dataSource}`
  - Example: `/api/realtime-nse/stock/RELIANCE/summary`

---

## 🔴 **3. Live Market Data** (`/api/live`)

- **GET** `/api/live/quote/{symbol}`
  - Get live market quote
  - Returns: Live market data object
  - Example: `/api/live/quote/RELIANCE`

- **GET** `/api/live/test/{symbol}`
  - Test live data fetching
  - Returns: `{symbol, currentPrice, change, changePercent, dataSource, timestamp, status, message}`
  - Example: `/api/live/test/RELIANCE`

---

## 📈 **4. NSE Stock Data** (`/api/nse`)

- **GET** `/api/nse/stock/{symbol}`
  - Get comprehensive NSE stock data
  - Returns: Complete NseStockData object
  - Example: `/api/nse/stock/RELIANCE`

- **GET** `/api/nse/stock/{symbol}/basic`
  - Get basic stock information
  - Returns: Basic stock data (price, volume, change, etc.)
  - Example: `/api/nse/stock/RELIANCE/basic`

- **GET** `/api/nse/stock/{symbol}/market-depth`
  - Get market depth (bid/ask data)
  - Returns: `{symbol, bid[], ask[], timestamp}`
  - Example: `/api/nse/stock/RELIANCE/market-depth`

- **GET** `/api/nse/stock/{symbol}/company-info`
  - Get company information
  - Returns: `{symbol, companyName, industry, sector, isin, series, faceValue, marketCap, pe, pb, dividendYield, bookValue, eps, timestamp}`
  - Example: `/api/nse/stock/RELIANCE/company-info`

---

## 🎭 **5. NSE Demo Data** (`/api/nse-demo`)

These endpoints return sample/demo data showing the structure of NSE API responses:

- **GET** `/api/nse-demo/stock/{symbol}`
  - Get demo comprehensive stock data
  - Returns: Sample NseStockData object
  - Example: `/api/nse-demo/stock/RELIANCE`

- **GET** `/api/nse-demo/stock/{symbol}/basic`
  - Get demo basic stock data
  - Example: `/api/nse-demo/stock/RELIANCE/basic`

- **GET** `/api/nse-demo/stock/{symbol}/market-depth`
  - Get demo market depth
  - Example: `/api/nse-demo/stock/RELIANCE/market-depth`

- **GET** `/api/nse-demo/stock/{symbol}/company-info`
  - Get demo company information
  - Example: `/api/nse-demo/stock/RELIANCE/company-info`

---

## 🔄 **6. Real-Time Test** (`/api/realtime`)

- **GET** `/api/realtime/test/{symbol}`
  - Test real-time data service
  - Returns: Live market data object
  - Example: `/api/realtime/test/RELIANCE`

---

## 📡 **7. Stream Data** (`/api/stream`)

- **GET** `/api/stream/latest/{symbol}`
  - Get latest quote from stream service
  - Returns: Real-time stock data
  - Example: `/api/stream/latest/RELIANCE`

---

## 🔌 **8. WebSocket Endpoints**

### WebSocket Connection
- **WebSocket URL**: `ws://localhost:8080/ws`
- **SockJS URL**: `http://localhost:8080/ws` (with SockJS)

### Message Topics
- **Subscribe to**: `/topic/*` (for receiving messages)
- **Send to**: `/app/*` (for sending messages)

### Available Controllers (via WebSocket)
Based on the application logs, these controllers are configured for WebSocket:
- `LiveDataController`
- `NseDataController`
- `NseDemoController`
- `RealTimeNseController`
- `RealtimeTestController`
- `SimpleController`
- `StreamController`

---

## 📝 **Valid Stock Symbols**

Common valid symbols you can use for testing:
- `RELIANCE` - Reliance Industries Ltd
- `TCS` - Tata Consultancy Services Ltd
- `INFY` - Infosys Ltd
- `HDFCBANK` - HDFC Bank Ltd
- `ICICIBANK` - ICICI Bank Ltd
- `WIPRO` - Wipro Ltd
- `BHARTIARTL` - Bharti Airtel Ltd
- `ITC` - ITC Ltd
- `SBIN` - State Bank of India
- `KOTAKBANK` - Kotak Mahindra Bank Ltd
- `IRCTC` - Indian Railway Catering and Tourism Corporation Ltd
- `IRFC` - Indian Railway Finance Corporation Ltd

To get the complete list, call: `GET /api/simple/valid-symbols`

---

## 🚀 **Quick Test Examples**

```bash
# Health check
curl http://localhost:8080/api/simple/health

# Get demo info
curl http://localhost:8080/api/simple/demo

# Analyze a stock
curl http://localhost:8080/api/simple/analyze/RELIANCE

# Check multibagger potential
curl http://localhost:8080/api/simple/multibagger-check/TCS

# Check swing trading suitability
curl http://localhost:8080/api/simple/swing-trading-check/INFY

# Get real-time price
curl http://localhost:8080/api/realtime-nse/stock/RELIANCE/price

# Get live quote
curl http://localhost:8080/api/live/quote/RELIANCE

# Get NSE stock data
curl http://localhost:8080/api/nse/stock/RELIANCE

# Get basic stock data
curl http://localhost:8080/api/nse/stock/RELIANCE/basic

# Get market depth
curl http://localhost:8080/api/nse/stock/RELIANCE/market-depth

# Get company info
curl http://localhost:8080/api/nse/stock/RELIANCE/company-info

# Get latest stream data
curl http://localhost:8080/api/stream/latest/RELIANCE
```

---

## 📊 **Response Format**

All endpoints return JSON responses with the following structure:

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "message": "Success message"
}
```

### Error Response
```json
{
  "success": false,
  "error": "Error message",
  "message": "Detailed error description"
}
```

### Analysis Response (from `/api/simple/analyze/{symbol}`)
```json
{
  "symbol": "RELIANCE",
  "name": "Reliance Industries Ltd",
  "currentPrice": 2500.00,
  "overallScore": 85.5,
  "confidence": 92.3,
  "recommendation": "STRONG_BUY",
  "technicalAnalysis": { ... },
  "fundamentalAnalysis": { ... },
  "multibaggerAnalysis": { ... },
  "riskAssessment": { ... },
  "tradingSignal": { ... }
}
```

---

## 🔐 **CORS Configuration**

All endpoints support CORS with `CrossOrigin(origins = "*")`, allowing requests from any origin.

---

## 📌 **Notes**

1. **Base URL**: All endpoints are prefixed with `/api/` except WebSocket endpoints
2. **Symbol Format**: Use uppercase stock symbols (e.g., `RELIANCE`, not `reliance`)
3. **Real-time Data**: Some endpoints fetch live data from NSE/Yahoo Finance APIs
4. **Demo Data**: `/api/nse-demo/*` endpoints return sample data for testing
5. **Error Handling**: All endpoints include proper error handling and validation

---

**Last Updated**: Based on Spring Boot application running on port 8080

