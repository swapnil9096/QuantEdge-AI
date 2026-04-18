"""Shared pytest fixtures.

Tests are designed to be offline and deterministic:
- a fake settings object avoids reading .env and network side-effects
- synthetic OHLCV fixtures provide stable inputs for indicator/engine tests
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

# Set required env vars BEFORE app.core.config is imported anywhere.
os.environ.setdefault("APP_ENV", "testing")
os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///:memory:")
os.environ.setdefault("REDIS_URL", "redis://localhost:6379/15")
os.environ.setdefault("UNIVERSE_SYMBOLS", "RELIANCE,TCS")

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
import pytest  # noqa: E402


def _make_ohlcv(n: int, *, seed: int, trend: float, start_price: float = 100.0) -> pd.DataFrame:
    """Generate a synthetic OHLCV DataFrame with a controllable drift.

    Parameters
    ----------
    n     : number of rows (days)
    seed  : RNG seed for reproducibility
    trend : per-day geometric drift, e.g. 0.002 for a gentle uptrend
    """
    rng = np.random.default_rng(seed)
    noise = rng.normal(loc=trend, scale=0.01, size=n)
    closes = start_price * np.cumprod(1.0 + noise)
    # Build OHLC around each close with small intraday range.
    opens = np.concatenate([[start_price], closes[:-1]])
    highs = np.maximum(opens, closes) * (1.0 + rng.uniform(0.001, 0.008, size=n))
    lows = np.minimum(opens, closes) * (1.0 - rng.uniform(0.001, 0.008, size=n))
    volume = rng.integers(low=500_000, high=2_000_000, size=n).astype(float)
    idx = pd.date_range(end=pd.Timestamp.utcnow().normalize(), periods=n, freq="B")
    return pd.DataFrame(
        {
            "open": opens,
            "high": highs,
            "low": lows,
            "close": closes,
            "volume": volume,
        },
        index=idx,
    )


@pytest.fixture
def ohlcv_uptrend() -> pd.DataFrame:
    return _make_ohlcv(n=260, seed=7, trend=0.003)


@pytest.fixture
def ohlcv_downtrend() -> pd.DataFrame:
    return _make_ohlcv(n=260, seed=11, trend=-0.003)


@pytest.fixture
def ohlcv_short() -> pd.DataFrame:
    """Deliberately too short to satisfy the 220-row minimum."""
    return _make_ohlcv(n=50, seed=1, trend=0.002)


@pytest.fixture
def bullish_engulfing_df() -> pd.DataFrame:
    """Two-candle DataFrame that must be recognised as bullish engulfing."""
    return pd.DataFrame(
        [
            {"open": 110.0, "high": 112.0, "low": 100.0, "close": 101.0, "volume": 1_000_000},
            {"open": 100.0, "high": 115.0, "low": 99.0, "close": 114.0, "volume": 2_000_000},
        ]
    )


@pytest.fixture
def non_engulfing_df() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {"open": 100.0, "high": 105.0, "low": 99.0, "close": 104.0, "volume": 1_000_000},
            {"open": 104.0, "high": 106.0, "low": 103.0, "close": 105.0, "volume": 1_100_000},
        ]
    )
