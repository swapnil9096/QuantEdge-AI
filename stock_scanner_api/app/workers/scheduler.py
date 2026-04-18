import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from redis.exceptions import RedisError

from app.cache.redis_client import redis_client
from app.core.config import get_settings
from app.db.session import AsyncSessionLocal
from app.services.scanner_service import ScannerService


logger = logging.getLogger(__name__)

settings = get_settings()
scanner_service = ScannerService()
scheduler = AsyncIOScheduler()


async def _run_scheduled_scan() -> None:
    # Distributed lock to avoid overlapping scans in scaled deployments.
    lock_key = "lock:market_scan"
    try:
        locked = await redis_client.set(lock_key, "1", ex=240, nx=True)
    except RedisError:
        locked = True
    if not locked:
        return

    async with AsyncSessionLocal() as db:
        opportunities = await scanner_service.scan_market(db)
        logger.info("Scheduled scan completed. Opportunities found: %s", len(opportunities))


def start_scheduler() -> None:
    scheduler.add_job(_run_scheduled_scan, "interval", minutes=settings.scan_interval_minutes, id="market_scan", replace_existing=True)
    scheduler.start()
    logger.info("Scheduler started. Scan interval: %s minutes", settings.scan_interval_minutes)


def stop_scheduler() -> None:
    scheduler.shutdown(wait=False)
