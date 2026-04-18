from __future__ import annotations

import logging
from typing import List

import httpx
import pandas as pd

from app.data_providers.base import MarketDataProvider
from app.data_providers.types import FundamentalData, NewsItem
from app.utils.retry import retry_network


logger = logging.getLogger(__name__)


class AlphaVantageProvider(MarketDataProvider):
    provider_name = "alpha_vantage"

    def __init__(self, api_key: str | None):
        self.api_key = api_key
        self.base_url = "https://www.alphavantage.co/query"

    def _enabled(self) -> bool:
        return bool(self.api_key)

    @staticmethod
    def _symbol_candidates(symbol: str) -> List[str]:
        # Alpha Vantage often expects exchange-qualified symbols for India.
        normalized = symbol.strip().upper()
        if ":" in normalized or "." in normalized:
            return [normalized]
        return [f"NSE:{normalized}", f"BSE:{normalized}", f"{normalized}.BSE", normalized]

    @retry_network
    async def _get(self, params: dict) -> dict:
        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get(self.base_url, params=params)
            response.raise_for_status()
            return response.json()

    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        if not self._enabled():
            return None
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get(
                    {
                        "function": "TIME_SERIES_DAILY",
                        "symbol": candidate,
                        "outputsize": "full",
                        "apikey": self.api_key,
                    }
                )
                raw = payload.get("Time Series (Daily)")
                if not raw:
                    continue
                df = pd.DataFrame.from_dict(raw, orient="index").rename(
                    columns={
                        "1. open": "open",
                        "2. high": "high",
                        "3. low": "low",
                        "4. close": "close",
                        "5. volume": "volume",
                    }
                )
                df = df[["open", "high", "low", "close", "volume"]].astype(float).sort_index()
                if not df.empty:
                    return df.tail(260)
            except Exception as exc:
                logger.debug("Alpha Vantage OHLCV candidate %s failed: %s", candidate, exc)
        return None

    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        if not self._enabled():
            return None
        for candidate in self._symbol_candidates(symbol):
            try:
                overview = await self._get({"function": "OVERVIEW", "symbol": candidate, "apikey": self.api_key})
                earnings = await self._get({"function": "EARNINGS", "symbol": candidate, "apikey": self.api_key})
                if not overview:
                    continue

                quarterly = earnings.get("quarterlyEarnings", [])
                increasing = None
                if len(quarterly) >= 2:
                    latest_eps = float(quarterly[0].get("reportedEPS", 0) or 0)
                    prev_eps = float(quarterly[1].get("reportedEPS", 0) or 0)
                    increasing = latest_eps >= prev_eps

                return FundamentalData(
                    market_cap=float(overview["MarketCapitalization"]) if overview.get("MarketCapitalization") else None,
                    revenue_growth=float(overview["QuarterlyRevenueGrowthYOY"]) * 100
                    if overview.get("QuarterlyRevenueGrowthYOY")
                    else None,
                    profit_growth=float(overview["QuarterlyEarningsGrowthYOY"]) * 100
                    if overview.get("QuarterlyEarningsGrowthYOY")
                    else None,
                    debt_to_equity=float(overview["DebtToEquity"]) if overview.get("DebtToEquity") else None,
                    roe=float(overview["ReturnOnEquityTTM"]) * 100 if overview.get("ReturnOnEquityTTM") else None,
                    promoter_holding=None,
                    institutional_holding=None,
                    increasing_quarterly_results=increasing,
                    operating_cash_flow_positive=None,
                    source=self.provider_name,
                    raw={"overview": overview, "earnings": earnings, "symbol": candidate},
                )
            except Exception as exc:
                logger.debug("Alpha Vantage fundamentals candidate %s failed: %s", candidate, exc)
        return None

    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        if not self._enabled():
            return []
        for candidate in self._symbol_candidates(symbol):
            try:
                payload = await self._get({"function": "NEWS_SENTIMENT", "tickers": candidate, "apikey": self.api_key})
                items: list[NewsItem] = []
                for article in payload.get("feed", [])[:15]:
                    items.append(
                        NewsItem(
                            title=article.get("title", ""),
                            source=article.get("source", "alphavantage"),
                            url=article.get("url"),
                        )
                    )
                if items:
                    return items
            except Exception as exc:
                logger.debug("Alpha Vantage news candidate %s failed: %s", candidate, exc)
        return []
