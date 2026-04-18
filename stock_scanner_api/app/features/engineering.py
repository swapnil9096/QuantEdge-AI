"""Feature engineering: EMAs, RSI, MACD, ATR, VWAP, patterns, smart money, target (+3% in 5 days)."""
from __future__ import annotations

import logging
from pathlib import Path

import numpy as np
import pandas as pd
from ta.momentum import RSIIndicator
from ta.trend import EMAIndicator, MACD
from ta.volatility import AverageTrueRange

from app.indicators.patterns import PatternDetector
from app.indicators.smart_money import SmartMoneyAnalyzer

logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data"
PROCESSED_DIR = DATA_DIR / "processed"
FEATURES_DIR = DATA_DIR / "features"
TARGET_FORWARD_DAYS = 5
TARGET_PCT = 3.0
MIN_ROWS = 250  # need enough for EMA200 + forward


def _to_1d(df: pd.DataFrame, col: str) -> pd.Series:
    s = df[col]
    if isinstance(s, pd.DataFrame):
        s = s.iloc[:, 0]
    return pd.to_numeric(s, errors="coerce")


def build_feature_matrix(df: pd.DataFrame) -> pd.DataFrame:
    """Build full feature matrix (no target) for each row. Requires open, high, low, close, volume."""
    if df is None or len(df) < MIN_ROWS:
        return pd.DataFrame()

    local = df.copy()
    for col in ["open", "high", "low", "close", "volume"]:
        local[col] = _to_1d(local, col)
    local = local.dropna(subset=["open", "high", "low", "close", "volume"])
    if len(local) < MIN_ROWS:
        return pd.DataFrame()

    close = local["close"]
    high = local["high"]
    low = local["low"]
    volume = local["volume"]
    typical = (high + low + close) / 3

    # EMAs
    ema20 = EMAIndicator(close=close, window=20).ema_indicator()
    ema50 = EMAIndicator(close=close, window=50).ema_indicator()
    ema200 = EMAIndicator(close=close, window=200).ema_indicator()

    # RSI, MACD, ATR
    rsi = RSIIndicator(close=close, window=14).rsi()
    macd_ind = MACD(close=close, window_fast=12, window_slow=26, window_sign=9)
    macd_line = macd_ind.macd()
    macd_signal = macd_ind.macd_signal()
    atr = AverageTrueRange(high=high, low=low, close=close, window=14).average_true_range()

    # VWAP (session-style: use cumsum over full series for simplicity per row)
    cum_tp_vol = (typical * volume).cumsum()
    cum_vol = volume.cumsum().replace(0, np.nan)
    vwap = cum_tp_vol / cum_vol

    # Volume
    vol_avg_20 = volume.rolling(20).mean().replace(0, np.nan)
    volume_spike = (volume / vol_avg_20).fillna(0)

    # Momentum and trend
    momentum_5 = close.pct_change(5)
    momentum_10 = close.pct_change(10)
    trend_strength = (ema20 - ema200) / ema200.replace(0, np.nan)
    volatility = atr / close.replace(0, np.nan)

    out = pd.DataFrame(index=local.index)
    out["ema_20"] = ema20
    out["ema_50"] = ema50
    out["ema_200"] = ema200
    out["rsi"] = rsi
    out["macd"] = macd_line
    out["macd_signal"] = macd_signal
    out["atr"] = atr
    out["vwap"] = vwap
    out["volume_spike_ratio"] = volume_spike
    out["momentum_5"] = momentum_5
    out["momentum_10"] = momentum_10
    out["trend_strength"] = trend_strength
    out["volatility"] = volatility
    out["close"] = close
    out["volume"] = volume

    # Candlestick patterns (rolling per row)
    pattern_det = PatternDetector()
    smc = SmartMoneyAnalyzer()
    bull_eng = []
    hammer_ = []
    morning_star_ = []
    inside_bar_ = []
    bos_bull = []
    liquidity_sweep = []
    order_block_zone = []

    for i in range(200, len(local)):
        window = local.iloc[: i + 1]
        pr = pattern_det.detect_all(window)
        bull_eng.append(1.0 if pr.bullish_engulfing else 0.0)
        hammer_.append(1.0 if pr.hammer else 0.0)
        morning_star_.append(1.0 if pr.morning_star else 0.0)
        inside_bar_.append(1.0 if pr.inside_bar_breakout else 0.0)
        sm = smc.analyze(window)
        bos_bull.append(1.0 if (sm.bos_detected and sm.bos_direction == "bullish") else 0.0)
        liquidity_sweep.append(1.0 if (sm.liquidity_grab_detected and sm.liquidity_grab_side == "buy_side") else 0.0)
        order_block_zone.append(1.0 if (sm.order_block_detected and sm.order_block_type == "bullish") else 0.0)

    for c in ["bullish_engulfing", "hammer", "morning_star", "inside_bar_breakout", "bos_bullish", "liquidity_sweep", "order_block_zone"]:
        out[c] = 0.0
    idx = out.index[200:]
    out.loc[idx, "bullish_engulfing"] = bull_eng
    out.loc[idx, "hammer"] = hammer_
    out.loc[idx, "morning_star"] = morning_star_
    out.loc[idx, "inside_bar_breakout"] = inside_bar_
    out.loc[idx, "bos_bullish"] = bos_bull
    out.loc[idx, "liquidity_sweep"] = liquidity_sweep
    out.loc[idx, "order_block_zone"] = order_block_zone

    # Fill pattern/smc before row 200 with 0
    for c in ["bullish_engulfing", "hammer", "morning_star", "inside_bar_breakout", "bos_bullish", "liquidity_sweep", "order_block_zone"]:
        if c not in out.columns:
            out[c] = 0.0
        out[c] = out[c].fillna(0.0)

    return out


def add_target(df: pd.DataFrame, forward_days: int = TARGET_FORWARD_DAYS, target_pct: float = TARGET_PCT) -> pd.DataFrame:
    """Add binary target: 1 if close in forward_days >= current_close * (1 + target_pct/100)."""
    if df is None or df.empty or "close" not in df.columns:
        return df
    out = df.copy()
    close = out["close"]
    future_close = close.shift(-forward_days)
    out["target"] = (future_close >= close * (1 + target_pct / 100)).astype(int)
    out = out.dropna(subset=["target"])
    return out


def build_dataset_for_symbol(symbol: str, forward_days: int = TARGET_FORWARD_DAYS, target_pct: float = TARGET_PCT) -> pd.DataFrame | None:
    """Load processed OHLCV, build features + target, return dataset. Saves to data/features/{symbol}.parquet."""
    from app.data.pipeline import load_processed

    FEATURES_DIR.mkdir(parents=True, exist_ok=True)
    raw = load_processed(symbol)
    if raw is None or len(raw) < MIN_ROWS:
        return None
    features = build_feature_matrix(raw)
    if features.empty:
        return None
    dataset = add_target(features, forward_days=forward_days, target_pct=target_pct)
    dataset = dataset.dropna()
    path = FEATURES_DIR / f"{symbol.upper()}.parquet"
    dataset.to_parquet(path, index=True)
    logger.info("Built dataset %s: %d rows -> %s", symbol, len(dataset), path)
    return dataset


def build_combined_dataset(symbols: list[str], forward_days: int = TARGET_FORWARD_DAYS, target_pct: float = TARGET_PCT) -> pd.DataFrame | None:
    """Build and concatenate datasets for multiple symbols; add symbol column."""
    frames = []
    for sym in symbols:
        ds = build_dataset_for_symbol(sym, forward_days=forward_days, target_pct=target_pct)
        if ds is not None and not ds.empty:
            ds = ds.copy()
            ds["symbol"] = sym.upper()
            frames.append(ds)
    if not frames:
        return None
    combined = pd.concat(frames, axis=0)
    path = FEATURES_DIR / "combined.parquet"
    combined.to_parquet(path, index=True)
    logger.info("Combined dataset: %d rows -> %s", len(combined), path)
    return combined


def get_feature_columns() -> list[str]:
    """Column names used as model features (exclude target, symbol, close for prediction)."""
    return [
        "ema_20", "ema_50", "ema_200", "rsi", "macd", "macd_signal", "atr", "vwap",
        "volume_spike_ratio", "momentum_5", "momentum_10", "trend_strength", "volatility",
        "bullish_engulfing", "hammer", "morning_star", "inside_bar_breakout",
        "bos_bullish", "liquidity_sweep", "order_block_zone",
    ]
