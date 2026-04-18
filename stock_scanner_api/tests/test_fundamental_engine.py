from app.data_providers.types import FundamentalData
from app.services.fundamental_engine import FundamentalEngine


def _strong() -> FundamentalData:
    return FundamentalData(
        market_cap=50_000_000_000,
        revenue_growth=20.0,
        profit_growth=25.0,
        debt_to_equity=0.4,
        roe=22.5,
        promoter_holding=60.0,
        institutional_holding=15.0,
        increasing_quarterly_results=True,
        operating_cash_flow_positive=True,
        source="test",
    )


def _weak() -> FundamentalData:
    return FundamentalData(
        market_cap=1_000_000_000,
        revenue_growth=2.0,
        profit_growth=-5.0,
        debt_to_equity=2.0,
        roe=4.0,
        promoter_holding=10.0,
        institutional_holding=1.0,
        increasing_quarterly_results=False,
        operating_cash_flow_positive=False,
        source="test",
    )


def test_strong_fundamentals_pass_strict_gate():
    result = FundamentalEngine().evaluate(_strong())
    assert result.passed is True
    assert result.score == 50
    assert all(result.details.values())


def test_weak_fundamentals_fail_strict_gate():
    result = FundamentalEngine().evaluate(_weak())
    assert result.passed is False
    assert result.score == 0
    assert not any(result.details.values())


def test_single_missing_check_fails_strict_but_keeps_partial_score():
    data = _strong()
    data.roe = 10.0  # below 15 threshold
    result = FundamentalEngine().evaluate(data)
    assert result.passed is False
    assert 0 < result.score < 50
    assert result.details["roe"] is False
    assert result.details["revenue_growth"] is True
