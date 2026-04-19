# QuantEdge AI

Institutional-grade full-stack quantitative stock research and paper trading dashboard for Indian equities (NSE/BSE).

- **Backend** — FastAPI + pandas/NumPy multi-gate scoring engine, real ML model training (XGBoost / LightGBM / RandomForest), WebSocket live prices, paper trading engine with Telegram alerts, Angel One SmartAPI broker integration, Claude Haiku AI trade narratives.
- **Frontend** — React 18 + Vite + Recharts + Lucide, six-tab dark-mode cockpit with WebSocket live price feed.
- **Deployed** — Backend on [Render](https://quantedge-ai-2ru3.onrender.com) · Frontend on [Vercel](https://quant-edge-ai.vercel.app)

---

## File structure

```
quantedge-ai/
├── backend/
│   ├── main.py              # FastAPI server — all endpoints + engines
│   ├── requirements.txt
│   └── .env.example
├── frontend/
│   ├── src/
│   │   ├── App.jsx                     # Thin shell — state + tab router
│   │   ├── constants.js                # Colour palette, API_BASE, TOKEN_KEY
│   │   ├── utils/
│   │   │   ├── api.js                  # All fetch helpers + JWT interceptor
│   │   │   ├── format.js               # fmt, fmtPct, formatCrore
│   │   │   └── indicators.js           # mlScoreFromData, computeSetup
│   │   ├── hooks/
│   │   │   ├── useWebSocket.js         # WS auto-reconnect hook
│   │   │   ├── useMarketStatus.js
│   │   │   └── usePaperPortfolio.js
│   │   └── components/
│   │       ├── Header.jsx
│   │       ├── TopKpiStrip.jsx
│   │       ├── LockScreen.jsx
│   │       ├── ScannerTab.jsx
│   │       ├── BacktestTab.jsx
│   │       ├── MLTab.jsx
│   │       ├── InsightsTab.jsx
│   │       ├── AlphaScanTab.jsx
│   │       ├── PaperTradingPanel.jsx
│   │       ├── TelegramPanel.jsx
│   │       └── BrokerPanel.jsx
│   ├── index.html
│   ├── package.json
│   ├── vercel.json
│   └── vite.config.js
├── render.yaml              # Render deploy config (backend)
└── README.md
```

---

## Quick start (local)

### Mac / Linux

```bash
cd quantedge-ai

# Backend
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # fill in your keys
uvicorn main:app --port 8000 --reload

# Frontend (new terminal)
cd frontend
npm install
npm run dev                 # opens http://localhost:3000
```

### Windows

```bat
cd quantedge-ai\backend
python -m venv .venv && .venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn main:app --port 8000 --reload
```

```bat
cd quantedge-ai\frontend
npm install && npm run dev
```

---

## Environment variables

Create `backend/.env` (never commit — only `.env.example` is tracked):

| Variable | Purpose | Required |
|---|---|---|
| `MASTER_PASSWORD` | Vault unlock password for the app | Yes |
| `ANTHROPIC_API_KEY` | Claude Haiku AI trade narratives | Yes |
| `TWELVEDATA_API_KEY` | Primary OHLCV + fundamentals source | Yes |
| `ALPHA_VANTAGE_API_KEY` | Fundamentals fallback (sector, growth) | Yes |
| `POLYGON_API_KEY` | Optional — reserved for US coverage | No |
| `TELEGRAM_BOT_TOKEN` | Telegram trade alert notifications | Optional |
| `TELEGRAM_CHAT_ID` | Your Telegram chat ID | Optional |
| `ANGEL_CLIENT_ID` | Angel One SmartAPI client ID | Optional |
| `ANGEL_PASSWORD` | Angel One login password | Optional |
| `ANGEL_TOTP_SECRET` | Angel One TOTP secret for 2FA | Optional |
| `ANGEL_API_KEY` | Angel One API key | Optional |

Get keys at:
- [twelvedata.com](https://twelvedata.com) · [alphavantage.co](https://www.alphavantage.co)
- [console.anthropic.com](https://console.anthropic.com)
- [smartapi.angelone.in](https://smartapi.angelone.in)

---

## Dashboard tabs

| Tab | What it does |
|---|---|
| **Scanner** | Watchlist manager — live price feed via WebSocket, per-symbol scan (RSI, EMA, MACD, pattern, R:R), one-click AI Insights |
| **Backtest** | Equity curve vs NIFTY benchmark, 8 KPI cards (CAGR, Sharpe, max drawdown, win rate), monthly returns heatmap |
| **ML Models** | Train real XGBoost / LightGBM / RandomForest / GradientBoosting models on NIFTY 500 OHLCV — shows AUC, accuracy, feature importance |
| **AI Insights** | Per-symbol institutional trade thesis via Claude Haiku — honest narrative that reflects actual gate results |
| **AlphaScan** | Full NIFTY 500 scan — 4-gate scoring (technical 40 + fundamentals 25 + backtest 20 + risk-reward 15), deep analysis, auto paper trade |
| **Broker** | Angel One SmartAPI integration — connect, view funds/positions/holdings, place orders, sync paper trades to live |

---

## AlphaScan scoring engine

Four gates must all pass and combined score ≥ 90 for a trade signal:

### Technical gate (40 pts)
- Price above 200 EMA
- EMA 20 > EMA 50 > EMA 200 (bullish stack)
- Bullish pattern strength ≥ 15
- Volume > 1.5× 20-day average
- Break of structure confirmed
- RSI between 50–70
- No strong resistance within 3%

### Fundamentals gate (25 pts)
- P/E ≤ 50
- Debt/Equity ≤ 1.0
- ROE ≥ 12%
- Revenue growth ≥ 10%
- Profit growth > 0
- Promoter holding ≥ 40%
- FII + DII ≥ 15%

### Backtest gate (20 pts)
- ≥ 10 historical signals
- Win rate ≥ 55%
- Profit factor ≥ 1.5
- Average return ≥ 2%

### Risk/Reward gate (15 pts)
- R:R ≥ 2.0
- Risk ≤ 3% of price
- ATR ≤ 5% of price

---

## Fundamentals data sources (4-layer fallback)

| Priority | Source | Fields |
|---|---|---|
| 1 | TwelveData `/statistics` | PE, D/E, ROE, market cap, book value |
| 2 | Alpha Vantage `OVERVIEW` | PE, ROE, revenue/profit growth, sector |
| 3 | Yahoo Finance (`yfinance`) | All fields via `fast_info` + financial statements |
| 4 | Screener.in (HTML scrape) | Promoter %, FII %, DII %, pledge % |

Shareholding fallback chain: NSE API → Screener.in → TwelveData proxy → Yahoo proxy

---

## Paper trading

Configured via the **Paper Trading** panel inside the Broker tab:

- Capital, risk %, max open positions, max hold days
- Auto-trade: automatically opens a paper trade when AlphaScan score ≥ threshold
- Monitor runs every 10 seconds — checks stop-loss / target / time exit
- Telegram alerts on open, close, and daily summary

---

## WebSocket live prices

Connect to `ws://<host>/ws/live-prices` — protocol:

```json
// Subscribe
{"action": "subscribe", "symbols": ["RELIANCE", "TCS"]}

// Unsubscribe
{"action": "unsubscribe", "symbols": ["RELIANCE"]}

// Server → Client (every 5s)
{"type": "price_update", "symbol": "RELIANCE", "price": 2850.5, "change_pct": 0.45, "volume": 1234567, "ts": "..."}
```

---

## Key API endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | `{"status":"ok", ...}` |
| `POST` | `/unlock` | Unlock vault with master password → JWT token |
| `GET` | `/lock-status` | Check if vault is locked/unlocked |
| `GET` | `/stock-data/{symbol}` | Live price + indicators (cached 60s) |
| `GET` | `/deep-analyze/{symbol}` | Full 4-gate analysis + AI narrative |
| `POST` | `/scan-best-stock` | AlphaScan over NIFTY 500 universe |
| `POST` | `/train-ml` | Train real ML models on NIFTY 500 OHLCV |
| `GET` | `/train-ml/latest` | Most recent ML training result |
| `GET` | `/fundamentals/{symbol}` | Fundamentals snapshot (4-source) |
| `GET` | `/paper-portfolio` | Open paper positions + unrealised P&L |
| `GET` | `/paper-trades` | Paper trade history |
| `GET` | `/paper-equity-curve` | Equity curve data for chart |
| `PATCH` | `/paper-settings` | Update paper trading configuration |
| `GET` | `/broker/status` | Angel One connection status |
| `POST` | `/broker/connect` | Authenticate with Angel One TOTP |
| `GET` | `/broker/funds` | Available cash + margin |
| `POST` | `/broker/order` | Place a real order |
| `POST` | `/broker/sync-paper/{id}` | Replicate paper trade as real order |
| `WS` | `/ws/live-prices` | WebSocket live price feed |
| `POST` | `/proxy/claude` | CORS-safe Claude API proxy |

---

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | FastAPI, httpx, pandas, NumPy, pydantic, yfinance, scikit-learn, XGBoost, LightGBM, joblib, PyJWT, cryptography, exchange-calendars, smartapi-python, pyotp |
| Frontend | React 18, Vite 5, Recharts 2, Lucide React |
| Deployment | Render (backend, free tier, Singapore) · Vercel (frontend, free tier) |
| Data | TwelveData, Alpha Vantage, Yahoo Finance, NSE India API, Screener.in |
| AI | Anthropic Claude Haiku (trade narratives) |

---

## Deployment

### Backend → Render

1. Push to `main` — Render auto-deploys via `render.yaml`
2. Set secret env vars in Render dashboard (not in `render.yaml`):
   `MASTER_PASSWORD`, `ANTHROPIC_API_KEY`, `TWELVEDATA_API_KEY`, `ALPHA_VANTAGE_API_KEY`, `TELEGRAM_BOT_TOKEN`, etc.
3. Python version is pinned to 3.12 via `backend/.python-version`

### Frontend → Vercel

1. Root directory: `quantedge-ai/frontend`
2. Build command: `npm run build`
3. Set env var in Vercel dashboard: `VITE_API_BASE=https://quantedge-ai-2ru3.onrender.com`
4. Redeploy after changing env vars (Vite bakes them at build time)

---

## Troubleshooting

| Issue | Fix |
|---|---|
| Spinner on load / "Backend unreachable" | Render free tier cold start — wait 30–60s and refresh |
| 401 Unauthorized after backend restart | Server session cleared — re-unlock the app |
| AlphaScan returns "no trade" | No stock cleared all 4 gates with score ≥ 90 today |
| Fundamentals all null | Check Render logs for `Fundamentals <SYM>:` line — verify API keys are set |
| yfinance rate limit errors | Normal under load — OHLCV cached 5 min, fundamentals cached 1 hr |
| Telegram alerts not working | Verify `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in Render env |
| Angel One connection fails | Check TOTP secret is base32-encoded and system clock is synced |

---

## License

MIT. For educational and research purposes only. Not investment advice.
