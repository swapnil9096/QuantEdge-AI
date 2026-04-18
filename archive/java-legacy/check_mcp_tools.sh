#!/bin/bash

echo "=========================================="
echo "MCP Tools Availability Check"
echo "=========================================="
echo ""

echo "1. Checking if MCP servers are running..."
ps aux | grep -E "mcp|npx.*@neural-trader|npx.*filesystem" | grep -v grep || echo "   No MCP processes found (this is normal if Cursor hasn't loaded them yet)"

echo ""
echo "2. Testing if servers can be started and expose tools..."
echo ""

# Test trading server
echo "Testing @neural-trader/mcp..."
timeout 3 npx -y @neural-trader/mcp 2>&1 | grep -i "loaded tool" | head -5 || echo "   Could not verify tools (server may need full initialization)"

echo ""
echo "3. Configuration check..."
python3 << 'PYTHON'
import json
import os

config_path = os.path.expanduser("~/.cursor/mcp.json")
with open(config_path) as f:
    config = json.load(f)

servers = config.get('mcpServers', {})
print(f"✓ Found {len(servers)} configured servers:")
for name, server_config in servers.items():
    print(f"  - {name}")
    if 'env' in server_config:
        env_vars = list(server_config['env'].keys())
        has_placeholders = any('YOUR_' in str(v) for v in server_config['env'].values())
        if has_placeholders:
            print(f"    ⚠️  Has placeholder API keys that need to be replaced")
PYTHON

echo ""
echo "4. Recommendations:"
echo "   - Make sure Cursor is completely restarted"
echo "   - Check Cursor's Developer Console (Cmd+Shift+P → 'Developer: Toggle Developer Tools')"
echo "   - Look for MCP-related errors in the console"
echo "   - Verify API keys are set (if required)"
echo ""
echo "5. To see available tools in Cursor:"
echo "   - Open Command Palette (Cmd+Shift+P)"
echo "   - Type 'MCP' to see MCP-related commands"
echo "   - Or check the MCP panel if available"
echo ""
echo "=========================================="

