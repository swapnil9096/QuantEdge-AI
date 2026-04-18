from dataclasses import dataclass

from app.core.config import get_settings
from app.data_providers.types import FundamentalData


@dataclass
class FundamentalEvaluation:
    passed: bool
    score: int
    details: dict


class FundamentalEngine:
    def __init__(self) -> None:
        self.settings = get_settings()

    def evaluate(self, data: FundamentalData) -> FundamentalEvaluation:
        checks = {
            "market_cap": (data.market_cap is not None and data.market_cap >= self.settings.midcap_threshold, 8),
            "revenue_growth": (data.revenue_growth is not None and data.revenue_growth >= 12, 7),
            "profit_growth": (data.profit_growth is not None and data.profit_growth > 0, 7),
            "debt_to_equity": (data.debt_to_equity is not None and data.debt_to_equity < 1, 7),
            "roe": (data.roe is not None and data.roe > 15, 7),
            "promoter_holding": (
                data.promoter_holding is not None and data.promoter_holding >= self.settings.promoter_holding_min,
                5,
            ),
            "increasing_quarterly_results": (data.increasing_quarterly_results is True, 5),
            "positive_operating_cash_flow": (data.operating_cash_flow_positive is True, 4),
        }

        score = sum(weight for passed, weight in checks.values() if passed)
        strict_pass = all(passed for passed, _ in checks.values())
        return FundamentalEvaluation(
            passed=strict_pass,
            score=min(score, 50),
            details={key: passed for key, (passed, _) in checks.items()},
        )
