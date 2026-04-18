"""Load best model and predict probability of success (+3% in 5 days)."""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from app.features.engineering import build_feature_matrix, get_feature_columns

logger = logging.getLogger(__name__)

MODELS_DIR = Path(__file__).resolve().parent.parent.parent / "data" / "models"
BEST_MODEL_JSON = MODELS_DIR / "best_model.json"
_model_cache: dict[str, Any] = {}
_meta_cache: dict | None = None


def _load_meta() -> dict | None:
    global _meta_cache
    if _meta_cache is not None:
        return _meta_cache
    if not BEST_MODEL_JSON.exists():
        return None
    try:
        with open(BEST_MODEL_JSON) as f:
            _meta_cache = json.load(f)
        return _meta_cache
    except Exception as exc:
        logger.warning("Load best_model.json failed: %s", exc)
        return None


def _get_model():
    meta = _load_meta()
    if meta is None:
        return None, None
    name = meta.get("best_model")
    if not name:
        return None, meta
    if name in _model_cache:
        return _model_cache[name], meta
    import joblib

    path = MODELS_DIR / f"{name}.joblib"
    if not path.exists():
        return None, meta
    try:
        model = joblib.load(path)
        _model_cache[name] = model
        return model, meta
    except Exception as exc:
        logger.warning("Load model %s failed: %s", path, exc)
        return None, meta


def predict_proba_for_ohlcv(df: pd.DataFrame) -> float | None:
    """Build features from OHLCV (last row used), return probability of success or None."""
    if df is None or len(df) < 250:
        return None
    features = build_feature_matrix(df)
    if features.empty:
        return None
    row = features.iloc[[-1]]
    return predict_proba_for_features(row)


def predict_proba_for_features(feature_row: pd.DataFrame) -> float | None:
    """Predict probability of success from a feature row (or multiple rows)."""
    model, meta = _get_model()
    if model is None or meta is None:
        return None
    cols = meta.get("feature_columns") or get_feature_columns()
    missing = [c for c in cols if c not in feature_row.columns]
    if missing:
        return None
    X = feature_row[cols].fillna(0)
    try:
        proba = model.predict_proba(X)
        if proba.ndim == 2:
            return float(proba[0, 1])
        return float(proba[0])
    except Exception as exc:
        logger.warning("predict_proba failed: %s", exc)
        return None


def get_probability_of_success(symbol: str, ohlcv_df: pd.DataFrame | None = None) -> float | None:
    """Convenience: if ohlcv_df provided use it; else load from data/processed. Returns 0..1 or None."""
    if ohlcv_df is not None:
        return predict_proba_for_ohlcv(ohlcv_df)
    from app.data.pipeline import load_processed

    df = load_processed(symbol)
    return predict_proba_for_ohlcv(df) if df is not None else None


def model_available() -> bool:
    return _load_meta() is not None and _get_model()[0] is not None


def get_model_meta() -> dict | None:
    return _load_meta()
