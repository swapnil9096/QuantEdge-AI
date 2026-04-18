# MCP Server Setup Guide

This document provides information about the MCP (Model Context Protocol) servers configured for this project.

## System Information

- **Username**: swapnilbobade1
- **Home Directory**: /Users/swapnilbobade1
- **Project Path**: /Users/swapnilbobade1/Documents/Trading
- **MCP Config Location**: `/Users/swapnilbobade1/.cursor/mcp.json`

## Configured MCP Servers

### 1. Trading MCP Server (`mcp-trader`)
- **Package**: `@neural-trader/mcp`
- **Version**: 2.1.0
- **Description**: Model Context Protocol server for Neural Trader with 87+ trading tools
- **Status**: ✅ Configured
- **Functions Available**:
  - `get_price(symbol, timeframe)`
  - `get_history(symbol, interval)`
  - `calculate_indicator(symbol, indicator_name)`
  - `detect_chart_pattern(symbol)`
  - `generate_trade_signal(symbol, strategy_name)`
  - `place_order(broker, symbol, qty, side)` (optional)
  - `backtest_strategy(strategy_name, symbol, timeframe)`

### 2. Nimble MCP Server
- **Package**: `mcp-remote@latest`
- **Endpoint**: `https://mcp.nimbleway.com/sse`
- **Status**: ✅ Configured (requires API key)
- **Setup Required**:
  1. Register at [Nimble Signup](https://nimbleway.com/signup)
  2. Get API key from Account Settings → API KEYS
  3. Replace `YOUR_NIMBLE_API_KEY` in `mcp.json`

### 3. Filesystem MCP Server
- **Package**: `@modelcontextprotocol/server-filesystem`
- **Status**: ✅ Configured
- **Allowed Directory**: `/Users/swapnilbobade1/Documents/Trading`
- **Description**: Provides secure filesystem access for file operations

### 4. ROSBag MCP Server
- **Package**: `rosbag-mcp-server`
- **Status**: ⚠️ Not available in npm registry
- **Note**: This package may need manual installation from GitHub or may not be available yet. If you don't need ROSBag functionality, you can remove this entry from `mcp.json`.

### 5. Notion MCP Server
- **Package**: `notion-mcp-server`
- **Version**: 1.0.1
- **Status**: ✅ Configured (requires API key)
- **Setup Required**:
  1. Create integration at [Notion Developers](https://www.notion.so/my-integrations)
  2. Copy API key
  3. Get Page ID from your Notion page
  4. Replace `YOUR_NOTION_API_KEY` and `YOUR_NOTION_PAGE_ID` in `mcp.json`
  5. Enable integration on your Notion pages (Connections → Add connection)

### 6. GitHub MCP Server
- **Package**: `github-mcp-server`
- **Version**: 1.8.7
- **Status**: ✅ Configured (requires GitHub token)
- **Setup Required**:
  1. Create GitHub Personal Access Token at [GitHub Settings](https://github.com/settings/tokens)
  2. Grant necessary permissions (repo, read:org, etc.)
  3. Replace `YOUR_GITHUB_TOKEN` in `mcp.json`

## Configuration File

The MCP configuration is located at: `/Users/swapnilbobade1/.cursor/mcp.json`

### Current Configuration

```json
{
  "mcpServers": {
    "mcp-trader": {
      "command": "npx",
      "args": ["-y", "@neural-trader/mcp"]
    },
    "nimble": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote@latest",
        "https://mcp.nimbleway.com/sse",
        "--header",
        "Authorization:YOUR_NIMBLE_API_KEY"
      ]
    },
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem"],
      "env": {
        "ALLOWED_DIRECTORIES": "/Users/swapnilbobade1/Documents/Trading"
      }
    },
    "rosbag": {
      "command": "npx",
      "args": ["-y", "rosbag-mcp-server"]
    },
    "notion": {
      "command": "npx",
      "args": ["-y", "notion-mcp-server"],
      "env": {
        "NOTION_TOKEN": "YOUR_NOTION_API_KEY",
        "NOTION_PAGE_ID": "YOUR_NOTION_PAGE_ID"
      }
    },
    "github": {
      "command": "npx",
      "args": ["-y", "github-mcp-server"],
      "env": {
        "GITHUB_TOKEN": "YOUR_GITHUB_TOKEN"
      }
    }
  }
}
```

## Installation Verification

Run the verification script to check your setup:

```bash
cd /Users/swapnilbobade1/Documents/Trading
./verify_mcp_setup.sh
```

## Next Steps

1. **Update API Keys**: Edit `/Users/swapnilbobade1/.cursor/mcp.json` and replace all placeholder values:
   - `YOUR_NIMBLE_API_KEY` → Your Nimble API key
   - `YOUR_NOTION_API_KEY` → Your Notion API key
   - `YOUR_NOTION_PAGE_ID` → Your Notion page ID
   - `YOUR_GITHUB_TOKEN` → Your GitHub personal access token

2. **Restart Cursor**: After updating the configuration, restart Cursor to load the MCP servers.

3. **Test MCP Functions**: Once Cursor is restarted, you can test the trading functions:
   - `get_price(symbol, timeframe)`
   - `get_history(symbol, interval)`
   - `calculate_indicator(symbol, indicator_name)`
   - `detect_chart_pattern(symbol)`
   - `generate_trade_signal(symbol, strategy_name)`
   - `backtest_strategy(strategy_name, symbol, timeframe)`

## Troubleshooting

### MCP Servers Not Loading
- Ensure Node.js and npm are installed: `node --version` and `npm --version`
- Check that `npx` is available: `which npx`
- Verify JSON syntax: `python3 -m json.tool ~/.cursor/mcp.json`

### API Key Issues
- Verify API keys are correctly set in `mcp.json`
- Check that API keys have necessary permissions
- For Notion: Ensure integration is enabled on your pages

### Package Not Found Errors
- Most packages use `npx -y` which downloads them on-demand
- If a package fails, check npm registry: `npm view <package-name> version`
- ROSBag MCP Server may need manual installation or removal from config

## Integration with Trading Framework

The MCP servers are configured to work with your existing trading framework. The trading functions can be called through the MCP protocol, and they integrate with:

- `ComprehensiveNseDataService.java` - For NSE data
- Trading analysis services
- Chart pattern detection
- Technical indicators
- Backtesting capabilities

## References

- [Model Context Protocol Documentation](https://modelcontextprotocol.io/)
- [Nimble MCP Server Docs](https://docs.nimbleway.com/ai-agents/mcp-server)
- [Notion MCP Server GitHub](https://github.com/awkoy/notion-mcp-server)
- [GitHub MCP Server](https://www.npmjs.com/package/github-mcp-server)

---

**Last Updated**: $(date)
**Setup Status**: ✅ Configuration Complete (API keys need to be added)


