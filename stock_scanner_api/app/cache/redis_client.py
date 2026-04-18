from __future__ import annotations

import json

from redis import asyncio as aioredis
from redis.exceptions import RedisError

from app.core.config import get_settings


settings = get_settings()
redis_client = aioredis.from_url(settings.redis_url, decode_responses=True)


async def cache_set_json(key: str, payload: dict, ttl: int | None = None) -> None:
    try:
        await redis_client.set(key, json.dumps(payload), ex=ttl or settings.redis_ttl_seconds)
    except RedisError:
        # Cache is optional for local resilience.
        return


async def cache_get_json(key: str) -> dict | None:
    try:
        value = await redis_client.get(key)
    except RedisError:
        return None
    if value is None:
        return None
    return json.loads(value)
