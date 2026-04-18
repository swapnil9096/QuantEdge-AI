from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd


@dataclass
class SmartMoneyResult:
    bos_detected: bool = False
    bos_direction: str = "none"
    choch_detected: bool = False
    liquidity_grab_detected: bool = False
    liquidity_grab_side: str = "none"
    order_block_detected: bool = False
    order_block_price: float = 0.0
    order_block_type: str = "none"
    fvg_detected: bool = False
    fvg_type: str = "none"
    fvg_range: tuple = (0.0, 0.0)
    institutional_score: int = 0
    details: dict = field(default_factory=dict)


class SmartMoneyAnalyzer:
    @staticmethod
    def _swing_highs_lows(df: pd.DataFrame, lookback: int = 5) -> tuple[list, list]:
        highs, lows = [], []
        high_vals = df["high"].values
        low_vals = df["low"].values
        for idx in range(lookback, len(df) - lookback):
            if high_vals[idx] == max(high_vals[idx - lookback : idx + lookback + 1]):
                highs.append((idx, high_vals[idx]))
            if low_vals[idx] == min(low_vals[idx - lookback : idx + lookback + 1]):
                lows.append((idx, low_vals[idx]))
        return highs, lows

    @staticmethod
    def detect_bos(df: pd.DataFrame, lookback: int = 5) -> tuple[bool, str]:
        """Break of Structure: price breaks the most recent swing high (bullish) or swing low (bearish)."""
        highs, lows = SmartMoneyAnalyzer._swing_highs_lows(df, lookback)
        if len(highs) < 2 or len(lows) < 2:
            return False, "none"
        last_close = float(df["close"].iloc[-1])
        last_swing_high = highs[-1][1]
        last_swing_low = lows[-1][1]
        if last_close > last_swing_high:
            return True, "bullish"
        if last_close < last_swing_low:
            return True, "bearish"
        return False, "none"

    @staticmethod
    def detect_choch(df: pd.DataFrame, lookback: int = 5) -> bool:
        """Change of Character: trend reversal confirmed by breaking structure opposite to prior trend."""
        highs, lows = SmartMoneyAnalyzer._swing_highs_lows(df, lookback)
        if len(highs) < 3 or len(lows) < 3:
            return False
        prev_trend_down = highs[-3][1] > highs[-2][1] and lows[-3][1] > lows[-2][1]
        now_break_up = float(df["close"].iloc[-1]) > highs[-1][1]
        if prev_trend_down and now_break_up:
            return True
        prev_trend_up = lows[-3][1] < lows[-2][1] and highs[-3][1] < highs[-2][1]
        now_break_down = float(df["close"].iloc[-1]) < lows[-1][1]
        return bool(prev_trend_up and now_break_down)

    @staticmethod
    def detect_liquidity_grab(df: pd.DataFrame, lookback: int = 20) -> tuple[bool, str]:
        """Liquidity sweep: wick below recent swing low followed by close above it."""
        _, lows = SmartMoneyAnalyzer._swing_highs_lows(df.iloc[:-3], lookback=5)
        highs, _ = SmartMoneyAnalyzer._swing_highs_lows(df.iloc[:-3], lookback=5)
        if not lows and not highs:
            return False, "none"
        recent = df.iloc[-3:]
        if lows:
            support = lows[-1][1]
            swept_below = float(recent["low"].min()) < support
            closed_above = float(recent["close"].iloc[-1]) > support
            if swept_below and closed_above:
                return True, "buy_side"
        if highs:
            resistance = highs[-1][1]
            swept_above = float(recent["high"].max()) > resistance
            closed_below = float(recent["close"].iloc[-1]) < resistance
            if swept_above and closed_below:
                return True, "sell_side"
        return False, "none"

    @staticmethod
    def detect_order_block(df: pd.DataFrame, lookback: int = 10) -> tuple[bool, float, str]:
        """Order Block: last bearish candle before a strong bullish move (or vice versa)."""
        if len(df) < lookback + 2:
            return False, 0.0, "none"
        recent = df.iloc[-(lookback + 2) :]
        closes = recent["close"].values
        opens = recent["open"].values
        for step in range(len(recent) - 2):
            bearish = closes[step] < opens[step]
            next_bullish = closes[step + 1] > opens[step + 1]
            strong_move = closes[step + 1] > opens[step]
            if bearish and next_bullish and strong_move:
                ob_price = float((opens[step] + closes[step]) / 2)
                current_close = float(df["close"].iloc[-1])
                if current_close >= ob_price:
                    return True, ob_price, "bullish"
        for step in range(len(recent) - 2):
            bullish = closes[step] > opens[step]
            next_bearish = closes[step + 1] < opens[step + 1]
            strong_move = closes[step + 1] < opens[step]
            if bullish and next_bearish and strong_move:
                ob_price = float((opens[step] + closes[step]) / 2)
                current_close = float(df["close"].iloc[-1])
                if current_close <= ob_price:
                    return True, ob_price, "bearish"
        return False, 0.0, "none"

    @staticmethod
    def detect_fvg(df: pd.DataFrame) -> tuple[bool, str, tuple]:
        """Fair Value Gap: gap between candle 1 high and candle 3 low (bullish) or reverse."""
        if len(df) < 3:
            return False, "none", (0.0, 0.0)
        c1 = df.iloc[-3]
        c3 = df.iloc[-1]
        if c3["low"] > c1["high"]:
            return True, "bullish", (float(c1["high"]), float(c3["low"]))
        if c3["high"] < c1["low"]:
            return True, "bearish", (float(c3["high"]), float(c1["low"]))
        return False, "none", (0.0, 0.0)

    @staticmethod
    def detect_nearby_resistance(df: pd.DataFrame, threshold_pct: float = 2.0) -> bool:
        """Returns True if current price is within threshold_pct of a recent swing high."""
        highs, _ = SmartMoneyAnalyzer._swing_highs_lows(df, lookback=5)
        if not highs:
            return False
        current = float(df["close"].iloc[-1])
        for _, level in highs[-5:]:
            if level > current and ((level - current) / current * 100) < threshold_pct:
                return True
        return False

    def analyze(self, df: pd.DataFrame) -> SmartMoneyResult:
        result = SmartMoneyResult()
        bos, bos_dir = self.detect_bos(df)
        result.bos_detected = bos
        result.bos_direction = bos_dir
        result.choch_detected = self.detect_choch(df)
        liq, liq_side = self.detect_liquidity_grab(df)
        result.liquidity_grab_detected = liq
        result.liquidity_grab_side = liq_side
        ob, ob_price, ob_type = self.detect_order_block(df)
        result.order_block_detected = ob
        result.order_block_price = ob_price
        result.order_block_type = ob_type
        fvg, fvg_type, fvg_range = self.detect_fvg(df)
        result.fvg_detected = fvg
        result.fvg_type = fvg_type
        result.fvg_range = fvg_range

        score = 0
        if bos and bos_dir == "bullish":
            score += 8
        if result.choch_detected:
            score += 5
        if liq and liq_side == "buy_side":
            score += 7
        if ob and ob_type == "bullish":
            score += 5
        result.institutional_score = min(score, 25)
        result.details = {
            "bos": {"detected": bos, "direction": bos_dir},
            "choch": result.choch_detected,
            "liquidity_grab": {"detected": liq, "side": liq_side},
            "order_block": {"detected": ob, "price": ob_price, "type": ob_type},
            "fvg": {"detected": fvg, "type": fvg_type, "range": list(fvg_range)},
        }
        return result
