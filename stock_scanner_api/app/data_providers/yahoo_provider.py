from __future__ import annotations

import asyncio
import logging
from datetime import datetime

import pandas as pd
import yfinance as yf

from app.data_providers.base import MarketDataProvider
from app.data_providers.types import FundamentalData, NewsItem


logger = logging.getLogger(__name__)


class YahooFinanceProvider(MarketDataProvider):
    provider_name = "yahoo_finance"

    def __init__(self) -> None:
        # yfinance shared internals can return mixed results under concurrent calls.
        self._lock = asyncio.Lock()

    @staticmethod
    def _normalize_symbol(symbol: str) -> str:
        # Indian equities typically require NSE suffix in Yahoo.
        if "." not in symbol and symbol.isalpha() and len(symbol) <= 15:
            return f"{symbol}.NS"
        return symbol

    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        ticker_symbol = self._normalize_symbol(symbol)

        def _download() -> pd.DataFrame:
            df = yf.download(ticker_symbol, period="1y", interval="1d", progress=False, auto_adjust=False)
            if df is None or df.empty:
                return pd.DataFrame()
            # yfinance may return MultiIndex columns like ("Close", "RELIANCE.NS")
            # for single-ticker downloads. Flatten to a single level.
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = [col[0] if isinstance(col, tuple) else col for col in df.columns]
            df = df.rename(
                columns={
                    "Open": "open",
                    "High": "high",
                    "Low": "low",
                    "Close": "close",
                    "Volume": "volume",
                }
            )
            df = df[["open", "high", "low", "close", "volume"]]
            # Keep first occurrence if duplicate columns are present after flattening.
            df = df.loc[:, ~df.columns.duplicated()]
            for column in ["open", "high", "low", "close", "volume"]:
                df[column] = pd.to_numeric(df[column], errors="coerce")
            df = df.dropna()
            return df

        try:
            async with self._lock:
                df = await asyncio.to_thread(_download)
            return df if not df.empty else None
        except Exception as exc:
            logger.warning("Yahoo OHLCV fetch failed for %s: %s", symbol, exc)
            return None

    async def fetch_ohlcv_historical(self, symbol: str, years: int = 5) -> pd.DataFrame | None:
        """Fetch OHLCV for quant pipeline (min 5 years). Uses period=5y for yfinance."""
        ticker_symbol = self._normalize_symbol(symbol)

        def _download() -> pd.DataFrame:
            df = yf.download(
                ticker_symbol,
                period=f"{min(years, 10)}y",
                interval="1d",
                progress=False,
                auto_adjust=False,
                threads=False,
            )
            if df is None or df.empty:
                return pd.DataFrame()
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = [col[0] if isinstance(col, tuple) else col for col in df.columns]
            df = df.rename(
                columns={
                    "Open": "open",
                    "High": "high",
                    "Low": "low",
                    "Close": "close",
                    "Volume": "volume",
                }
            )
            df = df[["open", "high", "low", "close", "volume"]]
            df = df.loc[:, ~df.columns.duplicated()]
            for column in ["open", "high", "low", "close", "volume"]:
                df[column] = pd.to_numeric(df[column], errors="coerce")
            df = df.dropna()
            return df

        try:
            async with self._lock:
                df = await asyncio.to_thread(_download)
            return df if not df.empty and len(df) >= 252 else None
        except Exception as exc:
            logger.warning("Yahoo historical OHLCV fetch failed for %s: %s", symbol, exc)
            return None

    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        ticker_symbol = self._normalize_symbol(symbol)

        def _info() -> dict:
            ticker = yf.Ticker(ticker_symbol)
            return ticker.info or {}

        try:
            async with self._lock:
                info = await asyncio.to_thread(_info)
            if not info:
                return None

            revenue_growth = info.get("revenueGrowth")
            profit_growth = info.get("earningsGrowth")
            debt_to_equity = info.get("debtToEquity")
            roe = info.get("returnOnEquity")
            operating_cf = info.get("operatingCashflow")

            return FundamentalData(
                market_cap=info.get("marketCap"),
                revenue_growth=(revenue_growth * 100) if isinstance(revenue_growth, (int, float)) else None,
                profit_growth=(profit_growth * 100) if isinstance(profit_growth, (int, float)) else None,
                debt_to_equity=(debt_to_equity / 100) if isinstance(debt_to_equity, (int, float)) else None,
                roe=(roe * 100) if isinstance(roe, (int, float)) else None,
                promoter_holding=None,
                institutional_holding=(
                    info.get("heldPercentInstitutions", 0) * 100
                    if isinstance(info.get("heldPercentInstitutions"), (int, float))
                    else None
                ),
                increasing_quarterly_results=True if info.get("earningsQuarterlyGrowth") else None,
                operating_cash_flow_positive=(
                    True if isinstance(operating_cf, (int, float)) and operating_cf > 0 else False
                ),
                source=self.provider_name,
                raw=info,
            )
        except Exception as exc:
            logger.warning("Yahoo fundamentals fetch failed for %s: %s", symbol, exc)
            return None

    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        ticker_symbol = self._normalize_symbol(symbol)

        def _news() -> list[dict]:
            ticker = yf.Ticker(ticker_symbol)
            return ticker.news or []

        try:
            async with self._lock:
                items = await asyncio.to_thread(_news)
            parsed: list[NewsItem] = []
            for item in items[:15]:
                published = item.get("providerPublishTime")
                parsed.append(
                    NewsItem(
                        title=item.get("title", ""),
                        source=item.get("publisher", "yahoo"),
                        published_at=datetime.fromtimestamp(published) if published else None,
                        url=item.get("link"),
                    )
                )
            return parsed
        except Exception as exc:
            logger.warning("Yahoo news fetch failed for %s: %s", symbol, exc)
            return []
