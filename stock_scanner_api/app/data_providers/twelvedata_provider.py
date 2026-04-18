from __future__ import annotations

import logging
from typing import List

import httpx
import pandas as pd

from app.data_providers.base import MarketDataProvider
from app.data_providers.types import FundamentalData, NewsItem
from app.utils.retry import retry_network


logger = logging.getLogger(__name__)


class TwelveDataProvider(MarketDataProvider):
    provider_name = "twelvedata"

    def __init__(self, api_key: str | None):
        self.api_key = api_key
        self.base_url = "https://api.twelvedata.com"

    def _enabled(self) -> bool:
        return bool(self.api_key)

    @staticmethod
    def _symbol_candidates(symbol: str) -> List[str]:
        normalized = symbol.strip().upper()
        if ":" in normalized or "." in normalized:
            return [normalized]
        return [f"{normalized}:NSE", f"{normalized}:BSE", normalized]

    @retry_network
    async def _get(self, endpoint: str, params: dict) -> dict:
        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get(f"{self.base_url}/{endpoint}", params=params)
            response.raise_for_status()
            return response.json()

    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        if not self._enabled():
            return None
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get(
                    "time_series",
                    {
                        "symbol": candidate,
                        "interval": "1day",
                        "outputsize": 260,
                        "apikey": self.api_key,
                    },
                )
                if payload.get("status") == "error":
                    continue
                rows = payload.get("values", [])
                if not rows:
                    continue
                df = pd.DataFrame(rows)
                df = df.rename(columns={"datetime": "date"})
                df = df[["open", "high", "low", "close", "volume"]].astype(float)
                df = df.iloc[::-1].reset_index(drop=True)
                if not df.empty:
                    return df
            except Exception as exc:
                logger.debug("TwelveData OHLCV candidate %s failed: %s", candidate, exc)
        return None

    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        # TwelveData focuses primarily on time-series and indicator endpoints.
        return None

    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        return []
