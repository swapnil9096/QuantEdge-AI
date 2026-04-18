"""Aggregate backtest results and model performance for API."""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from app.backtesting.engine import run_backtest, run_backtest_universe, BacktestResult
from app.data.pipeline import list_processed_symbols, load_processed
from app.ml.inference import get_model_meta

logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data"
BACKTEST_CACHE_PATH = DATA_DIR / "backtest_results.json"
ML_PROB_THRESHOLD = 0.80


def get_model_performance() -> dict[str, Any]:
    """Return best model metadata and test metrics for GET /model-performance."""
    meta = get_model_meta()
    if meta is None:
        return {
            "model_available": False,
            "message": "No trained model. Run training pipeline first (see README).",
        }
    return {
        "model_available": True,
        "best_model": meta.get("best_model"),
        "feature_columns": meta.get("feature_columns", []),
        "validation": {
            "accuracy": meta.get("val_accuracy"),
            "precision": meta.get("val_precision"),
            "recall": meta.get("val_recall"),
            "f1": meta.get("val_f1"),
            "roc_auc": meta.get("val_roc_auc"),
        },
        "test": {
            "accuracy": meta.get("test_accuracy"),
            "precision": meta.get("test_precision"),
            "recall": meta.get("test_recall"),
            "f1": meta.get("test_f1"),
            "roc_auc": meta.get("test_roc_auc"),
        },
    }


def compute_backtest_results(use_cache: bool = True) -> dict[str, Any]:
    """Run backtest on all processed symbols (or return cached). For GET /backtest-results."""
    if use_cache and BACKTEST_CACHE_PATH.exists():
        try:
            import json
            with open(BACKTEST_CACHE_PATH) as f:
                return json.load(f)
        except Exception as exc:
            logger.warning("Load backtest cache failed: %s", exc)

    symbols = list_processed_symbols()
    if not symbols:
        return {
            "total_trades": 0,
            "win_rate_pct": 0.0,
            "profit_factor": 0.0,
            "avg_return_pct": 0.0,
            "max_drawdown_pct": 0.0,
            "sharpe_ratio": 0.0,
            "risk_reward_ratio": 0.0,
            "equity_curve": [],
            "message": "No processed data. Run data pipeline first.",
        }

    symbol_to_ohlcv: dict[str, Any] = {}
    for sym in symbols[:50]:  # cap to avoid slow response
        df = load_processed(sym)
        if df is not None and len(df) >= 260:
            symbol_to_ohlcv[sym] = df

    if not symbol_to_ohlcv:
        return {
            "total_trades": 0,
            "win_rate_pct": 0.0,
            "profit_factor": 0.0,
            "avg_return_pct": 0.0,
            "max_drawdown_pct": 0.0,
            "sharpe_ratio": 0.0,
            "risk_reward_ratio": 0.0,
            "equity_curve": [],
            "message": "Insufficient OHLCV for backtest (need 260+ rows per symbol).",
        }

    result = run_backtest_universe(symbol_to_ohlcv, prob_threshold=ML_PROB_THRESHOLD)

    payload = {
        "total_trades": result.total_trades,
        "wins": result.wins,
        "losses": result.losses,
        "win_rate_pct": result.win_rate_pct,
        "profit_factor": result.profit_factor,
        "avg_return_pct": result.avg_return_pct,
        "max_drawdown_pct": result.max_drawdown_pct,
        "sharpe_ratio": result.sharpe_ratio,
        "risk_reward_ratio": result.risk_reward_ratio,
        "equity_curve": result.equity_curve,
        "symbols_backtested": len(symbol_to_ohlcv),
    }
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    try:
        import json
        with open(BACKTEST_CACHE_PATH, "w") as f:
            json.dump(payload, f, indent=2)
    except Exception as exc:
        logger.warning("Save backtest cache failed: %s", exc)
    return payload
