import pytest

from app.services.technical_engine import TechnicalEngine


def test_engine_rejects_short_series(ohlcv_short):
    with pytest.raises(ValueError):
        TechnicalEngine().evaluate(ohlcv_short)


def test_engine_evaluates_uptrend(ohlcv_uptrend):
    result = TechnicalEngine().evaluate(ohlcv_uptrend)
    assert 0 <= result.total_score <= 100
    assert result.entry_price > 0
    assert result.stop_loss < result.entry_price
    assert result.target_1 > result.entry_price
    assert result.target_2 > result.target_1
    assert result.trend_direction in {"Strong Uptrend", "Uptrend", "Sideways"}
    # condition_details must contain the documented strategy conditions.
    for key in (
        "price_above_200ema",
        "price_above_50ema",
        "crossed_above_200ema",
        "bullish_pattern",
        "volume_spike_1_5x",
        "break_of_structure",
        "liquidity_sweep",
        "rsi_50_to_70",
        "no_major_resistance",
    ):
        assert key in result.condition_details


def test_risk_reward_is_two_to_one(ohlcv_uptrend):
    result = TechnicalEngine().evaluate(ohlcv_uptrend)
    risk = result.entry_price - result.stop_loss
    reward = result.target_1 - result.entry_price
    # engine sets target_1 = entry + 2 * risk
    assert reward == pytest.approx(2 * risk, rel=1e-6)
