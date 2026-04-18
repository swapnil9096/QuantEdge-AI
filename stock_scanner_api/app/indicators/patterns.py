from __future__ import annotations

from dataclasses import dataclass, field

import pandas as pd


@dataclass
class PatternResult:
    bullish_engulfing: bool = False
    morning_star: bool = False
    hammer: bool = False
    inside_bar_breakout: bool = False
    detected_patterns: list = field(default_factory=list)
    pattern_score: int = 0


class PatternDetector:
    @staticmethod
    def bullish_engulfing(df: pd.DataFrame) -> bool:
        prev, curr = df.iloc[-2], df.iloc[-1]
        prev_bearish = prev["close"] < prev["open"]
        curr_bullish = curr["close"] > curr["open"]
        body_engulf = curr["close"] > prev["open"] and curr["open"] < prev["close"]
        return bool(prev_bearish and curr_bullish and body_engulf)

    @staticmethod
    def morning_star(df: pd.DataFrame) -> bool:
        if len(df) < 3:
            return False
        c1, c2, c3 = df.iloc[-3], df.iloc[-2], df.iloc[-1]
        c1_bearish = c1["close"] < c1["open"]
        c1_body = abs(c1["close"] - c1["open"])
        c2_small = abs(c2["close"] - c2["open"]) < c1_body * 0.3
        c3_bullish = c3["close"] > c3["open"]
        c3_recovery = c3["close"] > (c1["open"] + c1["close"]) / 2
        return bool(c1_bearish and c2_small and c3_bullish and c3_recovery)

    @staticmethod
    def hammer(df: pd.DataFrame) -> bool:
        curr = df.iloc[-1]
        body = abs(curr["close"] - curr["open"])
        lower_wick = min(curr["open"], curr["close"]) - curr["low"]
        upper_wick = curr["high"] - max(curr["open"], curr["close"])
        if body == 0:
            return False
        long_lower = lower_wick >= 2 * body
        small_upper = upper_wick <= body * 0.5
        return bool(long_lower and small_upper)

    @staticmethod
    def inside_bar_breakout(df: pd.DataFrame) -> bool:
        if len(df) < 3:
            return False
        mother = df.iloc[-2]
        child = df.iloc[-3]
        current = df.iloc[-1]
        inside = child["high"] <= mother["high"] and child["low"] >= mother["low"]
        breakout_up = current["close"] > mother["high"]
        return bool(inside and breakout_up)

    def detect_all(self, df: pd.DataFrame) -> PatternResult:
        result = PatternResult()
        if len(df) < 3:
            return result
        result.bullish_engulfing = self.bullish_engulfing(df)
        result.morning_star = self.morning_star(df)
        result.hammer = self.hammer(df)
        result.inside_bar_breakout = self.inside_bar_breakout(df)

        patterns = []
        if result.bullish_engulfing:
            patterns.append("Bullish Engulfing")
        if result.morning_star:
            patterns.append("Morning Star")
        if result.hammer:
            patterns.append("Hammer")
        if result.inside_bar_breakout:
            patterns.append("Inside Bar Breakout")
        result.detected_patterns = patterns

        score = 0
        if result.bullish_engulfing:
            score += 8
        if result.morning_star:
            score += 7
        if result.hammer:
            score += 5
        if result.inside_bar_breakout:
            score += 6
        result.pattern_score = min(score, 20)
        return result
