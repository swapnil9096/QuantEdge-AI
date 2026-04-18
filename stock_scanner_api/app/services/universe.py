from __future__ import annotations

import asyncio
import logging
import time
from typing import List

import httpx
import yfinance as yf

from app.cache.redis_client import cache_get_json, cache_set_json

logger = logging.getLogger(__name__)

NIFTY_INDICES = [
    "NIFTY 50",
    "NIFTY NEXT 50",
    "NIFTY MIDCAP 150",
    "NIFTY SMALLCAP 250",
]

CACHE_KEY = "universe:nifty_symbols"
CACHE_TTL = 86400


async def _fetch_nse_index_symbols(index_name: str) -> List[str]:
    url = "https://www.nseindia.com/api/equity-stockIndices"
    params = {"index": index_name}
    headers = {
        "User-Agent": "Mozilla/5.0",
        "Accept": "application/json",
        "Referer": "https://www.nseindia.com/",
    }
    try:
        async with httpx.AsyncClient(timeout=15, headers=headers) as client:
            await client.get("https://www.nseindia.com/")
            response = await client.get(url, params=params)
            response.raise_for_status()
            payload = response.json()
            data = payload.get("data", [])
            symbols = []
            for item in data:
                sym = item.get("symbol", "")
                if sym and sym != index_name and not sym.startswith("NIFTY"):
                    symbols.append(sym.strip().upper())
            return symbols
    except Exception as exc:
        logger.debug("NSE index fetch failed for %s: %s", index_name, exc)
        return []


async def fetch_nifty_universe() -> List[str]:
    cached = await cache_get_json(CACHE_KEY)
    if cached and isinstance(cached, list) and len(cached) > 50:
        logger.info("Loaded %d symbols from cache", len(cached))
        return cached

    all_symbols: set[str] = set()
    for index_name in NIFTY_INDICES:
        symbols = await _fetch_nse_index_symbols(index_name)
        all_symbols.update(symbols)
        if symbols:
            logger.info("Fetched %d symbols from %s", len(symbols), index_name)
        await asyncio.sleep(0.5)

    if not all_symbols:
        logger.warning("NSE index fetch returned 0 symbols; using hardcoded NIFTY 50 fallback")
        all_symbols = _nifty50_fallback()

    result = sorted(all_symbols)
    await cache_set_json(CACHE_KEY, result, ttl=CACHE_TTL)
    logger.info("Universe loaded: %d total symbols", len(result))
    return result


def _nifty50_fallback() -> set[str]:
    return {
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR", "SBIN",
        "BHARTIARTL", "ITC", "KOTAKBANK", "LT", "AXISBANK", "ASIANPAINT", "MARUTI",
        "HCLTECH", "SUNPHARMA", "TITAN", "BAJFINANCE", "WIPRO", "ULTRACEMCO",
        "NESTLEIND", "TECHM", "ADANIENT", "ADANIPORTS", "NTPC", "POWERGRID",
        "ONGC", "JSWSTEEL", "TATAMOTORS", "TATASTEEL", "M&M", "BAJAJFINSV",
        "COALINDIA", "GRASIM", "INDUSINDBK", "BPCL", "DIVISLAB", "DRREDDY",
        "EICHERMOT", "CIPLA", "BRITANNIA", "APOLLOHOSP", "HEROMOTOCO",
        "TATACONSUM", "SBILIFE", "HDFCLIFE", "UPL", "BAJAJ-AUTO", "HINDALCO", "SHRIRAMFIN",
    }
