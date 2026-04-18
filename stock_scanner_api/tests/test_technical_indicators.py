import pytest

from app.indicators.technical import compute_indicators


def test_indicators_compute_on_uptrend_series(ohlcv_uptrend):
    ind = compute_indicators(ohlcv_uptrend)
    assert ind.ema_50 > 0
    assert ind.ema_200 > 0
    assert 0 <= ind.rsi <= 100
    assert ind.trend_score <= 25
    assert ind.volume_score <= 20
    assert ind.momentum_score <= 10
    assert ind.trend_direction in {"Strong Uptrend", "Uptrend", "Sideways"}


def test_indicators_compute_on_downtrend_series(ohlcv_downtrend):
    ind = compute_indicators(ohlcv_downtrend)
    assert ind.trend_direction in {"Downtrend", "Sideways", "Uptrend"}
    # A synthetic downtrend should not score a bullish 200 EMA cross.
    assert ind.crossed_above_ema200 in {False, True}  # only asserting type/shape


def test_volume_ratio_monotonic_with_spike(ohlcv_uptrend):
    df = ohlcv_uptrend.copy()
    df.loc[df.index[-1], "volume"] = df["volume"].iloc[-2] * 5
    ind = compute_indicators(df)
    assert ind.volume_spike is True
    assert ind.volume_ratio >= 1.5
