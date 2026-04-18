from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from ta.momentum import RSIIndicator
from ta.trend import EMAIndicator, MACD
from ta.volatility import AverageTrueRange


@dataclass
class TechnicalIndicators:
    ema_50: float
    ema_200: float
    price_above_ema50: bool
    price_above_ema200: bool
    crossed_above_ema200: bool
    rsi: float
    rsi_in_zone: bool
    macd_value: float
    macd_signal: float
    macd_bullish_cross: bool
    vwap: float
    price_above_vwap: bool
    atr: float
    volume_current: float
    volume_avg_20: float
    volume_spike: bool
    volume_ratio: float
    trend_direction: str
    trend_score: int
    volume_score: int
    momentum_score: int


def _to_1d(df: pd.DataFrame, col: str) -> pd.Series:
    series = df[col]
    if isinstance(series, pd.DataFrame):
        series = series.iloc[:, 0]
    return pd.to_numeric(series, errors="coerce")


def compute_indicators(df: pd.DataFrame) -> TechnicalIndicators:
    close = _to_1d(df, "close")
    high = _to_1d(df, "high")
    low = _to_1d(df, "low")
    volume = _to_1d(df, "volume")

    ema50_series = EMAIndicator(close=close, window=50).ema_indicator()
    ema200_series = EMAIndicator(close=close, window=200).ema_indicator()
    rsi_series = RSIIndicator(close=close, window=14).rsi()
    macd_ind = MACD(close=close, window_fast=12, window_slow=26, window_sign=9)
    macd_line = macd_ind.macd()
    macd_sig = macd_ind.macd_signal()
    atr_series = AverageTrueRange(high=high, low=low, close=close, window=14).average_true_range()

    typical_price = (high + low + close) / 3
    cum_tp_vol = (typical_price * volume).cumsum()
    cum_vol = volume.cumsum()
    vwap_series = cum_tp_vol / cum_vol.replace(0, np.nan)

    vol_avg_20 = volume.rolling(20).mean()

    curr_close = float(close.iloc[-1])
    prev_close = float(close.iloc[-2])
    ema50_val = float(ema50_series.iloc[-1])
    ema200_val = float(ema200_series.iloc[-1])
    prev_ema200 = float(ema200_series.iloc[-2])
    rsi_val = float(rsi_series.iloc[-1])
    macd_val = float(macd_line.iloc[-1])
    macd_sig_val = float(macd_sig.iloc[-1])
    prev_macd = float(macd_line.iloc[-2])
    prev_macd_sig = float(macd_sig.iloc[-2])
    atr_val = float(atr_series.iloc[-1])
    vwap_val = float(vwap_series.iloc[-1])
    vol_curr = float(volume.iloc[-1])
    vol_avg = float(vol_avg_20.iloc[-1]) if not np.isnan(vol_avg_20.iloc[-1]) else 1.0

    crossed_above_200 = prev_close <= prev_ema200 and curr_close > ema200_val
    price_above_50 = curr_close > ema50_val
    price_above_200 = curr_close > ema200_val
    rsi_in_zone = 50 <= rsi_val <= 70
    macd_cross = prev_macd <= prev_macd_sig and macd_val > macd_sig_val
    above_vwap = curr_close > vwap_val
    vol_ratio = vol_curr / vol_avg if vol_avg > 0 else 0.0
    vol_spike = vol_ratio >= 1.5

    # Trend score (max 25)
    trend_score = 0
    if price_above_200:
        trend_score += 8
    if price_above_50:
        trend_score += 7
    if crossed_above_200:
        trend_score += 10
    if ema50_val > ema200_val:
        trend_score += 3
    trend_score = min(trend_score, 25)

    if price_above_200 and price_above_50:
        trend_dir = "Strong Uptrend"
    elif price_above_200:
        trend_dir = "Uptrend"
    elif not price_above_200 and not price_above_50:
        trend_dir = "Downtrend"
    else:
        trend_dir = "Sideways"

    # Volume score (max 20)
    volume_score = 0
    if vol_spike:
        volume_score += 12
    elif vol_ratio >= 1.2:
        volume_score += 6
    if above_vwap:
        volume_score += 5
    if vol_ratio >= 2.0:
        volume_score += 3
    volume_score = min(volume_score, 20)

    # Momentum score (max 10)
    momentum_score = 0
    if rsi_in_zone:
        momentum_score += 4
    if macd_cross:
        momentum_score += 4
    if macd_val > 0:
        momentum_score += 2
    momentum_score = min(momentum_score, 10)

    return TechnicalIndicators(
        ema_50=ema50_val,
        ema_200=ema200_val,
        price_above_ema50=price_above_50,
        price_above_ema200=price_above_200,
        crossed_above_ema200=crossed_above_200,
        rsi=rsi_val,
        rsi_in_zone=rsi_in_zone,
        macd_value=macd_val,
        macd_signal=macd_sig_val,
        macd_bullish_cross=macd_cross,
        vwap=vwap_val,
        price_above_vwap=above_vwap,
        atr=atr_val,
        volume_current=vol_curr,
        volume_avg_20=vol_avg,
        volume_spike=vol_spike,
        volume_ratio=vol_ratio,
        trend_direction=trend_dir,
        trend_score=trend_score,
        volume_score=volume_score,
        momentum_score=momentum_score,
    )
