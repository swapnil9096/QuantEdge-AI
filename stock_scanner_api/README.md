# High-Probability Stock Scanner API

Production-ready backend service that scans a stock universe every 5 minutes and returns only strict, high-probability opportunities.

## Stack

- Python + FastAPI
- Pandas + pandas-ta
- PostgreSQL (signal history)
- Redis (cache + scheduler lock)
- APScheduler (background scans)
- Async data fetching (`httpx`)

## Strategy Logic (Strict)

### Fundamental (0-50)

- Market cap > midcap threshold
- Revenue growth >= 12%
- Profit growth > 0
- Debt/Equity < 1
- ROE > 15
- Promoter holding >= configured threshold
- Increasing quarterly results
- Positive operating cash flow

All mandatory checks must pass for a symbol to be emitted.

### Technical (0-50)

Primary setup (mandatory):
- Just crossed above EMA 200 (latest candle)
- Bullish Engulfing on latest candle

Additional confirmations:
- Volume spike vs 20-day average
- RSI in 50-65 range
- MACD bullish crossover
- Price above VWAP
- Breakout above consolidation range

## Scoring

- `fundamental_score` (0-50)
- `technical_score` (0-50)
- `ai_sentiment_score` (0-10, additive)

Only outputs where:
- fundamentals pass
- EMA crossover is fresh
- bullish engulfing exists
- total `signal_strength` >= 80

## API Endpoints

- `GET /health`
- `GET /api/v1/scan-stocks?symbols=RELIANCE,TCS`
- `GET /api/v1/signals`
- `GET /api/v1/signals/high-confidence`
- `GET /api/v1/top-trades`
- `GET /api/v1/stock-analysis/{symbol}`
- `GET /api/v1/backtest-results`
- `GET /api/v1/model-performance`

## Response Format

```json
{
  "stock": "RELIANCE",
  "entry_price": 2805.2,
  "stop_loss": 2738.8,
  "target_1": 2938.0,
  "target_2": 3004.4,
  "signal_strength": 86,
  "pattern_detected": "Bullish Engulfing",
  "ema_status": "Crossed Above 200 EMA",
  "volume_confirmation": true,
  "analysis_summary": "RELIANCE meets strict filters with score 86/100..."
}
```

## Start everything (one command)

From the project root, run:

```bash
chmod +x scripts/start_all.sh   # once
./scripts/start_all.sh
```

Or, if you have `make`: **`make start`**

This script will:

1. **Start PostgreSQL and Redis** (using Docker if available, otherwise Homebrew services if installed).
2. **Wait** until both are reachable (or skip if already running).
3. **Start the API** on http://localhost:8000 (health: `/health`, dashboard: `/dashboard`).

If Docker and Homebrew are not available, start Postgres and Redis yourself, then run:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

---

## Run Locally

1) Copy env file:

```bash
cp .env.example .env
```

2) Install dependencies:

```bash
pip install -r requirements.txt
```

3) Start PostgreSQL and Redis (required for scan-stocks, signals, top-trades):

   **Option A – Docker (recommended):**
   ```bash
   docker compose up -d postgres redis
   ```

   **Option B – Local install (macOS):**
   ```bash
   brew install postgresql@16 redis
   brew services start postgresql@16
   brew services start redis
   ```
   Create the `postgres` role and `stock_scanner` database (run once; requires `psql` in PATH):
   ```bash
   ./scripts/setup_postgres.sh
   ```
   Or manually: `psql postgres -c "CREATE ROLE postgres WITH LOGIN PASSWORD 'postgres' SUPERUSER CREATEDB;"` then `psql postgres -c "CREATE DATABASE stock_scanner OWNER postgres;"`

   **Verify connectivity** (from project root):
   ```bash
   python scripts/check_services.py
   ```
   You should see `PostgreSQL: OK` and `Redis: OK`. If not, endpoints that use the DB (e.g. `/scan-stocks`, `/top-trades`) will return errors.

4) Run API:

```bash
uvicorn app.main:app --reload --port 8000
```

## Run with Docker Compose (all in one)

```bash
docker compose up --build
```

## Notes on Providers

- Yahoo Finance and NSE are enabled by default.
- Alpha Vantage, TwelveData, Polygon are automatically used when API keys are set.
- Provider orchestrator merges available fields and falls back automatically.

## AI Research Layer

- If `OPENAI_API_KEY` is configured, model-based sentiment scoring is used.
- Otherwise, a deterministic headline sentiment heuristic runs as fallback.

---

## Quantitative Trading System (ML + Backtesting)

The same API includes a full quantitative pipeline: data, feature engineering, ML models, backtesting, and trade analytics. Only trades with **ML probability > 80%** are returned as high-probability when a model is trained.

### Components

| Component | Location | Purpose |
|-----------|----------|---------|
| Data pipeline | `app/data/` | Fetch ≥5y OHLCV (Yahoo), clean, store under `data/processed/` |
| Feature engineering | `app/features/` | EMAs 20/50/200, RSI, MACD, ATR, VWAP, volume spike, momentum, trend, volatility, candlestick patterns, smart money; target = +3% in 5 days |
| ML models | `app/ml/` | Train RF, XGBoost, LightGBM, Gradient Boosting; compare; persist best under `data/models/` |
| Backtesting | `app/backtesting/` | Simulate trades from scanner + ML signals; win rate, profit factor, Sharpe, max drawdown, risk/reward |
| Analytics | `app/analytics/` | Aggregate backtest results and model performance for API |

### How to train models

1. **Install extra deps** (if not already): `pip install scikit-learn xgboost lightgbm joblib ta`

2. **Fetch historical data** (from project root):
   ```bash
   python -c "
   import asyncio
   from app.data.pipeline import fetch_and_store_universe
   from app.services.universe import fetch_nifty_universe
   async def run():
       symbols = await fetch_nifty_universe()
       symbols = symbols[:30]  # or use full universe
       r = await fetch_and_store_universe(symbols, years=5)
       print('Stored:', sum(1 for v in r.values() if v))
   asyncio.run(run())
   "
   ```

3. **Build features and combined dataset**:
   ```bash
   python -c "
   from app.data.pipeline import list_processed_symbols
   from app.features.engineering import build_combined_dataset
   symbols = list_processed_symbols()
   build_combined_dataset(symbols)
   "
   ```

4. **Train and save best model**:
   ```bash
   python scripts/train_models.py
   ```
   Or from Python: `from app.ml.train import train_and_compare; train_and_compare()`.

5. **Optional: refresh backtest cache**  
   Call `GET /api/v1/backtest-results?use_cache=false` or run backtest in code using `app.analytics.metrics.compute_backtest_results(use_cache=False)`.

### Quant API endpoints

- `GET /api/v1/scan-stocks` – Scan universe; returns only trades with ML probability > 80% when model is available. Response includes `probability_score`, `model_prediction`, `target`, `ai_analysis`.
- `GET /api/v1/top-trades` – Same trade format: `symbol`, `entry_price`, `stop_loss`, `target`, `probability_score`, `model_prediction`, `ai_analysis`.
- `GET /api/v1/backtest-results?use_cache=true` – Win rate, profit factor, avg return, max drawdown, Sharpe ratio, risk/reward, equity curve.
- `GET /api/v1/model-performance` – Best model name, validation/test metrics (accuracy, precision, recall, F1, ROC-AUC).

### Trade output format (example)

```json
{
  "symbol": "TCS",
  "entry_price": 3750,
  "stop_loss": 3650,
  "target": 3950,
  "probability_score": 87,
  "model_prediction": "High Probability",
  "ai_analysis": "The stock shows a strong upward trend with a recent bullish engulfing pattern and high volume breakout. Machine learning models predict a high probability continuation move."
}
```

### Dashboard

Open the built-in dashboard at **http://localhost:8000/dashboard/** after starting the API. It shows trade signals, ML probability, backtest statistics, equity curve, and win rate. For a custom React app, point it at the same API base URL and use the endpoints above.
