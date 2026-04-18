"""Backtest scanner signals on historical data; compute win rate, profit factor, Sharpe, max drawdown, R/R."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from app.features.engineering import build_feature_matrix, get_feature_columns
from app.ml.inference import _get_model

logger = logging.getLogger(__name__)

TRADING_DAYS_PER_YEAR = 252
MIN_PROB_THRESHOLD = 0.80
HOLD_DAYS = 5
TARGET_PCT = 3.0


@dataclass
class BacktestTrade:
    symbol: str
    entry_date: str
    entry_price: float
    stop_loss: float
    target: float
    probability: float
    exit_date: str
    exit_price: float
    pnl_pct: float
    win: bool


@dataclass
class BacktestResult:
    total_trades: int
    wins: int
    losses: int
    win_rate_pct: float
    profit_factor: float
    avg_return_pct: float
    max_drawdown_pct: float
    sharpe_ratio: float
    risk_reward_ratio: float
    equity_curve: list[float] = field(default_factory=list)
    trades: list[BacktestTrade] = field(default_factory=list)


def _compute_sl_target(entry: float, atr: float, close: float, low_prev: float) -> tuple[float, float]:
    stop = min(close, low_prev) - atr * 0.5
    stop = max(stop, entry * 0.90)
    risk = max(entry - stop, 0.01)
    target = entry + risk * 2
    return stop, target


def run_backtest(
    symbol: str,
    ohlcv: pd.DataFrame,
    prob_threshold: float = MIN_PROB_THRESHOLD,
    hold_days: int = HOLD_DAYS,
    target_pct: float = TARGET_PCT,
) -> BacktestResult:
    """Simulate long-only trades when ML probability > threshold. One trade per signal."""
    model, meta = _get_model()
    if model is None or meta is None:
        return BacktestResult(
            total_trades=0, wins=0, losses=0, win_rate_pct=0.0, profit_factor=0.0,
            avg_return_pct=0.0, max_drawdown_pct=0.0, sharpe_ratio=0.0, risk_reward_ratio=0.0,
        )

    if ohlcv is None or len(ohlcv) < 260:
        return BacktestResult(
            total_trades=0, wins=0, losses=0, win_rate_pct=0.0, profit_factor=0.0,
            avg_return_pct=0.0, max_drawdown_pct=0.0, sharpe_ratio=0.0, risk_reward_ratio=0.0,
        )

    features = build_feature_matrix(ohlcv)
    if features.empty:
        return BacktestResult(
            total_trades=0, wins=0, losses=0, win_rate_pct=0.0, profit_factor=0.0,
            avg_return_pct=0.0, max_drawdown_pct=0.0, sharpe_ratio=0.0, risk_reward_ratio=0.0,
        )

    cols = meta.get("feature_columns") or get_feature_columns()
    X = features[cols].fillna(0)
    try:
        proba = model.predict_proba(X)[:, 1]
    except Exception:
        proba = np.zeros(len(X))

    features["prob"] = proba
    features["close"] = ohlcv.reindex(features.index)["close"].values
    features["low"] = ohlcv.reindex(features.index)["low"].values
    features["atr"] = features["atr"]

    trades: list[BacktestTrade] = []
    for i in range(200, len(features) - hold_days - 1):
        if features["prob"].iloc[i] < prob_threshold:
            continue
        entry_price = float(features["close"].iloc[i])
        atr = float(features["atr"].iloc[i])
        low_prev = float(features["low"].iloc[i - 1])
        stop_loss, target = _compute_sl_target(entry_price, atr, entry_price, low_prev)
        exit_idx = i + hold_days
        exit_price = float(features["close"].iloc[exit_idx])
        pnl_pct = (exit_price - entry_price) / entry_price * 100
        win = pnl_pct > 0

        entry_date = str(features.index[i])
        exit_date = str(features.index[exit_idx])
        trades.append(
            BacktestTrade(
                symbol=symbol,
                entry_date=entry_date,
                entry_price=entry_price,
                stop_loss=stop_loss,
                target=target,
                probability=float(features["prob"].iloc[i]),
                exit_date=exit_date,
                exit_price=exit_price,
                pnl_pct=pnl_pct,
                win=win,
            )
        )

    if not trades:
        return BacktestResult(
            total_trades=0, wins=0, losses=0, win_rate_pct=0.0, profit_factor=0.0,
            avg_return_pct=0.0, max_drawdown_pct=0.0, sharpe_ratio=0.0, risk_reward_ratio=0.0,
            trades=[],
        )

    wins = sum(1 for t in trades if t.win)
    losses = len(trades) - wins
    win_rate = wins / len(trades) * 100 if trades else 0
    gross_profit = sum(t.pnl_pct for t in trades if t.win)
    gross_loss = abs(sum(t.pnl_pct for t in trades if not t.win))
    profit_factor = gross_profit / gross_loss if gross_loss > 0 else (gross_profit or 0)
    avg_return = np.mean([t.pnl_pct for t in trades])

    returns = np.array([t.pnl_pct for t in trades])
    cumulative = np.cumsum(returns)
    peak = np.maximum.accumulate(cumulative)
    drawdowns = peak - cumulative
    max_dd = float(np.max(drawdowns)) if len(drawdowns) > 0 else 0.0

    if len(returns) > 1 and np.std(returns) > 0:
        sharpe = np.sqrt(TRADING_DAYS_PER_YEAR / hold_days) * np.mean(returns) / np.std(returns)
    else:
        sharpe = 0.0

    avg_risk = np.mean([t.entry_price - t.stop_loss for t in trades])
    avg_reward = np.mean([t.target - t.entry_price for t in trades])
    risk_reward = (avg_reward / avg_risk) if avg_risk > 0 else 0.0

    equity_curve = [0.0] + list(np.cumsum(returns))

    return BacktestResult(
        total_trades=len(trades),
        wins=wins,
        losses=losses,
        win_rate_pct=round(win_rate, 2),
        profit_factor=round(profit_factor, 4),
        avg_return_pct=round(avg_return, 4),
        max_drawdown_pct=round(max_dd, 4),
        sharpe_ratio=round(sharpe, 4),
        risk_reward_ratio=round(risk_reward, 4),
        equity_curve=equity_curve,
        trades=trades[:500],
    )


def run_backtest_universe(
    symbol_to_ohlcv: dict[str, pd.DataFrame],
    prob_threshold: float = MIN_PROB_THRESHOLD,
) -> BacktestResult:
    """Aggregate backtest over multiple symbols."""
    all_trades: list[BacktestTrade] = []
    for symbol, ohlcv in symbol_to_ohlcv.items():
        res = run_backtest(symbol, ohlcv, prob_threshold=prob_threshold)
        all_trades.extend(res.trades)

    if not all_trades:
        return BacktestResult(
            total_trades=0, wins=0, losses=0, win_rate_pct=0.0, profit_factor=0.0,
            avg_return_pct=0.0, max_drawdown_pct=0.0, sharpe_ratio=0.0, risk_reward_ratio=0.0,
        )

    wins = sum(1 for t in all_trades if t.win)
    losses = len(all_trades) - wins
    win_rate = wins / len(all_trades) * 100
    gross_profit = sum(t.pnl_pct for t in all_trades if t.win)
    gross_loss = abs(sum(t.pnl_pct for t in all_trades if not t.win))
    profit_factor = gross_profit / gross_loss if gross_loss > 0 else 0.0
    avg_return = np.mean([t.pnl_pct for t in all_trades])
    returns = np.array([t.pnl_pct for t in all_trades])
    cumulative = np.cumsum(returns)
    peak = np.maximum.accumulate(cumulative)
    max_dd = float(np.max(peak - cumulative)) if len(cumulative) > 0 else 0.0
    sharpe = (np.sqrt(TRADING_DAYS_PER_YEAR / HOLD_DAYS) * np.mean(returns) / np.std(returns)) if np.std(returns) > 0 else 0.0
    avg_risk = np.mean([t.entry_price - t.stop_loss for t in all_trades])
    avg_reward = np.mean([t.target - t.entry_price for t in all_trades])
    risk_reward = (avg_reward / avg_risk) if avg_risk > 0 else 0.0
    equity_curve = [0.0] + list(np.cumsum(returns))

    return BacktestResult(
        total_trades=len(all_trades),
        wins=wins,
        losses=losses,
        win_rate_pct=round(win_rate, 2),
        profit_factor=round(profit_factor, 4),
        avg_return_pct=round(avg_return, 4),
        max_drawdown_pct=round(max_dd, 4),
        sharpe_ratio=round(sharpe, 4),
        risk_reward_ratio=round(risk_reward, 4),
        equity_curve=equity_curve,
        trades=all_trades[:500],
    )
