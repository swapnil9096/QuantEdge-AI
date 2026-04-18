#!/bin/bash

# MCP Server Setup Verification Script
# This script verifies that all MCP servers are properly configured and accessible

echo "=========================================="
echo "MCP Server Setup Verification"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if MCP config file exists
MCP_CONFIG="$HOME/.cursor/mcp.json"
echo -n "Checking MCP configuration file... "
if [ -f "$MCP_CONFIG" ]; then
    echo -e "${GREEN}✓ Found${NC}"
    echo "  Location: $MCP_CONFIG"
    
    # Validate JSON
    if python3 -m json.tool "$MCP_CONFIG" > /dev/null 2>&1; then
        echo -e "  JSON validation: ${GREEN}✓ Valid${NC}"
    else
        echo -e "  JSON validation: ${RED}✗ Invalid${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ Not found${NC}"
    exit 1
fi

echo ""
echo "Testing MCP Server Packages:"
echo "----------------------------------------"

# Test each package
test_package() {
    local name=$1
    local package=$2
    echo -n "Testing $name ($package)... "
    
    if timeout 10 npx -y "$package" --version > /dev/null 2>&1 || timeout 10 npx -y "$package" --help > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Available${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ May require setup${NC}"
        return 1
    fi
}

# Test packages
test_package "Trading MCP" "@neural-trader/mcp"
test_package "Nimble MCP" "mcp-remote@latest"
test_package "Filesystem MCP" "@modelcontextprotocol/server-filesystem"
test_package "Notion MCP" "notion-mcp-server"
test_package "GitHub MCP" "github-mcp-server"

echo ""
echo "ROSBag MCP Server:"
echo -n "  Checking rosbag-mcp-server... "
if npm view rosbag-mcp-server version > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Available${NC}"
else
    echo -e "${YELLOW}⚠ Not found in npm registry${NC}"
    echo "  Note: ROSBag MCP Server may need manual installation from GitHub"
fi

echo ""
echo "=========================================="
echo "Configuration Summary:"
echo "=========================================="
echo ""
echo "MCP Config Location: $MCP_CONFIG"
echo ""
echo "Required API Keys (update in $MCP_CONFIG):"
echo "  - Nimble API Key: YOUR_NIMBLE_API_KEY"
echo "  - Notion API Key: YOUR_NOTION_API_KEY"
echo "  - Notion Page ID: YOUR_NOTION_PAGE_ID"
echo "  - GitHub Token: YOUR_GITHUB_TOKEN"
echo ""
echo "Next Steps:"
echo "  1. Update API keys in $MCP_CONFIG"
echo "  2. Restart Cursor to load MCP servers"
echo "  3. Test MCP functions in your application"
echo ""
echo "=========================================="

