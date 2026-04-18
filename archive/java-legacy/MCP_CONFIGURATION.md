# MCP Server Configuration Guide

This guide explains how to configure the MCP (Model Context Protocol) server to fetch live market data from your custom website.

## Overview

The MCP Data Service allows you to connect to any custom website/API that provides live market data. It's configurable through the `application.yml` file and integrates seamlessly with the existing live data services.

## Configuration Steps

### 1. Enable MCP Service

Edit `src/main/resources/application.yml` and set:

```yaml
api:
  mcp:
    enabled: true  # Enable the MCP data source
```

### 2. Configure Base URL

Set the base URL of your MCP server:

```yaml
api:
  mcp:
    base-url: "https://api.example.com/stocks"  # Your MCP server URL
```

### 3. Configure Endpoint Pattern

Specify how the symbol should be included in the URL:

```yaml
api:
  mcp:
    endpoint-pattern: "{symbol}"  # Will be replaced with actual symbol
```

**Examples:**
- `{symbol}` → `https://api.example.com/stocks/RELIANCE`
- `/quote/{symbol}` → `https://api.example.com/stocks/quote/RELIANCE`
- `?symbol={symbol}` → `https://api.example.com/stocks?symbol=RELIANCE`

### 4. Configure API Key (Optional)

If your API requires authentication:

```yaml
api:
  mcp:
    api-key: "your-api-key-here"
    api-key-header: "X-API-Key"  # Header name (e.g., "Authorization", "X-API-Key")
```

### 5. Configure JSON Paths

Map the JSON paths in your API response to the expected data fields:

```yaml
api:
  mcp:
    json-path:
      current-price: "price"  # Path to current price in JSON
      open: "open"
      high: "high"
      low: "low"
      previous-close: "previousClose"
      volume: "volume"
      symbol: "symbol"
```

**Supported JSON Path Formats:**
- Simple: `"price"` → `{ "price": 2500 }`
- Nested: `"data.price"` → `{ "data": { "price": 2500 } }`
- Array: `"result.0.price"` → `{ "result": [{ "price": 2500 }] }`

## Complete Example Configuration

```yaml
api:
  mcp:
    enabled: true
    base-url: "https://api.example.com/stocks"
    endpoint-pattern: "/quote/{symbol}"
    api-key: "your-secret-key"
    api-key-header: "Authorization"
    timeout: 5000
    json-path:
      current-price: "data.price"
      open: "data.open"
      high: "data.high"
      low: "data.low"
      previous-close: "data.previousClose"
      volume: "data.volume"
      symbol: "data.symbol"
```

## Expected API Response Format

Your MCP server should return JSON in one of these formats:

### Format 1: Flat Structure
```json
{
  "symbol": "RELIANCE",
  "price": 2500.00,
  "open": 2480.00,
  "high": 2520.00,
  "low": 2470.00,
  "previousClose": 2480.00,
  "volume": 1500000
}
```

### Format 2: Nested Structure
```json
{
  "data": {
    "symbol": "RELIANCE",
    "price": 2500.00,
    "open": 2480.00,
    "high": 2520.00,
    "low": 2470.00,
    "previousClose": 2480.00,
    "volume": 1500000
  }
}
```

### Format 3: Array Response
```json
{
  "result": [{
    "symbol": "RELIANCE",
    "price": 2500.00,
    "open": 2480.00,
    "high": 2520.00,
    "low": 2470.00,
    "previousClose": 2480.00,
    "volume": 1500000
  }]
}
```

## Integration with Existing Services

The MCP service is integrated into the `LiveMarketDataService` and will be tried in this order:

1. NSE API (if enabled)
2. **MCP Server** (if enabled) ← **Your custom source**
3. Robust Market Data Service
4. Yahoo Finance
5. Finnhub API
6. Alpha Vantage
7. Web Scraping

## Testing Your Configuration

1. Start the application:
   ```bash
   mvn spring-boot:run
   ```

2. The MCP service will automatically be used when fetching live data for any symbol.

3. Check the logs for MCP server connection status:
   ```
   Trying MCP server for RELIANCE
   MCP server success for RELIANCE, data source: MCP Server: https://api.example.com/stocks
   ```

## Troubleshooting

### Error: "MCP base URL is not configured"
- **Solution**: Set `api.mcp.base-url` in `application.yml`

### Error: "Could not extract current price from MCP response"
- **Solution**: Check your `json-path.current-price` configuration matches your API response structure

### Error: "MCP API returned status: 401"
- **Solution**: Verify your API key is correct and the `api-key-header` matches your server's expected header name

### Error: "MCP API returned status: 404"
- **Solution**: Check your `base-url` and `endpoint-pattern` configuration

## Advanced Configuration

### Custom Headers

You can add custom headers by modifying the `McpDataService.java` file's `createHeaders()` method.

### Request Timeout

Adjust the timeout for slow APIs:

```yaml
api:
  mcp:
    timeout: 10000  # 10 seconds
```

### URL Pattern Examples

- Path parameter: `base-url: "https://api.example.com"` + `endpoint-pattern: "/stocks/{symbol}"`
- Query parameter: `base-url: "https://api.example.com/stocks"` + `endpoint-pattern: "?symbol={symbol}"`
- Full URL: `base-url: "https://api.example.com/stocks/{symbol}"` + `endpoint-pattern: ""`

## Support

For issues or questions:
1. Check the application logs for detailed error messages
2. Verify your API response format matches the JSON path configuration
3. Test your API endpoint directly with a tool like `curl` or Postman

