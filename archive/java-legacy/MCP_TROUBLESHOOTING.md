# MCP Server Troubleshooting Guide

## Issues Fixed

### ✅ Fixed: Filesystem Server Configuration
**Problem**: Filesystem server was using environment variables incorrectly.

**Solution**: Changed to use command-line arguments:
```json
"filesystem": {
  "command": "npx",
  "args": [
    "-y",
    "@modelcontextprotocol/server-filesystem",
    "/Users/swapnilbobade1/Documents/Trading"
  ]
}
```

### ✅ Fixed: ROSBag Server Removed
**Problem**: `rosbag-mcp-server` doesn't exist in npm registry.

**Solution**: Removed from configuration. If you need ROSBag functionality, you'll need to install it manually from GitHub or use an alternative.

## Current Configuration Status

✅ **5 MCP Servers Configured:**
1. `mcp-trader` - @neural-trader/mcp (v2.1.0)
2. `nimble` - mcp-remote@latest (requires API key)
3. `filesystem` - @modelcontextprotocol/server-filesystem (v2025.8.21)
4. `notion` - notion-mcp-server (v1.0.1) (requires API key)
5. `github` - github-mcp-server (v1.8.7) (requires GitHub token)

## Common Issues and Solutions

### Issue 1: MCP Servers Not Loading in Cursor

**Symptoms:**
- MCP servers don't appear in Cursor
- No MCP tools available

**Solutions:**
1. **Restart Cursor completely** - Close and reopen the application
2. **Check JSON syntax** - Run: `python3 -m json.tool ~/.cursor/mcp.json`
3. **Verify file location** - Ensure config is at `~/.cursor/mcp.json`
4. **Check Cursor logs** - Look for MCP-related errors in Cursor's developer console

### Issue 2: "Command not found" Errors

**Symptoms:**
- Error: `spawn npx ENOENT`
- Server fails to start

**Solutions:**
1. **Verify Node.js/npm installed:**
   ```bash
   which node
   which npm
   which npx
   ```

2. **Test npx directly:**
   ```bash
   npx --version
   ```

3. **Install Node.js if missing:**
   - Visit: https://nodejs.org/
   - Install LTS version

### Issue 3: API Key Errors

**Symptoms:**
- Servers start but fail with authentication errors
- "Invalid API key" messages

**Solutions:**
1. **Update API keys in `~/.cursor/mcp.json`:**
   - Replace `YOUR_NIMBLE_API_KEY`
   - Replace `YOUR_NOTION_API_KEY` and `YOUR_NOTION_PAGE_ID`
   - Replace `YOUR_GITHUB_TOKEN`

2. **Verify API keys are valid:**
   - Test Nimble API key at: https://docs.nimbleway.com/
   - Test Notion API key at: https://www.notion.so/my-integrations
   - Test GitHub token at: https://github.com/settings/tokens

### Issue 4: Filesystem Server Permission Errors

**Symptoms:**
- "Permission denied" errors
- Cannot access files

**Solutions:**
1. **Verify directory exists:**
   ```bash
   ls -la /Users/swapnilbobade1/Documents/Trading
   ```

2. **Check permissions:**
   ```bash
   chmod -R 755 /Users/swapnilbobade1/Documents/Trading
   ```

3. **Update allowed directory in config if needed**

### Issue 5: Trading Functions Not Available

**Symptoms:**
- `get_price()`, `get_history()`, etc. not found
- MCP tools not showing up

**Solutions:**
1. **Verify trading MCP server is configured:**
   ```bash
   grep "mcp-trader" ~/.cursor/mcp.json
   ```

2. **Test trading server directly:**
   ```bash
   npx -y @neural-trader/mcp
   ```
   (Should show tool loading messages)

3. **Check if server requires additional setup:**
   - Some trading servers may need broker API keys
   - Check @neural-trader/mcp documentation

## Verification Steps

### Step 1: Verify Configuration File
```bash
python3 -m json.tool ~/.cursor/mcp.json
```

### Step 2: Test Each Server Package
```bash
# Test trading server
npx -y @neural-trader/mcp --help

# Test filesystem server
npx -y @modelcontextprotocol/server-filesystem /Users/swapnilbobade1/Documents/Trading

# Test GitHub server
npx -y github-mcp-server --help

# Test Notion server
npx -y notion-mcp-server --help
```

### Step 3: Use MCP Inspector
```bash
npx @modelcontextprotocol/inspector
```
Then connect to your servers and test them.

### Step 4: Check Cursor Logs
1. Open Cursor
2. Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
3. Type "Developer: Toggle Developer Tools"
4. Check Console for MCP-related errors

## Testing Trading Functions

Once MCP servers are loaded in Cursor, you should be able to use:

- `get_price(symbol, timeframe)` - Get current price
- `get_history(symbol, interval)` - Get historical data
- `calculate_indicator(symbol, indicator_name)` - Calculate technical indicators
- `detect_chart_pattern(symbol)` - Detect chart patterns
- `generate_trade_signal(symbol, strategy_name)` - Generate trade signals
- `backtest_strategy(strategy_name, symbol, timeframe)` - Backtest strategies

## Still Not Working?

If issues persist:

1. **Check Cursor version** - Ensure you're using a recent version that supports MCP
2. **Review official MCP docs** - https://modelcontextprotocol.io/
3. **Check server-specific documentation:**
   - Trading: Check @neural-trader/mcp npm page
   - Notion: https://github.com/awkoy/notion-mcp-server
   - GitHub: Check github-mcp-server npm page

4. **Minimal test configuration:**
   Try with just one server to isolate the issue:
   ```json
   {
     "mcpServers": {
       "filesystem": {
         "command": "npx",
         "args": [
           "-y",
           "@modelcontextprotocol/server-filesystem",
           "/Users/swapnilbobade1/Documents/Trading"
         ]
       }
     }
   }
   ```

## Current Configuration

Your MCP configuration is located at:
`/Users/swapnilbobade1/.cursor/mcp.json`

Run the test script to verify:
```bash
cd /Users/swapnilbobade1/Documents/Trading
./test_mcp_config.sh
```

---

**Last Updated**: After fixing filesystem server configuration and removing ROSBag server



