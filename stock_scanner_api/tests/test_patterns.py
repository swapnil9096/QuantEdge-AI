from app.indicators.patterns import PatternDetector


def test_bullish_engulfing_detected(bullish_engulfing_df):
    assert PatternDetector.bullish_engulfing(bullish_engulfing_df) is True


def test_non_engulfing_not_detected(non_engulfing_df):
    assert PatternDetector.bullish_engulfing(non_engulfing_df) is False


def test_detect_all_scores_bullish_engulfing(bullish_engulfing_df):
    import pandas as pd

    # `detect_all` requires at least 3 rows; prepend a neutral candle.
    padded = pd.concat(
        [
            pd.DataFrame(
                [{"open": 108.0, "high": 111.0, "low": 107.0, "close": 110.0, "volume": 900_000}]
            ),
            bullish_engulfing_df,
        ],
        ignore_index=True,
    )
    result = PatternDetector().detect_all(padded)
    assert "Bullish Engulfing" in result.detected_patterns
    assert 8 <= result.pattern_score <= 20
