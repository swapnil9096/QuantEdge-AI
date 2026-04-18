#!/usr/bin/env python3
"""
Train quantitative ML models: data pipeline, feature engineering, train/compare RF, XGBoost, LightGBM, GradientBoosting.
Run from project root: python scripts/train_models.py [--symbols RELIANCE,TCS,HDFCBANK] [--skip-data]
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

# Add project root to path when run as script
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

logging.basicConfig(level=logging.INFO, format="%(levelname)s:%(name)s:%(message)s")
logger = logging.getLogger(__name__)


def run_data_pipeline(symbols: list[str], years: int = 5) -> list[str]:
    from app.data.pipeline import fetch_and_store_universe

    results = asyncio.run(fetch_and_store_universe(symbols, years=years))
    stored = [s for s, ok in results.items() if ok]
    logger.info("Stored OHLCV for %d symbols: %s", len(stored), stored[:10])
    return stored


def run_feature_engineering(symbols: list[str]) -> None:
    from app.features.engineering import build_combined_dataset

    build_combined_dataset(symbols)
    logger.info("Built combined feature dataset for %d symbols", len(symbols))


def run_training() -> dict:
    from app.ml.train import train_and_compare

    return train_and_compare()


def main() -> None:
    parser = argparse.ArgumentParser(description="Quant ML: fetch data, build features, train and save best model")
    parser.add_argument("--symbols", type=str, default="RELIANCE,TCS,HDFCBANK,INFY,ICICIBANK,BHARTIARTL,SBIN,KOTAKBANK,LT,HINDUNILVR", help="Comma-separated symbols")
    parser.add_argument("--years", type=int, default=5, help="Years of history to fetch")
    parser.add_argument("--skip-data", action="store_true", help="Skip data fetch; use existing data/processed and data/features")
    args = parser.parse_args()

    symbols = [s.strip().upper() for s in args.symbols.split(",") if s.strip()]
    if not symbols:
        logger.error("Provide at least one symbol (e.g. --symbols RELIANCE,TCS)")
        sys.exit(1)

    if not args.skip_data:
        stored = run_data_pipeline(symbols, years=args.years)
        if not stored:
            logger.error("No data stored. Check symbols and network.")
            sys.exit(1)
        run_feature_engineering(stored)
    else:
        from app.data.pipeline import list_processed_symbols
        from app.features.engineering import FEATURES_DIR

        existing = list_processed_symbols()
        if not existing:
            logger.error("No processed data in data/processed. Run without --skip-data first.")
            sys.exit(1)
        combined = FEATURES_DIR / "combined.parquet"
        if not combined.exists():
            run_feature_engineering(existing[:30])

    meta = run_training()
    logger.info("Best model: %s", meta["best_model"])
    logger.info("Test ROC-AUC: %s", meta["meta"].get("test_roc_auc"))
    print("Training complete. Use GET /api/v1/model-performance and /api/v1/backtest-results to inspect.")


if __name__ == "__main__":
    main()
