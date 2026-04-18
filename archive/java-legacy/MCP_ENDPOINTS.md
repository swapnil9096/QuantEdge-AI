# MCP NSE Data Endpoints

## 🎯 Comprehensive Stock Data Endpoints

The following endpoints provide comprehensive stock data from NSE India using the MCP service.

---

## 📊 **Primary Endpoint: Comprehensive Data**

### **GET** `/api/nse/stock/{symbol}/comprehensive`

**Description**: Returns ALL available stock information from NSE India, organized into categories.

**URL**: `http://localhost:8080/api/nse/stock/{symbol}/comprehensive`

**Example**:
```bash
curl http://localhost:8080/api/nse/stock/RELIANCE/comprehensive
```

**Response Structure**:
```json
{
  "success": true,
  "message": "Comprehensive stock data retrieved successfully",
  "timestamp": "2024-01-15T10:30:00",
  "dataSource": "NSE Comprehensive Data (MCP)",
  "data": {
    "symbol": "RELIANCE",
    "companyName": "RELIANCE INDUSTRIES LTD",
    
    "priceInfo": {
      "lastPrice": 2450.50,
      "open": 2480.00,
      "dayHigh": 2520.00,
      "dayLow": 2470.00,
      "previousClose": 2480.00,
      "close": 2450.50,
      "change": -29.50,
      "changePercent": -1.19
    },
    
    "volumeInfo": {
      "totalTradedVolume": 1500000,
      "totalTradedValue": 3675000000.00,
      "marketCap": 1690000000000.00
    },
    
    "historicalInfo": {
      "high52Week": 2800.00,
      "low52Week": 2200.00,
      "yearHigh": 2800.00,
      "yearLow": 2200.00
    },
    
    "companyInfo": {
      "companyName": "RELIANCE INDUSTRIES LTD",
      "industry": "Refineries",
      "sector": "Oil & Gas",
      "isin": "INE002A01018",
      "series": "EQ",
      "faceValue": "1.00"
    },
    
    "financialMetrics": {
      "pe": 15.50,
      "pb": 2.10,
      "dividendYield": 0.45,
      "bookValue": 1166.67,
      "eps": 158.06
    },
    
    "marketDepth": {
      "bid": [
        {
          "price": 2450.00,
          "quantity": 1500,
          "orders": 5
        },
        {
          "price": 2449.50,
          "quantity": 2000,
          "orders": 8
        }
      ],
      "ask": [
        {
          "price": 2450.50,
          "quantity": 1200,
          "orders": 4
        },
        {
          "price": 2451.00,
          "quantity": 1800,
          "orders": 6
        }
      ]
    }
  }
}
```

---

## 🔧 **MCP-Specific Endpoint**

### **GET** `/api/nse/stock/{symbol}/mcp`

**Description**: Directly uses the MCP service to fetch comprehensive NSE data. Same structure as `/comprehensive` endpoint.

**URL**: `http://localhost:8080/api/nse/stock/{symbol}/mcp`

**Example**:
```bash
curl http://localhost:8080/api/nse/stock/TCS/mcp
```

**Response**: Same structure as `/comprehensive` endpoint above.

**Use Case**: Use this endpoint when you specifically want to ensure MCP service is used (not the fallback service).

---

## 📋 **Available Data Categories**

### 1. **Price Information** (`priceInfo`)
- `lastPrice` - Current/last traded price
- `open` - Opening price
- `dayHigh` - Day's highest price
- `dayLow` - Day's lowest price
- `previousClose` - Previous day's closing price
- `close` - Current closing price
- `change` - Price change amount
- `changePercent` - Price change percentage

### 2. **Volume & Trading Data** (`volumeInfo`)
- `totalTradedVolume` - Total volume traded
- `totalTradedValue` - Total value traded
- `marketCap` - Market capitalization

### 3. **Historical Data** (`historicalInfo`)
- `high52Week` - 52-week high price
- `low52Week` - 52-week low price
- `yearHigh` - Year's highest price
- `yearLow` - Year's lowest price

### 4. **Company Information** (`companyInfo`)
- `companyName` - Full company name
- `industry` - Industry classification
- `sector` - Sector classification
- `isin` - ISIN code
- `series` - Equity series (EQ, BE, etc.)
- `faceValue` - Face value per share

### 5. **Financial Metrics** (`financialMetrics`)
- `pe` - Price-to-Earnings ratio
- `pb` - Price-to-Book ratio
- `dividendYield` - Dividend yield percentage
- `bookValue` - Book value per share
- `eps` - Earnings Per Share

### 6. **Market Depth** (`marketDepth`)
- `bid` - Array of bid orders (price, quantity, orders)
- `ask` - Array of ask orders (price, quantity, orders)

---

## 🚀 **Quick Examples**

### Example 1: Get Comprehensive Data for RELIANCE
```bash
curl http://localhost:8080/api/nse/stock/RELIANCE/comprehensive
```

### Example 2: Get Comprehensive Data for TCS
```bash
curl http://localhost:8080/api/nse/stock/TCS/comprehensive
```

### Example 3: Get Comprehensive Data for INFY
```bash
curl http://localhost:8080/api/nse/stock/INFY/comprehensive
```

### Example 4: Using MCP Service Directly
```bash
curl http://localhost:8080/api/nse/stock/HDFCBANK/mcp
```

---

## 📝 **Using in JavaScript/Fetch**

```javascript
// Fetch comprehensive stock data
fetch('http://localhost:8080/api/nse/stock/RELIANCE/comprehensive')
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      console.log('Stock Symbol:', data.data.symbol);
      console.log('Current Price:', data.data.priceInfo.lastPrice);
      console.log('Change %:', data.data.priceInfo.changePercent);
      console.log('PE Ratio:', data.data.financialMetrics.pe);
      console.log('Market Cap:', data.data.volumeInfo.marketCap);
    }
  })
  .catch(error => console.error('Error:', error));
```

---

## 📝 **Using in Python**

```python
import requests

# Get comprehensive stock data
response = requests.get('http://localhost:8080/api/nse/stock/RELIANCE/comprehensive')
data = response.json()

if data['success']:
    stock_data = data['data']
    print(f"Symbol: {stock_data['symbol']}")
    print(f"Price: {stock_data['priceInfo']['lastPrice']}")
    print(f"Change: {stock_data['priceInfo']['changePercent']}%")
    print(f"PE Ratio: {stock_data['financialMetrics']['pe']}")
    print(f"Market Cap: {stock_data['volumeInfo']['marketCap']}")
```

---

## ⚠️ **Error Handling**

If the request fails, you'll receive:

```json
{
  "success": false,
  "error": "Error message here",
  "message": "Failed to retrieve comprehensive stock data for RELIANCE"
}
```

**Common HTTP Status Codes**:
- `200 OK` - Success
- `400 Bad Request` - Invalid symbol or API error
- `500 Internal Server Error` - Server error

---

## 🔄 **Endpoint Comparison**

| Endpoint | Uses MCP | Fallback | Use Case |
|----------|----------|----------|----------|
| `/api/nse/stock/{symbol}/comprehensive` | ✅ Yes (if enabled) | ✅ Yes | **Recommended** - Best of both worlds |
| `/api/nse/stock/{symbol}/mcp` | ✅ Yes | ❌ No | Use when you specifically need MCP |
| `/api/nse/stock/{symbol}` | ❌ No | ❌ No | Uses ComprehensiveNseDataService only |

---

## 📚 **Related Endpoints**

For other NSE data endpoints, see:
- `/api/nse/stock/{symbol}` - Basic stock data
- `/api/nse/stock/{symbol}/basic` - Basic price/volume info
- `/api/nse/stock/{symbol}/market-depth` - Bid/ask orders only
- `/api/nse/stock/{symbol}/company-info` - Company and financial info only

---

## ✅ **Status**

**Endpoint Status**: ✅ **Active and Ready**

The endpoints are configured and ready to use. Start your Spring Boot application and test with:

```bash
curl http://localhost:8080/api/nse/stock/RELIANCE/comprehensive
```

---

**Last Updated**: 2024-01-15

