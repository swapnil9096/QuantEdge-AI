from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pandas as pd

from app.indicators.patterns import PatternDetector, PatternResult
from app.indicators.smart_money import SmartMoneyAnalyzer, SmartMoneyResult
from app.indicators.technical import TechnicalIndicators, compute_indicators


@dataclass
class AdvancedTechnicalEvaluation:
    trend_score: int = 0
    volume_score: int = 0
    pattern_score: int = 0
    institutional_score: int = 0
    momentum_score: int = 0
    total_score: int = 0
    is_high_probability: bool = False
    entry_price: float = 0.0
    stop_loss: float = 0.0
    target_1: float = 0.0
    target_2: float = 0.0
    rsi_value: float = 0.0
    trend_direction: str = "Sideways"
    volume_strength: str = "Low"
    detected_patterns: list = field(default_factory=list)
    indicators: dict = field(default_factory=dict)
    smart_money: dict = field(default_factory=dict)
    all_conditions_met: bool = False
    condition_details: dict = field(default_factory=dict)


class TechnicalEngine:
    def __init__(self) -> None:
        self.pattern_detector = PatternDetector()
        self.smc_analyzer = SmartMoneyAnalyzer()

    def evaluate(self, df: pd.DataFrame) -> AdvancedTechnicalEvaluation:
        if len(df) < 220:
            raise ValueError("Insufficient OHLCV candles for 200 EMA strategy")

        local = df.copy()
        for col in ["open", "high", "low", "close", "volume"]:
            s = local[col]
            if isinstance(s, pd.DataFrame):
                s = s.iloc[:, 0]
            local[col] = pd.to_numeric(s, errors="coerce")
        local = local.dropna(subset=["open", "high", "low", "close", "volume"]).copy()
        if len(local) < 220:
            raise ValueError("Insufficient clean OHLCV candles for 200 EMA strategy")

        indicators = compute_indicators(local)
        patterns = self.pattern_detector.detect_all(local)
        smc = self.smc_analyzer.analyze(local)

        total = (
            indicators.trend_score
            + indicators.volume_score
            + patterns.pattern_score
            + smc.institutional_score
            + indicators.momentum_score
        )
        total = min(total, 100)

        entry = float(local["close"].iloc[-1])
        prev_low = float(local["low"].iloc[-2])
        curr_low = float(local["low"].iloc[-1])
        stop_loss = min(curr_low, prev_low) - indicators.atr * 0.5
        risk = max(entry - stop_loss, 0.01)
        target_1 = entry + risk * 2
        target_2 = entry + risk * 3

        if indicators.volume_ratio >= 2.0:
            vol_str = "Very High"
        elif indicators.volume_spike:
            vol_str = "High"
        elif indicators.volume_ratio >= 1.0:
            vol_str = "Average"
        else:
            vol_str = "Low"

        nearby_resistance = self.smc_analyzer.detect_nearby_resistance(local)

        conditions = {
            "price_above_200ema": indicators.price_above_ema200,
            "price_above_50ema": indicators.price_above_ema50,
            "crossed_above_200ema": indicators.crossed_above_ema200,
            "bullish_pattern": bool(patterns.detected_patterns),
            "volume_spike_1_5x": indicators.volume_spike,
            "break_of_structure": smc.bos_detected and smc.bos_direction == "bullish",
            "liquidity_sweep": smc.liquidity_grab_detected and smc.liquidity_grab_side == "buy_side",
            "rsi_50_to_70": indicators.rsi_in_zone,
            "no_major_resistance": not nearby_resistance,
        }
        all_met = all(conditions.values())

        return AdvancedTechnicalEvaluation(
            trend_score=indicators.trend_score,
            volume_score=indicators.volume_score,
            pattern_score=patterns.pattern_score,
            institutional_score=smc.institutional_score,
            momentum_score=indicators.momentum_score,
            total_score=total,
            is_high_probability=total >= 80 and all_met,
            entry_price=entry,
            stop_loss=stop_loss,
            target_1=target_1,
            target_2=target_2,
            rsi_value=indicators.rsi,
            trend_direction=indicators.trend_direction,
            volume_strength=vol_str,
            detected_patterns=patterns.detected_patterns,
            indicators={
                "ema_50": indicators.ema_50,
                "ema_200": indicators.ema_200,
                "rsi": round(indicators.rsi, 2),
                "macd": round(indicators.macd_value, 4),
                "macd_signal": round(indicators.macd_signal, 4),
                "macd_bullish_cross": indicators.macd_bullish_cross,
                "vwap": round(indicators.vwap, 2),
                "atr": round(indicators.atr, 2),
                "volume_current": indicators.volume_current,
                "volume_avg_20": round(indicators.volume_avg_20, 0),
                "volume_ratio": round(indicators.volume_ratio, 2),
            },
            smart_money=smc.details,
            all_conditions_met=all_met,
            condition_details=conditions,
        )
