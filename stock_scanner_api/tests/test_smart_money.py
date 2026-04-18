from app.indicators.smart_money import SmartMoneyAnalyzer


def test_analyzer_produces_bounded_score(ohlcv_uptrend):
    result = SmartMoneyAnalyzer().analyze(ohlcv_uptrend)
    assert 0 <= result.institutional_score <= 25
    assert result.bos_direction in {"none", "bullish", "bearish"}
    assert result.liquidity_grab_side in {"none", "buy_side", "sell_side"}
    assert set(result.details.keys()) == {
        "bos",
        "choch",
        "liquidity_grab",
        "order_block",
        "fvg",
    }


def test_fvg_detects_clear_gap():
    import pandas as pd

    df = pd.DataFrame(
        [
            {"open": 100, "high": 101, "low": 99, "close": 100, "volume": 1},
            {"open": 102, "high": 104, "low": 101, "close": 103, "volume": 1},
            {"open": 105, "high": 108, "low": 104, "close": 107, "volume": 1},  # low > first high
        ]
    )
    fvg, direction, rng = SmartMoneyAnalyzer.detect_fvg(df)
    assert fvg is True
    assert direction == "bullish"
    assert rng[0] <= rng[1]
