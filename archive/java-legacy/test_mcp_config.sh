#!/bin/bash
echo "Testing MCP Configuration..."
echo ""

# Test JSON validity
echo "1. Testing JSON validity..."
python3 -c "import json; f=open('$HOME/.cursor/mcp.json'); json.load(f); print('   ✓ JSON is valid')" || echo "   ✗ JSON is invalid"

# Test each server package exists
echo ""
echo "2. Testing server packages..."
for server in "@neural-trader/mcp" "mcp-remote@latest" "@modelcontextprotocol/server-filesystem" "notion-mcp-server" "github-mcp-server"; do
    if npm view "$server" version > /dev/null 2>&1; then
        version=$(npm view "$server" version 2>&1 | head -1)
        echo "   ✓ $server ($version)"
    else
        echo "   ✗ $server (not found)"
    fi
done

echo ""
echo "3. Configuration summary:"
python3 << 'PYTHON'
import json
with open('/Users/swapnilbobade1/.cursor/mcp.json') as f:
    config = json.load(f)
    servers = config.get('mcpServers', {})
    print(f"   Total servers configured: {len(servers)}")
    for name in servers:
        print(f"   - {name}")
PYTHON

echo ""
echo "✓ Configuration test complete"
