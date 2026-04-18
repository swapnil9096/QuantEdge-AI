#!/usr/bin/env bash
# scripts/deploy-koyeb.sh — push to GitHub and trigger a Koyeb deploy.
#
# Usage:
#   cd quantedge-ai
#   ./scripts/deploy-koyeb.sh                # default: commit any changes + push
#   ./scripts/deploy-koyeb.sh "commit msg"   # custom commit message
#   ./scripts/deploy-koyeb.sh --logs         # follow logs after push
#
# Assumptions:
#   1. You've run `git init`, `git remote add origin git@github.com:<you>/quantedge-ai.git`
#      and the `main` branch is configured for pushes.
#   2. You've created a Koyeb service connected to this GitHub repo — Koyeb will
#      auto-rebuild on every push.
#
# If you want to trigger a deploy directly via the Koyeb CLI (no git push),
# use:  koyeb service redeploy quantedge/quantedge

set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"

if [ -z "$REPO_ROOT" ]; then
  cat <<EOF >&2
✗ This isn't inside a git repository yet.

  Run these first (one-time):
    cd quantedge-ai
    git init
    git branch -M main
    git add .
    git commit -m "Initial QuantEdge AI commit"

  Then create a PRIVATE repo on github.com called quantedge-ai, and:
    git remote add origin git@github.com:<your-user>/quantedge-ai.git
    git push -u origin main

  Then re-run this script to deploy new changes.
EOF
  exit 1
fi

# Safety: refuse to push if the working tree has the plaintext .env (shouldn't
# ever happen because .env is gitignored, but belt-and-braces).
if git ls-files --error-unmatch backend/.env >/dev/null 2>&1; then
  echo "✗ REFUSING TO PUSH: quantedge-ai/backend/.env is tracked by git." >&2
  echo "   Run: git rm --cached backend/.env   (it stays on disk, just removes from git)" >&2
  exit 1
fi

msg="${1:-deploy: $(date +'%Y-%m-%d %H:%M')}"
if [ "$msg" = "--logs" ]; then
  msg="deploy: $(date +'%Y-%m-%d %H:%M')"
fi

CHANGED=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$CHANGED" -gt 0 ]; then
  echo "==> Staging + committing $CHANGED change(s) …"
  git add .
  git commit -m "$msg" || true
else
  echo "==> No local changes. Pushing existing HEAD."
fi

BRANCH=$(git branch --show-current)
echo "==> Pushing $BRANCH to origin …"
git push origin "$BRANCH"

echo ""
echo "==> Koyeb will detect the push and start a new build."
echo "    Watch progress: https://app.koyeb.com/services"
echo ""

if [ "${1:-}" = "--logs" ] || [ "${2:-}" = "--logs" ]; then
  if command -v koyeb >/dev/null 2>&1; then
    koyeb service logs quantedge/quantedge --since 1m
  else
    echo "(Install koyeb CLI to tail logs here: curl -fsSL https://raw.githubusercontent.com/koyeb/koyeb-cli/master/install.sh | sh)"
  fi
fi
