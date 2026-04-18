from __future__ import annotations

import logging
from datetime import datetime, timedelta

import httpx
import pandas as pd

from app.data_providers.base import MarketDataProvider
from app.data_providers.types import FundamentalData, NewsItem
from app.utils.retry import retry_network


logger = logging.getLogger(__name__)


class NseProvider(MarketDataProvider):
    provider_name = "nse"

    def __init__(self) -> None:
        self.base_url = "https://www.nseindia.com"
        self.headers = {
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/json,text/plain,*/*",
            "Referer": "https://www.nseindia.com/",
        }

    @retry_network
    async def _get(self, endpoint: str, params: dict | None = None) -> dict:
        async with httpx.AsyncClient(timeout=15, headers=self.headers) as client:
            # warm-up request for cookies
            await client.get(f"{self.base_url}/")
            response = await client.get(f"{self.base_url}{endpoint}", params=params)
            response.raise_for_status()
            return response.json()

    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        try:
            today = datetime.utcnow().date()
            from_date = today - timedelta(days=400)
            payload = await self._get(
                "/api/historical/cm/equity",
                {
                    "symbol": symbol,
                    "series": '["EQ"]',
                    "from": from_date.strftime("%d-%m-%Y"),
                    "to": today.strftime("%d-%m-%Y"),
                },
            )
            rows = payload.get("data", [])
            if not rows:
                return None
            df = pd.DataFrame(rows).rename(
                columns={
                    "CH_OPENING_PRICE": "open",
                    "CH_TRADE_HIGH_PRICE": "high",
                    "CH_TRADE_LOW_PRICE": "low",
                    "CH_CLOSING_PRICE": "close",
                    "CH_TOT_TRADED_QTY": "volume",
                }
            )
            df = df[["open", "high", "low", "close", "volume"]].astype(float)
            df = df.iloc[::-1].reset_index(drop=True)
            return df.tail(260)
        except httpx.HTTPStatusError as exc:
            # NSE historical endpoint is inconsistent and frequently returns 404 for valid symbols.
            # Treat as expected fallback condition (Yahoo/other providers are attempted next).
            status = exc.response.status_code if exc.response is not None else None
            if status == 404:
                logger.debug("NSE historical unavailable for %s (404). Falling back to alternate provider.", symbol)
                return None
            logger.warning("NSE OHLCV failed for %s: %s", symbol, exc)
            return None
        except Exception as exc:
            logger.warning("NSE OHLCV failed for %s: %s", symbol, exc)
            return None

    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        try:
            payload = await self._get("/api/quote-equity", {"symbol": symbol})
            info = payload.get("info", {})
            security = payload.get("securityInfo", {})
            price_info = payload.get("priceInfo", {})
            return FundamentalData(
                market_cap=payload.get("securityWiseDP", {}).get("marketCap"),
                revenue_growth=None,
                profit_growth=None,
                debt_to_equity=None,
                roe=None,
                promoter_holding=None,
                institutional_holding=payload.get("shareholdingPattern", {}).get("institutions"),
                increasing_quarterly_results=None,
                operating_cash_flow_positive=None,
                source=self.provider_name,
                raw={"info": info, "security": security, "priceInfo": price_info},
            )
        except Exception as exc:
            logger.warning("NSE fundamentals failed for %s: %s", symbol, exc)
            return None

    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        return []
