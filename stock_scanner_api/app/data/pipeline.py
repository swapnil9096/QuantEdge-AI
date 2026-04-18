"""Data pipeline: fetch ≥5y OHLCV, clean, normalize, store locally."""
from __future__ import annotations

import asyncio
import logging
from pathlib import Path

import numpy as np
import pandas as pd

from app.data_providers.yahoo_provider import YahooFinanceProvider


logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data"
PROCESSED_DIR = DATA_DIR / "processed"
FEATURES_DIR = DATA_DIR / "features"
MIN_ROWS_5Y = 1000  # ~5 years trading days


def _ensure_dirs() -> None:
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    FEATURES_DIR.mkdir(parents=True, exist_ok=True)


def _clean_ohlcv(df: pd.DataFrame) -> pd.DataFrame:
    if df is None or df.empty:
        return pd.DataFrame()
    out = df.copy()
    for col in ["open", "high", "low", "close", "volume"]:
        if col not in out.columns:
            return pd.DataFrame()
        out[col] = pd.to_numeric(out[col], errors="coerce")
    out = out.dropna(subset=["open", "high", "low", "close", "volume"])
    out = out[out["volume"] > 0]
    out["high"] = np.maximum(out["high"], np.maximum(out["open"], out["close"]))
    out["low"] = np.minimum(out["low"], np.minimum(out["open"], out["close"]))
    return out


async def fetch_and_store_symbol(symbol: str, years: int = 5) -> bool:
    """Fetch 5y OHLCV for one symbol, clean, store as parquet. Returns True if stored."""
    _ensure_dirs()
    provider = YahooFinanceProvider()
    df = await provider.fetch_ohlcv_historical(symbol, years=years)
    if df is None or df.empty or len(df) < MIN_ROWS_5Y:
        logger.debug("Insufficient data for %s (rows=%s)", symbol, len(df) if df is not None else 0)
        return False
    df = _clean_ohlcv(df)
    if len(df) < MIN_ROWS_5Y:
        return False
    path = PROCESSED_DIR / f"{symbol.upper()}.parquet"
    df.to_parquet(path, index=True)
    logger.info("Stored %s: %d rows to %s", symbol, len(df), path)
    return True


async def fetch_and_store_universe(symbols: list[str], years: int = 5, max_concurrent: int = 3) -> dict[str, bool]:
    """Fetch and store OHLCV for multiple symbols with limited concurrency."""
    sem = asyncio.Semaphore(max_concurrent)

    async def one(s: str) -> tuple[str, bool]:
        async with sem:
            ok = await fetch_and_store_symbol(s, years=years)
            return s, ok

    results = await asyncio.gather(*[one(s) for s in symbols])
    return dict(results)


def load_processed(symbol: str) -> pd.DataFrame | None:
    """Load processed OHLCV from data/processed/{symbol}.parquet."""
    path = PROCESSED_DIR / f"{symbol.upper()}.parquet"
    if not path.exists():
        return None
    try:
        return pd.read_parquet(path)
    except Exception as exc:
        logger.warning("Load failed for %s: %s", symbol, exc)
        return None


def list_processed_symbols() -> list[str]:
    """List symbols that have processed parquet files."""
    _ensure_dirs()
    return [p.stem for p in PROCESSED_DIR.glob("*.parquet")]
