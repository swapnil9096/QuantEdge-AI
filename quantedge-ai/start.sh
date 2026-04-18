#!/usr/bin/env bash
# QuantEdge AI — one-command launcher for Mac/Linux.
# Brings up the FastAPI backend on :8000 and the Vite dev server on :3000.

set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

cleanup() {
  echo ""
  echo "==> Shutting down QuantEdge AI"
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
  exit 0
}
trap cleanup INT TERM

echo "==> QuantEdge AI launcher"
echo "    backend  → http://localhost:8000"
echo "    frontend → http://localhost:3000"
echo ""

# --- Backend ---
cd "$ROOT/backend"

if [[ ! -d .venv ]]; then
  echo "==> Creating Python virtualenv (.venv)"
  PY_BIN="python3"
  if command -v python3.12 >/dev/null 2>&1; then PY_BIN="python3.12"; fi
  "$PY_BIN" -m venv .venv
fi

# shellcheck source=/dev/null
source .venv/bin/activate

pip install --quiet --upgrade pip
pip install --quiet -r requirements.txt

if [[ ! -f .env ]]; then
  echo "==> Creating backend/.env from .env.example (fill in your keys!)"
  cp .env.example .env
fi

echo "==> Starting FastAPI on :8000"
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload &
BACKEND_PID=$!

# --- Frontend ---
cd "$ROOT/frontend"

if [[ ! -d node_modules ]]; then
  echo "==> Installing frontend dependencies (npm install)"
  npm install --silent
fi

echo "==> Starting Vite dev server on :3000"
npm run dev &
FRONTEND_PID=$!

wait $BACKEND_PID $FRONTEND_PID
