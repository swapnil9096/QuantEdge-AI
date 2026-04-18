#!/usr/bin/env bash
# Create PostgreSQL role and database for stock_scanner_api.
# Run from project root in a terminal where `psql` is on PATH (e.g. after brew install postgresql).
set -e
echo "Creating role 'postgres' and database 'stock_scanner'..."
psql -d postgres -v ON_ERROR_STOP=1 <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'postgres') THEN
    CREATE ROLE postgres WITH LOGIN PASSWORD 'postgres' SUPERUSER CREATEDB;
  END IF;
END
$$;
SQL
psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE stock_scanner OWNER postgres;" 2>/dev/null || echo "Database stock_scanner already exists."
echo "Done. Run: python3 scripts/check_services.py"
