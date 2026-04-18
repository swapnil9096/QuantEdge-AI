# Alembic migrations

This directory owns the database schema for `stock_scanner_api`.

## Commands

From `stock_scanner_api/`:

```bash
make migrate                      # alembic upgrade head
make migration m="add_column_x"   # autogenerate a new revision
alembic downgrade -1              # revert one revision
alembic history                   # list revisions
```

## How it reads configuration

`alembic/env.py` pulls `DATABASE_URL` from `app.core.config` (same `.env` the API
loads) and rewrites the async driver to a sync one (`asyncpg` → `psycopg2`,
`aiosqlite` → `sqlite`) because Alembic runs migrations synchronously.

## Runtime behaviour note

`app/main.py` used to call `Base.metadata.create_all` at startup as a one-shot
bootstrapper. With Alembic wired, we still call `create_all` **only in
development** (`APP_ENV=development`) so a fresh checkout can run without
migrations. In any other environment, apply migrations with `alembic upgrade
head` before starting the app.
