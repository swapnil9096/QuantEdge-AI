#!/usr/bin/env bash
# Start all services required for the Stock Scanner API: PostgreSQL, Redis, then the API.
# Run from project root: ./scripts/start_all.sh
# Uses Docker if available; otherwise tries Homebrew services. Then starts uvicorn.
set -e
cd "$(dirname "$0")/.."
PROJECT_ROOT="$(pwd)"
export PYTHONPATH="${PROJECT_ROOT}:${PYTHONPATH}"

echo "==> Stock Scanner API – starting all services"
echo ""

# 1) Start PostgreSQL and Redis
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "==> Using Docker for PostgreSQL and Redis"
  docker compose up -d postgres redis 2>/dev/null || docker-compose up -d postgres redis 2>/dev/null || true
  echo "    Waiting for PostgreSQL to be ready..."
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if python3 scripts/check_services.py >/dev/null 2>&1; then
      echo "    PostgreSQL and Redis are up."
      break
    fi
    sleep 2
  done
elif command -v brew >/dev/null 2>&1; then
  echo "==> Using Homebrew for PostgreSQL and Redis"
  brew services start postgresql@16 2>/dev/null || brew services start postgresql 2>/dev/null || true
  brew services start redis 2>/dev/null || true
  echo "    Waiting a few seconds for services..."
  sleep 5
else
  echo "==> Docker and Homebrew not found. Ensure PostgreSQL and Redis are already running."
  echo "    Then run: python3 scripts/check_services.py"
fi

# 2) Verify connectivity
echo ""
echo "==> Checking PostgreSQL and Redis..."
if ! python3 scripts/check_services.py; then
  echo ""
  echo "    PostgreSQL or Redis is not ready. Start them manually, then run this script again."
  echo "    Example: docker compose up -d postgres redis"
  exit 1
fi

# 3) Start the API
echo ""
echo "==> Starting API (uvicorn) on http://0.0.0.0:8000"
echo "    Health: http://localhost:8000/health"
echo "    Dashboard: http://localhost:8000/dashboard/"
echo "    Press Ctrl+C to stop."
echo ""
exec python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000
