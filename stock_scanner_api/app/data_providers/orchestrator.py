from __future__ import annotations

import logging
from dataclasses import asdict

import pandas as pd

from app.core.config import get_settings
from app.data_providers.alphavantage_provider import AlphaVantageProvider
from app.data_providers.base import MarketDataProvider
from app.data_providers.nse_provider import NseProvider
from app.data_providers.polygon_provider import PolygonProvider
from app.data_providers.twelvedata_provider import TwelveDataProvider
from app.data_providers.types import FundamentalData, StockResearchBundle
from app.data_providers.yahoo_provider import YahooFinanceProvider


logger = logging.getLogger(__name__)


class DataProviderOrchestrator:
    def __init__(self) -> None:
        settings = get_settings()
        self.ohlcv_providers: list[MarketDataProvider] = [NseProvider()]
        if settings.alpha_vantage_api_key:
            self.ohlcv_providers.append(AlphaVantageProvider(settings.alpha_vantage_api_key))
        if settings.twelvedata_api_key:
            self.ohlcv_providers.append(TwelveDataProvider(settings.twelvedata_api_key))
        # Yahoo remains a robust fallback for NSE symbols.
        self.ohlcv_providers.append(YahooFinanceProvider())
        if settings.polygon_api_key:
            self.ohlcv_providers.append(PolygonProvider(settings.polygon_api_key))

        self.providers: list[MarketDataProvider] = [NseProvider(), YahooFinanceProvider()]
        if settings.alpha_vantage_api_key:
            self.providers.append(AlphaVantageProvider(settings.alpha_vantage_api_key))
        if settings.twelvedata_api_key:
            self.providers.append(TwelveDataProvider(settings.twelvedata_api_key))
        if settings.polygon_api_key:
            self.providers.append(PolygonProvider(settings.polygon_api_key))

    async def fetch_research_bundle(self, symbol: str) -> StockResearchBundle | None:
        ohlcv, ohlcv_source = await self._fetch_ohlcv(symbol)
        if ohlcv is None or ohlcv.empty:
            return None

        fundamentals, fundamental_sources = await self._fetch_fundamentals(symbol)
        if fundamentals is None:
            fundamentals = FundamentalData(source="none")
            fundamental_sources = []

        news, news_sources = await self._fetch_news(symbol)

        return StockResearchBundle(
            symbol=symbol,
            ohlcv=ohlcv,
            fundamentals=fundamentals,
            news=news,
            ohlcv_source=ohlcv_source,
            fundamental_sources=fundamental_sources,
            news_sources=news_sources,
        )

    async def _fetch_ohlcv(self, symbol: str) -> tuple[pd.DataFrame | None, str]:
        for provider in self.ohlcv_providers:
            data = await provider.fetch_ohlcv(symbol)
            if data is not None and not data.empty:
                logger.info("OHLCV for %s fetched via %s", symbol, provider.provider_name)
                return data, provider.provider_name
        return None, "none"

    async def _fetch_fundamentals(self, symbol: str) -> tuple[FundamentalData | None, list[str]]:
        merged = FundamentalData(source="merged")
        populated_fields = 0
        sources: list[str] = []

        for provider in self.providers:
            data = await provider.fetch_fundamentals(symbol)
            if data is None:
                continue
            sources.append(provider.provider_name)
            for key, value in asdict(data).items():
                if key in {"source", "raw"}:
                    continue
                if value is not None and getattr(merged, key) is None:
                    setattr(merged, key, value)
                    populated_fields += 1
            merged.raw[provider.provider_name] = data.raw

        if populated_fields == 0:
            return None, []
        return merged, sources

    async def _fetch_news(self, symbol: str) -> tuple[list, list[str]]:
        news = []
        sources = []
        for provider in self.providers:
            provider_news = await provider.fetch_news(symbol)
            if provider_news:
                sources.append(provider.provider_name)
            news.extend(provider_news)
        dedup = {}
        for item in news:
            dedup[item.title] = item
        return list(dedup.values())[:25], sources
