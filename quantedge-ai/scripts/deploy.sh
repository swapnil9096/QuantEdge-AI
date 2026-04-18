#!/usr/bin/env bash
# scripts/deploy.sh — build + deploy QuantEdge AI to Fly.io.
#
# Usage:
#   cd quantedge-ai
#   ./scripts/deploy.sh            # deploy
#   ./scripts/deploy.sh --logs     # follow logs after deploy
#   ./scripts/deploy.sh --open     # open the app URL in your browser
#
# Assumes:
#   1. You've installed flyctl (https://fly.io/docs/flyctl/install/) and run
#      `fly auth login`.
#   2. You've run `fly launch --copy-config --no-deploy` once, which set your
#      app name in fly.toml and created the `quantedge_data` volume.

set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v fly >/dev/null 2>&1; then
  echo "✗ flyctl not found. Install: curl -L https://fly.io/install.sh | sh" >&2
  exit 1
fi

APP_NAME=$(awk -F' *= *' '/^app *= *"/{gsub(/"/,"",$2); print $2; exit}' fly.toml 2>/dev/null || echo "")
if [ -z "$APP_NAME" ] || [ "$APP_NAME" = "quantedge-REPLACE-ME" ]; then
  cat <<EOF >&2
✗ fly.toml still has 'app = "quantedge-REPLACE-ME"'. Run this once to wire up
  the app name and volume, then re-run this script:

    fly launch --copy-config --no-deploy

  That command:
    - asks you for a unique app name (e.g. quantedge-$(whoami))
    - writes it back into fly.toml
    - creates the quantedge_data volume in the Mumbai region
EOF
  exit 1
fi

echo "==> Deploying $APP_NAME to Fly.io ..."
fly deploy --config fly.toml

echo ""
echo "==> Deploy complete. Status:"
fly status --config fly.toml || true

URL="https://${APP_NAME}.fly.dev"
echo ""
echo "==> Your app:"
echo "    $URL"
echo ""
echo "    /health        → should return 200"
echo "    /lock-status   → should return {\"configured\": true, \"unlocked\": false}"
echo "    /              → serves the React dashboard (lock screen until you unlock)"
echo ""

if [ "${1:-}" = "--logs" ]; then
  fly logs --config fly.toml
elif [ "${1:-}" = "--open" ]; then
  fly apps open --config fly.toml || open "$URL" 2>/dev/null || xdg-open "$URL" 2>/dev/null
fi
