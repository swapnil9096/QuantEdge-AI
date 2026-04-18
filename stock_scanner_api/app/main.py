import warnings
from contextlib import asynccontextmanager

# Suppress noisy LibreSSL warning on macOS system Python before other imports.
warnings.filterwarnings("ignore", message=".*urllib3 v2 only supports OpenSSL 1.1.1\\+.*")

from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.api.routes import router
from app.core.config import get_settings
from app.core.logging_config import configure_logging
from app.db.base import Base
from app.db.session import engine
from app.workers.scheduler import start_scheduler, stop_scheduler


settings = get_settings()
configure_logging(settings.log_level)


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Schema is owned by Alembic. Only auto-create tables in development so
    # a fresh checkout can run without applying migrations.
    if settings.app_env.lower() in {"dev", "development", "test", "testing"}:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
    start_scheduler()
    yield
    stop_scheduler()
    await engine.dispose()


app = FastAPI(title="High Probability Stock Scanner API", version="1.0.0", lifespan=lifespan)
app.include_router(router, prefix=settings.api_prefix, tags=["scanner"])

_dashboard_dir = Path(__file__).resolve().parent.parent / "dashboard"
if _dashboard_dir.exists():
    app.mount("/dashboard", StaticFiles(directory=str(_dashboard_dir), html=True), name="dashboard")


@app.get("/health")
async def health() -> dict:
    return {"status": "UP", "app": settings.app_name, "env": settings.app_env}
