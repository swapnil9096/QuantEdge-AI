# Stock Scanner API — one-command start + dev workflows

.PHONY: start start-api check deps deps-dev docker-up docker-full docker-down test migrate migration

# Start everything: dependencies (Postgres + Redis) then API
start:
	@./scripts/start_all.sh

# Start only the API (assumes Postgres + Redis already running)
start-api:
	@echo "Starting API on http://0.0.0.0:8000 ..."
	@python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# Verify Postgres and Redis connectivity
check:
	@python3 scripts/check_services.py

# Install runtime dependencies
deps:
	pip install -r requirements.txt

# Install runtime + test / migration dependencies
deps-dev:
	pip install -r requirements-dev.txt

# Run the test suite (offline, no DB required)
test:
	PYTHONPATH=. python3 -m pytest

# Apply database migrations to current DATABASE_URL
migrate:
	python3 -m alembic upgrade head

# Generate a new migration from the current ORM state: make migration m="describe change"
migration:
	@if [ -z "$(m)" ]; then echo 'Usage: make migration m="describe change"'; exit 1; fi
	python3 -m alembic revision --autogenerate -m "$(m)"

# Docker: start Postgres + Redis only (then run 'make start-api' in another terminal)
docker-up:
	docker compose up -d postgres redis

# Docker: start full stack (Postgres + Redis + API)
docker-full:
	docker compose up --build

# Docker: stop services
docker-down:
	docker compose down
