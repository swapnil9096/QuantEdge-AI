# How to Access and Use MCP Tools in Cursor

## ✅ Good News: Your MCP Servers ARE Running!

I can see that Cursor has started your MCP servers:
- ✅ Filesystem MCP Server (running)
- ✅ Notion MCP Server (running)  
- ✅ Nimble MCP Server (running)
- ✅ Trading MCP Server (configured)
- ✅ GitHub MCP Server (configured)

## How to Access MCP Tools in Cursor

### Method 1: Through the Chat/Composer Interface

MCP tools are automatically available when you use Cursor's AI chat. Simply:

1. **Open Cursor's Composer** (Cmd+I or Ctrl+I)
2. **Ask for a tool by name** - For example:
   - "Use the filesystem tool to read a file"
   - "Get the price of RELIANCE using the trading tools"
   - "List files in the current directory"

3. **The AI will automatically use the MCP tools** - Cursor's AI can see and use all available MCP tools

### Method 2: Check Available Tools via Command Palette

1. Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
2. Type "MCP" to see MCP-related commands
3. Look for options like:
   - "MCP: List Tools"
   - "MCP: Show Server Status"
   - "MCP: Refresh"

### Method 3: Check Developer Console

1. Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
2. Type "Developer: Toggle Developer Tools"
3. Go to the Console tab
4. Look for MCP-related messages
5. You can also check the Network tab for MCP communication

### Method 4: Direct Tool Invocation

In Cursor's chat, you can directly request tool usage:

```
@mcp-trader get_price RELIANCE 1d
```

or

```
Use the filesystem read_file tool to read src/main/java/com/trading/service/ComprehensiveNseDataService.java
```

## Available Tools from Your Servers

### From Trading MCP Server (@neural-trader/mcp):
- `execute_trade` - Execute trades
- `get_mark_price` - Get current market price (similar to get_price)
- `get_klines` - Get historical candlestick data (similar to get_history)
- `correlation_analysis` - Analyze correlations
- `analyze_market_sentiment_tool` - Analyze market sentiment
- `adaptive_strategy_selection` - Select trading strategies
- And 80+ more trading tools!

### From Filesystem MCP Server:
- `read_file` / `read_text_file` - Read file contents
- `write_file` - Write to files
- `list_directory` - List directory contents
- `search_files` - Search for files
- `get_file_info` - Get file metadata

### From Notion MCP Server:
- `notion_pages` - Create/search/update Notion pages
- `notion_blocks` - Work with Notion blocks
- `notion_database` - Query Notion databases

### From GitHub MCP Server:
- `github_*` - Various GitHub operations

## Testing if Tools Work

### Test 1: Filesystem Tool
In Cursor's chat, try:
```
Read the file src/main/java/com/trading/service/ComprehensiveNseDataService.java using the filesystem tool
```

### Test 2: Trading Tool
In Cursor's chat, try:
```
Use the trading MCP server to get the current price of RELIANCE
```

### Test 3: List Available Tools
In Cursor's chat, try:
```
What MCP tools are currently available?
```

## Troubleshooting: Tools Still Not Showing?

### 1. Verify Servers Are Running
Run this command in terminal:
```bash
ps aux | grep -E "mcp|npx.*@neural-trader|npx.*filesystem" | grep -v grep
```

You should see processes for your MCP servers.

### 2. Check for Errors
- Open Developer Console (Cmd+Shift+P → "Developer: Toggle Developer Tools")
- Look for red error messages related to MCP
- Check if any servers failed to start

### 3. Restart Cursor Completely
- Quit Cursor completely (Cmd+Q on Mac)
- Reopen Cursor
- Wait a few seconds for MCP servers to initialize

### 4. Check API Keys
Some servers need valid API keys:
- **Nimble**: Replace `YOUR_NIMBLE_API_KEY` in `~/.cursor/mcp.json`
- **Notion**: Replace `YOUR_NOTION_API_KEY` and `YOUR_NOTION_PAGE_ID`
- **GitHub**: Replace `YOUR_GITHUB_TOKEN`

Servers with placeholder keys may not work properly.

### 5. Test Individual Servers
Test if servers work outside Cursor:
```bash
# Test filesystem server
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | npx -y @modelcontextprotocol/server-filesystem /Users/swapnilbobade1/Documents/Trading

# Test trading server  
npx -y @neural-trader/mcp
# (Press Ctrl+C after seeing tool loading messages)
```

## Important Notes

1. **MCP tools are NOT visible as buttons** - They're available through Cursor's AI chat interface
2. **The AI automatically uses them** - When you ask for something that requires a tool, Cursor uses it automatically
3. **Tools work behind the scenes** - You don't need to manually invoke them; just ask naturally

## Example Usage

Instead of thinking "I need to use the get_price tool", just ask:

❌ **Don't do this:**
```
Use get_price tool with symbol RELIANCE and timeframe 1d
```

✅ **Do this:**
```
What's the current price of RELIANCE stock?
```

Cursor's AI will automatically use the appropriate MCP tool to get the price.

## Still Having Issues?

1. Check Cursor version - Make sure you're using a recent version
2. Review MCP logs - Check Developer Console for errors
3. Try minimal config - Test with just the filesystem server
4. Check Cursor documentation - Look for MCP-specific settings

---

**Remember**: MCP tools are integrated into Cursor's AI - you don't need to call them directly. Just ask naturally and Cursor will use the appropriate tools!

