from __future__ import annotations

from abc import ABC, abstractmethod

import pandas as pd

from app.data_providers.types import FundamentalData, NewsItem


class MarketDataProvider(ABC):
    provider_name: str = "base"

    @abstractmethod
    async def fetch_ohlcv(self, symbol: str) -> pd.DataFrame | None:
        raise NotImplementedError

    @abstractmethod
    async def fetch_fundamentals(self, symbol: str) -> FundamentalData | None:
        raise NotImplementedError

    @abstractmethod
    async def fetch_news(self, symbol: str) -> list[NewsItem]:
        raise NotImplementedError
