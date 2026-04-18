# Trading

This repository contains the **Stock Scanner API** — a FastAPI + Postgres + Redis
service that scans an NSE-centric stock universe, generates high-probability swing
trade signals, and trains/backtests ML models on top of engineered technical
features.

## Layout

```
.
├── stock_scanner_api/     # active Python application (FastAPI, SQLAlchemy, scikit-learn)
│   ├── app/               # API, scanner, indicators, providers, ML, backtesting
│   ├── alembic/           # database migrations (baseline + future revisions)
│   ├── tests/             # pytest suite (unit tests for engines, indicators, hashing)
│   ├── scripts/           # start_all.sh, check_services.py, train_models.py
│   ├── dashboard/         # static HTML dashboard served at /dashboard/
│   ├── Dockerfile, docker-compose.yml, Makefile, requirements.txt
│   └── .env.example
├── archive/java-legacy/   # retired Spring Boot app and associated docs (read-only)
├── SECRETS_ROTATION_CHECKLIST.md
├── .gitignore
└── README.md (this file)
```

The Java Spring Boot codebase that used to live at the root has been moved into
`archive/java-legacy/`. It is no longer built or deployed; keep it around only as
a reference. See `archive/java-legacy/README.md` for context.

## Quick start

```bash
cd stock_scanner_api
cp .env.example .env           # fill in real API keys locally; never commit
make start                     # brings up Postgres + Redis (docker) and uvicorn
```

Then:

- API: <http://localhost:8000>
- OpenAPI docs: <http://localhost:8000/docs>
- Dashboard: <http://localhost:8000/dashboard/>
- Health: <http://localhost:8000/health>

## Tests and migrations

```bash
cd stock_scanner_api
make test        # pytest
make migrate     # alembic upgrade head
make migration m="describe change"   # generate new revision
```

See `stock_scanner_api/README.md` for full API / strategy / ML documentation and
`SECRETS_ROTATION_CHECKLIST.md` for the one-time credential rotation task.
