from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

import pandas as pd


@dataclass
class NewsItem:
    title: str
    source: str
    published_at: datetime | None = None
    url: str | None = None


@dataclass
class FundamentalData:
    market_cap: float | None = None
    revenue_growth: float | None = None
    profit_growth: float | None = None
    debt_to_equity: float | None = None
    roe: float | None = None
    promoter_holding: float | None = None
    institutional_holding: float | None = None
    increasing_quarterly_results: bool | None = None
    operating_cash_flow_positive: bool | None = None
    source: str = "unknown"
    raw: dict = field(default_factory=dict)


@dataclass
class StockResearchBundle:
    symbol: str
    ohlcv: pd.DataFrame
    fundamentals: FundamentalData
    news: list[NewsItem] = field(default_factory=list)
    ohlcv_source: str = "unknown"
    fundamental_sources: list[str] = field(default_factory=list)
    news_sources: list[str] = field(default_factory=list)
