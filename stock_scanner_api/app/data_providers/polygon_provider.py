from __future__ import annotations

import logging
from datetime import datetime
from typing import List

import httpx
import pandas as pd

from app.data_providers.base import MarketDataProvider
from app.data_providers.types import FundamentalData, NewsItem
from app.utils.retry import retry_network


logger = logging.getLogger(__name__)


class PolygonProvider(MarketDataProvider):
    provider_name = "polygon"

    def __init__(self, api_key: str | None):
        self.api_key = api_key
        self.base_url = "https://api.polygon.io"

    def _enabled(self) -> bool:
        return bool(self.api_key)

    @staticmethod
    def _symbol_candidates(symbol: str) -> List[str]:
        # Polygon is strongest on US tickers; keep native symbol first.
        normalized = symbol.strip().upper()
        if ":" in normalized:
            return [normalized]
        return [normalized]

    @retry_network
    async def _get(self, endpoint: str, params: dict) -> dict:
        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get(f"{self.base_url}{endpoint}", params=params)
            response.raise_for_status()
            return response.json()

    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        if not self._enabled():
            return None
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get(
                    f"/v2/aggs/ticker/{candidate}/range/1/day/2024-01-01/2100-01-01",
                    {"apiKey": self.api_key, "limit": 500},
                )
                results = payload.get("results", [])
                if not results:
                    continue
                df = pd.DataFrame(results).rename(
                    columns={"o": "open", "h": "high", "l": "low", "c": "close", "v": "volume"}
                )
                return df[["open", "high", "low", "close", "volume"]].astype(float).tail(260)
            except Exception as exc:
                logger.debug("Polygon OHLCV candidate %s failed: %s", candidate, exc)
        return None

    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        if not self._enabled():
            return None
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get("/v3/reference/tickers", {"ticker": candidate, "apiKey": self.api_key, "limit": 1})
                results = payload.get("results", [])
                if not results:
                    continue
                ticker = results[0]
                return FundamentalData(
                    market_cap=ticker.get("market_cap"),
                    revenue_growth=None,
                    profit_growth=None,
                    debt_to_equity=None,
                    roe=None,
                    promoter_holding=None,
                    institutional_holding=None,
                    increasing_quarterly_results=None,
                    operating_cash_flow_positive=None,
                    source=self.provider_name,
                    raw=ticker,
                )
            except Exception as exc:
                logger.debug("Polygon fundamentals candidate %s failed: %s", candidate, exc)
        return None

    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        if not self._enabled():
            return []
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get("/v2/reference/news", {"ticker": candidate, "apiKey": self.api_key, "limit": 20})
                items = [
                    NewsItem(
                        title=item.get("title", ""),
                        source=item.get("publisher", {}).get("name", "polygon"),
                        published_at=datetime.fromisoformat(item["published_utc"].replace("Z", "+00:00"))
                        if item.get("published_utc")
                        else None,
                        url=item.get("article_url"),
                    )
                    for item in payload.get("results", [])
                ]
                if items:
                    return items
            except Exception as exc:
                logger.debug("Polygon news candidate %s failed: %s", candidate, exc)
        return []
