"""Train RandomForest, XGBoost, LightGBM, GradientBoosting; compare with CV; persist best model."""
from __future__ import annotations

import json
import logging
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import cross_validate, train_test_split

from app.features.engineering import FEATURES_DIR, get_feature_columns

logger = logging.getLogger(__name__)

MODELS_DIR = Path(__file__).resolve().parent.parent.parent / "data" / "models"
BEST_MODEL_JSON = MODELS_DIR / "best_model.json"
CV_FOLDS = 5
TRAIN_SIZE = 0.70
VAL_SIZE = 0.15
TEST_SIZE = 0.15
RANDOM_STATE = 42


def _ensure_models_dir() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)


def _load_combined_dataset() -> pd.DataFrame | None:
    path = FEATURES_DIR / "combined.parquet"
    if not path.exists():
        return None
    try:
        return pd.read_parquet(path)
    except Exception as exc:
        logger.warning("Load combined dataset failed: %s", exc)
        return None


def _prepare_xy(df: pd.DataFrame):
    cols = get_feature_columns()
    missing = [c for c in cols if c not in df.columns]
    if missing:
        raise ValueError(f"Missing feature columns: {missing}")
    X = df[cols].copy()
    X = X.fillna(0)
    y = df["target"].values
    return X, y


def _train_test_split_stratified(X: pd.DataFrame, y: np.ndarray):
    X_train, X_rest, y_train, y_rest = train_test_split(
        X, y, train_size=TRAIN_SIZE, stratify=y, random_state=RANDOM_STATE
    )
    val_ratio = VAL_SIZE / (VAL_SIZE + TEST_SIZE)
    X_val, X_test, y_val, y_test = train_test_split(
        X_rest, y_rest, train_size=val_ratio, stratify=y_rest, random_state=RANDOM_STATE
    )
    return X_train, X_val, X_test, y_train, y_val, y_test


def train_random_forest(X_train: pd.DataFrame, y_train: np.ndarray):
    from sklearn.ensemble import RandomForestClassifier

    model = RandomForestClassifier(n_estimators=100, max_depth=12, random_state=RANDOM_STATE, n_jobs=-1)
    model.fit(X_train, y_train)
    return model


def train_xgboost(X_train: pd.DataFrame, y_train: np.ndarray):
    try:
        import xgboost as xgb
    except ImportError:
        return None
    model = xgb.XGBClassifier(
        n_estimators=100,
        max_depth=6,
        learning_rate=0.1,
        random_state=RANDOM_STATE,
        eval_metric="logloss",
    )
    model.fit(X_train, y_train)
    return model


def train_lightgbm(X_train: pd.DataFrame, y_train: np.ndarray):
    try:
        import lightgbm as lgb
    except ImportError:
        return None
    model = lgb.LGBMClassifier(
        n_estimators=100,
        max_depth=6,
        learning_rate=0.1,
        random_state=RANDOM_STATE,
        verbose=-1,
    )
    model.fit(X_train, y_train)
    return model


def train_gradient_boosting(X_train: pd.DataFrame, y_train: np.ndarray):
    model = GradientBoostingClassifier(
        n_estimators=100,
        max_depth=5,
        learning_rate=0.1,
        random_state=RANDOM_STATE,
    )
    model.fit(X_train, y_train)
    return model


def evaluate_model(model, X: pd.DataFrame, y: np.ndarray, name: str) -> dict:
    pred = model.predict(X)
    proba = getattr(model, "predict_proba", None)
    if proba is not None:
        try:
            p = model.predict_proba(X)[:, 1]
            auc = float(roc_auc_score(y, p))
        except Exception:
            auc = 0.0
    else:
        auc = 0.0
    return {
        "model": name,
        "accuracy": float(accuracy_score(y, pred)),
        "precision": float(precision_score(y, pred, zero_division=0)),
        "recall": float(recall_score(y, pred, zero_division=0)),
        "f1": float(f1_score(y, pred, zero_division=0)),
        "roc_auc": auc,
    }


def run_cv(model, X: pd.DataFrame, y: np.ndarray, name: str) -> dict:
    scoring = ["accuracy", "precision", "recall", "f1", "roc_auc"]
    cv = cross_validate(model, X, y, cv=CV_FOLDS, scoring=scoring, n_jobs=-1)
    return {
        "model": name,
        "accuracy_mean": float(cv["test_accuracy"].mean()),
        "precision_mean": float(cv["test_precision"].mean()),
        "recall_mean": float(cv["test_recall"].mean()),
        "f1_mean": float(cv["test_f1"].mean()),
        "roc_auc_mean": float(cv["test_roc_auc"].mean()),
    }


def train_and_compare() -> dict:
    """Load combined dataset, split 70/15/15, train all models, compare on validation, save best."""
    _ensure_models_dir()
    df = _load_combined_dataset()
    if df is None or len(df) < 500:
        raise FileNotFoundError("Run data pipeline and feature engineering first. Need data/features/combined.parquet with enough rows.")

    X, y = _prepare_xy(df)
    X_train, X_val, X_test, y_train, y_val, y_test = _train_test_split_stratified(X, y)

    models_to_train = [
        ("random_forest", train_random_forest),
        ("xgboost", train_xgboost),
        ("lightgbm", train_lightgbm),
        ("gradient_boosting", train_gradient_boosting),
    ]

    results = []
    trained = {}

    for name, train_fn in models_to_train:
        try:
            model = train_fn(X_train, y_train)
            if model is None:
                continue
            trained[name] = model
            metrics = evaluate_model(model, X_val, y_val, name)
            results.append(metrics)
            logger.info("%s val metrics: %s", name, metrics)
        except Exception as exc:
            logger.warning("Train %s failed: %s", name, exc)

    if not results:
        raise RuntimeError("No model trained successfully.")

    best = max(results, key=lambda x: (x["roc_auc"], x["f1"]))
    best_name = best["model"]
    best_model = trained.get(best_name)
    if best_model is None:
        raise RuntimeError("Best model not found.")

    # Persist best model (sklearn/joblib for RF and GB; xgb/lgb have own save)
    import joblib

    model_path = MODELS_DIR / f"{best_name}.joblib"
    if best_name in ("xgboost", "lightgbm"):
        try:
            if best_name == "xgboost":
                import xgboost as xgb

                best_model.save_model(str(MODELS_DIR / "best_xgboost.json"))
            else:
                best_model.booster_.save_model(str(MODELS_DIR / "best_lightgbm.txt"))
        except Exception as e:
            logger.warning("Native save failed for %s: %s", best_name, e)
    joblib.dump(best_model, model_path)

    meta = {
        "best_model": best_name,
        "model_path": str(model_path),
        "val_accuracy": best["accuracy"],
        "val_precision": best["precision"],
        "val_recall": best["recall"],
        "val_f1": best["f1"],
        "val_roc_auc": best["roc_auc"],
        "feature_columns": get_feature_columns(),
    }
    with open(BEST_MODEL_JSON, "w") as f:
        json.dump(meta, f, indent=2)

    # Test set evaluation
    test_metrics = evaluate_model(best_model, X_test, y_test, best_name)
    meta["test_accuracy"] = test_metrics["accuracy"]
    meta["test_precision"] = test_metrics["precision"]
    meta["test_recall"] = test_metrics["recall"]
    meta["test_f1"] = test_metrics["f1"]
    meta["test_roc_auc"] = test_metrics["roc_auc"]
    with open(BEST_MODEL_JSON, "w") as f:
        json.dump(meta, f, indent=2)

    logger.info("Best model: %s, test ROC-AUC: %.4f", best_name, test_metrics["roc_auc"])
    return {"best_model": best_name, "results": results, "meta": meta}
