"""QuantEdge AI — FastAPI backend.

Provides:
  * /health, /                         — liveness / service info
  * /proxy/claude                      — CORS-safe Claude (Anthropic) proxy
  * /scan-best-stock                   — AlphaScan engine over NIFTY 500 universe

Design notes
------------
The AlphaScan engine mirrors an institutional workflow:
  1. Fetch daily OHLCV from TwelveData (Alpha Vantage fallback) with a per-symbol
     5-minute cache and a semaphore-capped concurrency of 8.
  2. Compute indicators in pure pandas/numpy (EMA 20/50/200, RSI, MACD, ATR, VWAP).
  3. Detect candlestick and smart-money structures.
  4. Apply 7 hard conditions; if any fails the symbol is disqualified.
  5. Compute a 0–100 probability score across 5 dimensions (20 pts each).
  6. Keep only winners with score >= 90; pick the top score; hand the context to
     GPT-4o for a concise institutional narrative.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import sqlite3
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional
from zoneinfo import ZoneInfo

# Use the OS trust store instead of certifi's bundle. This is required on
# corporate networks that perform TLS inspection (Zscaler/Netskope/etc.) and
# inject a self-signed root CA into outbound HTTPS. Must run BEFORE httpx is
# imported so the patched ssl module is picked up.
try:
    import truststore

    truststore.inject_into_ssl()
except Exception:
    # Library is optional at runtime; if import fails we fall back to certifi.
    pass

import httpx
import numpy as np
import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

load_dotenv()

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("quantedge")

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

# These API-key globals are initially loaded from process env for backwards
# compatibility, but in the secrets-vault flow they are re-assigned at runtime
# by `_reload_secret_globals` after a successful /unlock. Every call-site reads
# these names at call time (Python global-lookup), so updating them here
# propagates without any other code changes.
TWELVEDATA_API_KEY = os.getenv("TWELVEDATA_API_KEY", "")
ALPHA_VANTAGE_API_KEY = os.getenv("ALPHA_VANTAGE_API_KEY", "")
POLYGON_API_KEY = os.getenv("POLYGON_API_KEY", "")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY", "")

# Portkey gateway support. When PORTKEY_API_KEY is set the Claude proxy routes
# through Portkey instead of hitting api.anthropic.com directly. Two modes:
#   1) Virtual-key  : set PORTKEY_API_KEY + PORTKEY_VIRTUAL_KEY
#                     (Portkey holds the Anthropic secret; no Anthropic key needed here.)
#   2) Pass-through : set PORTKEY_API_KEY + ANTHROPIC_API_KEY
#                     (we forward your Anthropic key via Authorization header and
#                      tell Portkey to use provider=anthropic)
PORTKEY_API_KEY = os.getenv("PORTKEY_API_KEY", "")
PORTKEY_VIRTUAL_KEY = os.getenv("PORTKEY_VIRTUAL_KEY", "")
PORTKEY_BASE_URL = os.getenv("PORTKEY_BASE_URL", "https://api.portkey.ai/v1")
PORTKEY_CONFIG = os.getenv("PORTKEY_CONFIG", "")  # optional Portkey config id

# Telegram alerting — TELEGRAM_BOT_TOKEN lives in the vault (secret).
# TELEGRAM_CHAT_ID is not a secret on its own, stays in .env.
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")

# Angel One SmartAPI — secrets live in vault, loaded by _reload_secret_globals.
ANGEL_CLIENT_ID = os.getenv("ANGEL_CLIENT_ID", "")
ANGEL_PASSWORD = os.getenv("ANGEL_PASSWORD", "")
ANGEL_TOTP_SECRET = os.getenv("ANGEL_TOTP_SECRET", "")
ANGEL_API_KEY = os.getenv("ANGEL_API_KEY", "")


# ---------------------------------------------------------------------------
# Secrets vault (encrypted at rest, password-unlocked at runtime)
# ---------------------------------------------------------------------------

import base64 as _b64
import secrets as _secrets_mod
from threading import Lock as _ThreadLock

try:
    import jwt as _jwt
    from cryptography.hazmat.backends import default_backend as _crypto_backend
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM as _AESGCM
    from cryptography.hazmat.primitives.kdf.scrypt import Scrypt as _Scrypt
    _VAULT_DEPS_OK = True
except ImportError:
    _VAULT_DEPS_OK = False
    logger.warning("Vault dependencies missing. Install cryptography + PyJWT to use secrets lock.")

SECRETS_VAULT_PATH = Path(__file__).resolve().parent / "secrets.enc.json"
SECRETS_VAULT_AAD = b"quantedge-secrets-v1"
SESSION_TTL_SECONDS = int(os.getenv("SESSION_TTL_HOURS", "24")) * 3600

SECRET_KEYS: list[str] = [
    "TWELVEDATA_API_KEY",
    "ALPHA_VANTAGE_API_KEY",
    "POLYGON_API_KEY",
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "PORTKEY_API_KEY",
    "PORTKEY_VIRTUAL_KEY",
    "PORTKEY_CONFIG",
    "TELEGRAM_BOT_TOKEN",
    "ANGEL_CLIENT_ID",
    "ANGEL_PASSWORD",
    "ANGEL_TOTP_SECRET",
    "ANGEL_API_KEY",
]

# API-handled paths that REQUIRE auth by default. Anything not in this set
# falls through to StaticFiles (the React SPA). Prefixes match any sub-path.
_API_PATH_PREFIXES: tuple[str, ...] = (
    "/api-info",
    "/health",
    "/lock-status",
    "/unlock",
    "/lock",
    "/market-status",
    "/market-holidays",
    "/proxy/",
    "/stock-data/",
    "/deep-analyze/",
    "/deep-analysis-history",
    "/paper-portfolio",
    "/paper-equity-curve",
    "/paper-settings",
    "/paper-trades",
    "/telegram-status",
    "/telegram-test",
    "/scan-best-stock",
    "/high-probability-scan",
    "/broker/",
    "/broker",
    "/train-ml",
    "/ws/",
    "/docs",
    "/openapi.json",
    "/redoc",
)

# Paths that remain accessible while the vault is locked. Everything else
# behind the auth gate returns 401.
_PUBLIC_PATHS: set[str] = {
    "/health",
    "/lock-status",
    "/unlock",
    "/market-status",
    "/market-holidays",
    "/api-info",
}
_PUBLIC_PREFIXES: tuple[str, ...] = (
    "/docs",
    "/openapi.json",
    "/redoc",
)


def _is_api_path(path: str) -> bool:
    """True if this path is handled by a registered API route (auth applies)."""
    return any(path == p.rstrip("/") or path.startswith(p) for p in _API_PATH_PREFIXES)


class SecretsVault:
    """Password-unlocked in-memory store for the whitelisted API keys."""

    def __init__(self) -> None:
        self._lock = _ThreadLock()
        self._secrets: dict[str, str] = {}
        self._unlocked = False
        self._failed_attempts = 0
        self._last_failed_at: float = 0.0
        self._session_key: bytes = _secrets_mod.token_bytes(32)

    @property
    def configured(self) -> bool:
        return SECRETS_VAULT_PATH.exists()

    @property
    def unlocked(self) -> bool:
        return self._unlocked

    @property
    def session_key(self) -> bytes:
        return self._session_key

    def backoff_seconds(self) -> int:
        """Cool-down (in seconds) that must elapse since the last failed attempt."""
        if self._failed_attempts < 3:
            return 0
        # 2, 4, 8, 16, 32 — capped at 60.
        return min(2 ** (self._failed_attempts - 2), 60)

    def remaining_backoff(self) -> int:
        wait = self.backoff_seconds()
        if not wait:
            return 0
        elapsed = time.time() - self._last_failed_at
        return max(int(wait - elapsed), 0)

    def unlock(self, password: str) -> bool:
        if not _VAULT_DEPS_OK:
            raise RuntimeError("cryptography/PyJWT not installed.")
        remaining = self.remaining_backoff()
        if remaining:
            raise PermissionError(f"Too many failed attempts. Try again in {remaining}s.")
        if not SECRETS_VAULT_PATH.exists():
            raise FileNotFoundError(
                "No vault configured. Run: python scripts/setup_secrets.py"
            )
        blob = json.loads(SECRETS_VAULT_PATH.read_text(encoding="utf-8"))
        salt = _b64.b64decode(blob["salt"])
        nonce = _b64.b64decode(blob["nonce"])
        ciphertext = _b64.b64decode(blob["ciphertext"])
        kdf = _Scrypt(
            salt=salt,
            length=32,
            n=int(blob.get("scrypt_n", 2 ** 17)),
            r=int(blob.get("scrypt_r", 8)),
            p=int(blob.get("scrypt_p", 1)),
            backend=_crypto_backend(),
        )
        try:
            key = kdf.derive(password.encode("utf-8"))
            aad = blob.get("aad", SECRETS_VAULT_AAD.decode("ascii")).encode("ascii")
            plaintext = _AESGCM(key).decrypt(nonce, ciphertext, aad)
        except Exception:
            self._failed_attempts += 1
            self._last_failed_at = time.time()
            logger.warning("Vault unlock failed (attempt %d).", self._failed_attempts)
            return False
        decoded = json.loads(plaintext)
        with self._lock:
            self._secrets = {str(k): str(v) for k, v in decoded.items()}
            self._unlocked = True
            self._failed_attempts = 0
            self._last_failed_at = 0.0
            self._session_key = _secrets_mod.token_bytes(32)
        _reload_secret_globals(self._secrets)
        logger.info(
            "Vault unlocked with %d secret(s). Session TTL %ds.",
            len(self._secrets),
            SESSION_TTL_SECONDS,
        )
        return True

    def lock(self) -> None:
        with self._lock:
            self._secrets = {}
            self._unlocked = False
            # Rotate the session key so all outstanding JWTs are instantly invalid.
            self._session_key = _secrets_mod.token_bytes(32)
        _reload_secret_globals({})
        logger.info("Vault locked.")

    def get(self, name: str, default: str = "") -> str:
        return self._secrets.get(name, default)


vault = SecretsVault()


def _reload_secret_globals(d: dict[str, str]) -> None:
    """Push the decrypted (or empty) secrets into module globals so existing
    call sites (e.g. `_build_claude_route`) pick up the values with no
    refactor. Called from vault.unlock() and vault.lock()."""
    global TWELVEDATA_API_KEY, ALPHA_VANTAGE_API_KEY, POLYGON_API_KEY
    global OPENAI_API_KEY, ANTHROPIC_API_KEY
    global PORTKEY_API_KEY, PORTKEY_VIRTUAL_KEY, PORTKEY_CONFIG
    global TELEGRAM_BOT_TOKEN
    global ANGEL_CLIENT_ID, ANGEL_PASSWORD, ANGEL_TOTP_SECRET, ANGEL_API_KEY
    TWELVEDATA_API_KEY = d.get("TWELVEDATA_API_KEY", "")
    ALPHA_VANTAGE_API_KEY = d.get("ALPHA_VANTAGE_API_KEY", "")
    POLYGON_API_KEY = d.get("POLYGON_API_KEY", "")
    OPENAI_API_KEY = d.get("OPENAI_API_KEY", "")
    ANTHROPIC_API_KEY = d.get("ANTHROPIC_API_KEY", "")
    PORTKEY_API_KEY = d.get("PORTKEY_API_KEY", "")
    PORTKEY_VIRTUAL_KEY = d.get("PORTKEY_VIRTUAL_KEY", "")
    PORTKEY_CONFIG = d.get("PORTKEY_CONFIG", "")
    TELEGRAM_BOT_TOKEN = d.get("TELEGRAM_BOT_TOKEN", "")
    ANGEL_CLIENT_ID = d.get("ANGEL_CLIENT_ID", "")
    ANGEL_PASSWORD = d.get("ANGEL_PASSWORD", "")
    ANGEL_TOTP_SECRET = d.get("ANGEL_TOTP_SECRET", "")
    ANGEL_API_KEY = d.get("ANGEL_API_KEY", "")


def _issue_session_token() -> dict[str, Any]:
    now = int(time.time())
    payload = {
        "iat": now,
        "exp": now + SESSION_TTL_SECONDS,
        "purpose": "quantedge-session",
    }
    token = _jwt.encode(payload, vault.session_key, algorithm="HS256")
    if isinstance(token, bytes):  # older PyJWT
        token = token.decode("utf-8")
    return {
        "access_token": token,
        "token_type": "Bearer",
        "expires_in": SESSION_TTL_SECONDS,
    }


def _verify_bearer_token(authorization: Optional[str]) -> bool:
    if not authorization or not authorization.lower().startswith("bearer "):
        return False
    token = authorization.split(" ", 1)[1].strip()
    try:
        _jwt.decode(token, vault.session_key, algorithms=["HS256"])
        return True
    except Exception:
        return False


def _path_is_public(path: str) -> bool:
    if path in _PUBLIC_PATHS:
        return True
    return any(path.startswith(prefix) for prefix in _PUBLIC_PREFIXES)

# Anthropic's `web_search_20250305` tool is only supported on api.anthropic.com,
# not on AWS Bedrock (which is what most Portkey workspaces route to). By default
# we strip the tool for any portkey-* route. Flip this to force-enable.
CLAUDE_FORCE_WEB_SEARCH = os.getenv("CLAUDE_FORCE_WEB_SEARCH", "false").lower() in {
    "1",
    "true",
    "yes",
}

ANTHROPIC_DIRECT_URL = "https://api.anthropic.com/v1/messages"
ANTHROPIC_VERSION = "2023-06-01"
ANTHROPIC_DEFAULT_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-latest")

TWELVEDATA_URL = "https://api.twelvedata.com/time_series"
ALPHAVANTAGE_URL = "https://www.alphavantage.co/query"

OHLCV_CACHE_TTL_SECONDS = 300          # 5 minutes per symbol
SCAN_CACHE_TTL_SECONDS = 300           # 5 minutes for /scan-best-stock
MAX_CONCURRENT_FETCHES = 8
MIN_SCORE = 90                          # hard floor for emitting a trade
MIN_OHLCV_ROWS = 220

# NIFTY 500 universe (65+ representative symbols across sectors)
NIFTY_UNIVERSE: list[str] = [
    "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR", "SBIN",
    "BHARTIARTL", "ITC", "KOTAKBANK", "LT", "AXISBANK", "ASIANPAINT", "MARUTI",
    "HCLTECH", "SUNPHARMA", "TITAN", "BAJFINANCE", "WIPRO", "ULTRACEMCO",
    "NESTLEIND", "TECHM", "ADANIENT", "ADANIPORTS", "NTPC", "POWERGRID",
    "ONGC", "JSWSTEEL", "TATAMOTORS", "TATASTEEL", "M&M", "BAJAJFINSV",
    "COALINDIA", "GRASIM", "INDUSINDBK", "BPCL", "DIVISLAB", "DRREDDY",
    "EICHERMOT", "CIPLA", "BRITANNIA", "APOLLOHOSP", "HEROMOTOCO",
    "TATACONSUM", "SBILIFE", "HDFCLIFE", "UPL", "BAJAJ-AUTO", "HINDALCO",
    "SHRIRAMFIN", "PIDILITIND", "DABUR", "GODREJCP", "HAVELLS", "DLF",
    "VEDL", "IOC", "GAIL", "TRENT", "PERSISTENT", "LTIM", "MPHASIS",
    "NAUKRI", "BANKBARODA", "PNB", "CANBK", "IDFCFIRSTB", "IDEA", "YESBANK",
    "ZOMATO",
]

SECTOR_MAP: dict[str, str] = {
    # A minimal static sector mapping; GPT fills the gaps for anything missing.
    "RELIANCE": "Energy", "ONGC": "Energy", "BPCL": "Energy", "IOC": "Energy",
    "GAIL": "Energy", "COALINDIA": "Energy", "NTPC": "Energy",
    "POWERGRID": "Utilities",
    "TCS": "IT", "INFY": "IT", "WIPRO": "IT", "TECHM": "IT", "HCLTECH": "IT",
    "LTIM": "IT", "MPHASIS": "IT", "PERSISTENT": "IT",
    "HDFCBANK": "Banking", "ICICIBANK": "Banking", "SBIN": "Banking",
    "AXISBANK": "Banking", "KOTAKBANK": "Banking", "INDUSINDBK": "Banking",
    "BANKBARODA": "Banking", "PNB": "Banking", "CANBK": "Banking",
    "IDFCFIRSTB": "Banking", "YESBANK": "Banking",
    "HINDUNILVR": "FMCG", "ITC": "FMCG", "NESTLEIND": "FMCG",
    "BRITANNIA": "FMCG", "DABUR": "FMCG", "GODREJCP": "FMCG", "TATACONSUM": "FMCG",
    "MARUTI": "Auto", "TATAMOTORS": "Auto", "M&M": "Auto", "EICHERMOT": "Auto",
    "HEROMOTOCO": "Auto", "BAJAJ-AUTO": "Auto",
    "SUNPHARMA": "Pharma", "DRREDDY": "Pharma", "DIVISLAB": "Pharma",
    "CIPLA": "Pharma", "APOLLOHOSP": "Healthcare",
    "JSWSTEEL": "Metals", "TATASTEEL": "Metals", "HINDALCO": "Metals", "VEDL": "Metals",
    "BHARTIARTL": "Telecom", "IDEA": "Telecom",
    "BAJFINANCE": "NBFC", "BAJAJFINSV": "NBFC", "SHRIRAMFIN": "NBFC",
    "SBILIFE": "Insurance", "HDFCLIFE": "Insurance",
    "LT": "Infrastructure", "ADANIPORTS": "Infrastructure",
    "ADANIENT": "Conglomerate", "ASIANPAINT": "Paints", "PIDILITIND": "Chemicals",
    "ULTRACEMCO": "Cement", "GRASIM": "Cement",
    "TITAN": "Consumer Discretionary", "TRENT": "Retail",
    "DLF": "Realty", "NAUKRI": "Internet", "ZOMATO": "Internet",
    "HAVELLS": "Consumer Durables", "UPL": "Agri Chem",
}


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(
    title="QuantEdge AI",
    description="Institutional-grade deep-analysis + AlphaScan + Claude proxy backend.",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
    allow_credentials=False,
)


@app.middleware("http")
async def _secrets_gate(request: Any, call_next):
    """Every request except the public set must carry a valid session JWT and
    find the vault unlocked. If the vault isn't configured yet we still serve
    the public routes so the UI can render a setup-needed screen."""
    from starlette.responses import JSONResponse

    path = request.url.path
    method = request.method.upper()

    # Frontend (StaticFiles) paths: anything that isn't an API route. Always
    # allowed so the SPA + its assets render even when the vault is locked.
    if not _is_api_path(path):
        return await call_next(request)

    if method == "OPTIONS" or _path_is_public(path):
        return await call_next(request)

    if not vault.configured:
        return JSONResponse(
            status_code=409,
            content={
                "code": "not_configured",
                "message": (
                    "Secrets vault not configured. "
                    "Run `python scripts/setup_secrets.py` on the server."
                ),
            },
        )
    if not vault.unlocked:
        return JSONResponse(
            status_code=401,
            content={
                "code": "locked",
                "message": "Vault is locked. POST /unlock with your master password.",
                "configured": True,
            },
        )
    if not _verify_bearer_token(request.headers.get("Authorization")):
        return JSONResponse(
            status_code=401,
            content={
                "code": "locked",
                "message": "Missing or expired session token. Re-enter password to unlock.",
            },
        )
    return await call_next(request)


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class ClaudeMessage(BaseModel):
    role: str
    content: Any


class ClaudeProxyRequest(BaseModel):
    messages: list[ClaudeMessage]
    system: Optional[str] = None
    max_tokens: int = Field(default=1200, ge=1, le=8192)
    use_search: bool = False
    model: Optional[str] = None
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)


class TradeResult(BaseModel):
    symbol: str
    entry_price: float
    stop_loss: float
    target_price: float
    expected_return: float
    confidence_score: int
    analysis: str
    pattern_detected: str
    rsi: float
    macd_signal: str
    volume_ratio: float
    ema_20: float
    ema_50: float
    ema_200: float
    atr: float
    risk_reward: float
    sector: str
    scan_timestamp: str


class NoTradeResult(BaseModel):
    message: str
    reason: str
    scan_timestamp: str
    stocks_scanned: int


class ScanResponse(BaseModel):
    trade_found: bool
    result: Optional[TradeResult] = None
    no_trade: Optional[NoTradeResult] = None


# ---------------------------------------------------------------------------
# Caches
# ---------------------------------------------------------------------------

_ohlcv_cache: dict[str, tuple[float, pd.DataFrame]] = {}
_scan_cache: dict[str, tuple[float, ScanResponse]] = {}
_ohlcv_lock = asyncio.Lock()
_fetch_semaphore = asyncio.Semaphore(MAX_CONCURRENT_FETCHES)


def _cache_get(cache: dict, key: str, ttl: int):
    item = cache.get(key)
    if not item:
        return None
    ts, value = item
    if time.time() - ts > ttl:
        cache.pop(key, None)
        return None
    return value


def _cache_set(cache: dict, key: str, value) -> None:
    cache[key] = (time.time(), value)


# ---------------------------------------------------------------------------
# Data fetching
# ---------------------------------------------------------------------------


def _twelvedata_symbol(symbol: str) -> str:
    # TwelveData expects SYMBOL:EXCHANGE for non-US equities.
    if ":" in symbol or "." in symbol:
        return symbol
    return f"{symbol}:NSE"


async def _fetch_twelvedata(client: httpx.AsyncClient, symbol: str) -> Optional[pd.DataFrame]:
    if not TWELVEDATA_API_KEY:
        return None
    params = {
        "symbol": _twelvedata_symbol(symbol),
        "interval": "1day",
        "outputsize": 260,
        "apikey": TWELVEDATA_API_KEY,
        "order": "asc",
    }
    try:
        r = await client.get(TWELVEDATA_URL, params=params, timeout=15)
        r.raise_for_status()
        payload = r.json()
        if payload.get("status") == "error" or not payload.get("values"):
            return None
        df = pd.DataFrame(payload["values"])
        df = df.rename(columns={"datetime": "date"})
        df = df[["date", "open", "high", "low", "close", "volume"]]
        df["date"] = pd.to_datetime(df["date"])
        for col in ("open", "high", "low", "close", "volume"):
            df[col] = pd.to_numeric(df[col], errors="coerce")
        df = df.dropna().sort_values("date").reset_index(drop=True)
        return df if len(df) >= MIN_OHLCV_ROWS else None
    except Exception as exc:
        logger.debug("TwelveData fetch failed for %s: %s", symbol, exc)
        return None


async def _fetch_alphavantage(client: httpx.AsyncClient, symbol: str) -> Optional[pd.DataFrame]:
    if not ALPHA_VANTAGE_API_KEY:
        return None
    # Alpha Vantage uses .BSE suffixes for some Indian equities; try NSE-prefixed first.
    candidates = [f"NSE:{symbol}", f"{symbol}.BSE", symbol]
    for candidate in candidates:
        try:
            r = await client.get(
                ALPHAVANTAGE_URL,
                params={
                    "function": "TIME_SERIES_DAILY",
                    "symbol": candidate,
                    "outputsize": "full",
                    "apikey": ALPHA_VANTAGE_API_KEY,
                },
                timeout=20,
            )
            r.raise_for_status()
            payload = r.json()
            series = payload.get("Time Series (Daily)")
            if not series:
                continue
            df = pd.DataFrame.from_dict(series, orient="index").rename(
                columns={
                    "1. open": "open",
                    "2. high": "high",
                    "3. low": "low",
                    "4. close": "close",
                    "5. volume": "volume",
                }
            )
            df.index = pd.to_datetime(df.index)
            df = df[["open", "high", "low", "close", "volume"]].astype(float)
            df = df.sort_index().reset_index().rename(columns={"index": "date"})
            if len(df) >= MIN_OHLCV_ROWS:
                return df.tail(260).reset_index(drop=True)
        except Exception as exc:
            logger.debug("AlphaVantage %s failed: %s", candidate, exc)
    return None


async def fetch_ohlcv(client: httpx.AsyncClient, symbol: str) -> Optional[pd.DataFrame]:
    cached = _cache_get(_ohlcv_cache, symbol, OHLCV_CACHE_TTL_SECONDS)
    if cached is not None:
        return cached

    async with _fetch_semaphore:
        df = await _fetch_twelvedata(client, symbol)
        if df is None:
            df = await _fetch_alphavantage(client, symbol)

    if df is not None:
        async with _ohlcv_lock:
            _cache_set(_ohlcv_cache, symbol, df)
    return df


# ---------------------------------------------------------------------------
# Indicators (pure pandas / numpy, no TA library)
# ---------------------------------------------------------------------------


def _ema(series: pd.Series, span: int) -> pd.Series:
    return series.ewm(span=span, adjust=False).mean()


def _rsi(series: pd.Series, period: int = 14) -> pd.Series:
    delta = series.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1 / period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    return (100 - (100 / (1 + rs))).fillna(50.0)


def _macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9):
    macd_line = _ema(series, fast) - _ema(series, slow)
    signal_line = _ema(macd_line, signal)
    hist = macd_line - signal_line
    return macd_line, signal_line, hist


def _atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
    high, low, close = df["high"], df["low"], df["close"]
    prev_close = close.shift()
    tr = pd.concat(
        [(high - low), (high - prev_close).abs(), (low - prev_close).abs()], axis=1
    ).max(axis=1)
    return tr.ewm(alpha=1 / period, adjust=False).mean()


def _vwap(df: pd.DataFrame) -> pd.Series:
    typical = (df["high"] + df["low"] + df["close"]) / 3.0
    cum_tp_vol = (typical * df["volume"]).cumsum()
    cum_vol = df["volume"].cumsum().replace(0, np.nan)
    return cum_tp_vol / cum_vol


def compute_indicators(df: pd.DataFrame) -> dict[str, Any]:
    close = df["close"]
    ema20 = _ema(close, 20)
    ema50 = _ema(close, 50)
    ema200 = _ema(close, 200)
    rsi = _rsi(close, 14)
    macd_line, signal_line, hist = _macd(close)
    atr = _atr(df, 14)
    vwap = _vwap(df)
    vol_avg20 = df["volume"].rolling(20).mean()

    last = -1
    prev = -2
    curr_close = float(close.iloc[last])
    curr_vol = float(df["volume"].iloc[last])
    avg_vol = float(vol_avg20.iloc[last]) if not np.isnan(vol_avg20.iloc[last]) else curr_vol
    volume_ratio = curr_vol / avg_vol if avg_vol > 0 else 0.0

    return {
        "close": curr_close,
        "prev_close": float(close.iloc[prev]),
        "ema_20": float(ema20.iloc[last]),
        "ema_50": float(ema50.iloc[last]),
        "ema_200": float(ema200.iloc[last]),
        "prev_ema_200": float(ema200.iloc[prev]),
        "rsi": float(rsi.iloc[last]),
        "macd": float(macd_line.iloc[last]),
        "macd_signal_line": float(signal_line.iloc[last]),
        "macd_hist": float(hist.iloc[last]),
        "prev_macd": float(macd_line.iloc[prev]),
        "prev_macd_signal_line": float(signal_line.iloc[prev]),
        "atr": float(atr.iloc[last]),
        "vwap": float(vwap.iloc[last]),
        "volume": curr_vol,
        "avg_volume_20": avg_vol,
        "volume_ratio": volume_ratio,
    }


# ---------------------------------------------------------------------------
# Pattern + smart money detection
# ---------------------------------------------------------------------------


def detect_patterns(df: pd.DataFrame) -> dict[str, Any]:
    """Return the strongest pattern detected and a 0..20 strength score."""
    patterns: list[tuple[str, int]] = []

    c1 = df.iloc[-2]
    c2 = df.iloc[-1]

    body2 = abs(c2["close"] - c2["open"])
    range2 = max(c2["high"] - c2["low"], 1e-9)

    # Bullish Engulfing
    if (
        c1["close"] < c1["open"]
        and c2["close"] > c2["open"]
        and c2["close"] > c1["open"]
        and c2["open"] < c1["close"]
    ):
        patterns.append(("Bullish Engulfing", 18))

    # Hammer
    lower_wick2 = min(c2["open"], c2["close"]) - c2["low"]
    upper_wick2 = c2["high"] - max(c2["open"], c2["close"])
    if body2 > 0 and lower_wick2 >= 2 * body2 and upper_wick2 <= body2 * 0.5:
        patterns.append(("Hammer", 15))

    # Inside Bar Breakout (requires 3 candles)
    if len(df) >= 3:
        mother = df.iloc[-3]
        child = df.iloc[-2]
        if (
            child["high"] <= mother["high"]
            and child["low"] >= mother["low"]
            and c2["close"] > mother["high"]
        ):
            patterns.append(("Inside Bar Breakout", 17))

    # Morning Star (3 candle)
    if len(df) >= 3:
        a = df.iloc[-3]
        b = df.iloc[-2]
        body_a = abs(a["close"] - a["open"])
        if (
            a["close"] < a["open"]
            and abs(b["close"] - b["open"]) < body_a * 0.35
            and c2["close"] > c2["open"]
            and c2["close"] > (a["open"] + a["close"]) / 2
        ):
            patterns.append(("Morning Star", 16))

    # Momentum Breakout: close above last 20-day high by > 0.5%
    if len(df) >= 22:
        prior_high = df["high"].iloc[-22:-2].max()
        if c2["close"] > prior_high * 1.005:
            patterns.append(("Momentum Breakout", 19))

    # Bullish Marubozu: full-body green candle with tiny wicks
    if (
        c2["close"] > c2["open"]
        and (c2["high"] - c2["close"]) / range2 < 0.08
        and (c2["open"] - c2["low"]) / range2 < 0.08
        and body2 / range2 > 0.85
    ):
        patterns.append(("Bullish Marubozu", 17))

    if not patterns:
        return {"name": "None", "strength": 0, "all": []}
    patterns.sort(key=lambda x: x[1], reverse=True)
    top = patterns[0]
    return {"name": top[0], "strength": top[1], "all": [p[0] for p in patterns]}


def _swing_levels(df: pd.DataFrame, lookback: int = 5):
    highs, lows = [], []
    hv = df["high"].values
    lv = df["low"].values
    for i in range(lookback, len(df) - lookback):
        window_high = hv[i - lookback : i + lookback + 1]
        window_low = lv[i - lookback : i + lookback + 1]
        if hv[i] == window_high.max():
            highs.append((i, float(hv[i])))
        if lv[i] == window_low.min():
            lows.append((i, float(lv[i])))
    return highs, lows


def detect_smart_money(df: pd.DataFrame, indicators: dict[str, Any]) -> dict[str, Any]:
    highs, lows = _swing_levels(df)
    last_close = float(df["close"].iloc[-1])

    break_of_structure = False
    higher_highs = False
    if len(highs) >= 2:
        break_of_structure = last_close > highs[-1][1]
        higher_highs = highs[-1][1] > highs[-2][1] and (
            lows[-1][1] > lows[-2][1] if len(lows) >= 2 else True
        )

    # Liquidity grab: recent 3 candles swept below most recent swing low then closed back above
    liquidity_grab = False
    if lows:
        support = lows[-1][1]
        recent = df.iloc[-3:]
        if float(recent["low"].min()) < support and last_close > support:
            liquidity_grab = True

    # Volume accumulation: avg volume of last 5 sessions > avg volume of the 20 before them
    volume_accumulation = False
    if len(df) >= 25:
        recent_avg = df["volume"].iloc[-5:].mean()
        base_avg = df["volume"].iloc[-25:-5].mean()
        if base_avg > 0 and recent_avg > base_avg * 1.15:
            volume_accumulation = True

    # Nearby resistance within 3 %
    strong_resistance_nearby = False
    if highs:
        for _, level in highs[-5:]:
            if level > last_close and (level - last_close) / last_close * 100 < 3:
                strong_resistance_nearby = True
                break

    return {
        "break_of_structure": break_of_structure,
        "higher_highs": higher_highs,
        "liquidity_grab": liquidity_grab,
        "volume_accumulation": volume_accumulation,
        "strong_resistance_nearby": strong_resistance_nearby,
    }


# ---------------------------------------------------------------------------
# Scoring + hard conditions
# ---------------------------------------------------------------------------


def score_stock(
    indicators: dict[str, Any],
    patterns: dict[str, Any],
    smart: dict[str, Any],
) -> dict[str, Any]:
    # 1. Trend (0-20): EMA alignment + VWAP
    trend = 0
    if indicators["close"] > indicators["ema_200"]:
        trend += 6
    if indicators["ema_20"] > indicators["ema_50"] > indicators["ema_200"]:
        trend += 9
    if indicators["close"] > indicators["vwap"]:
        trend += 5
    trend = min(trend, 20)

    # 2. Volume (0-20)
    vr = indicators["volume_ratio"]
    if vr >= 2.5:
        volume = 20
    elif vr >= 2.0:
        volume = 16
    elif vr >= 1.5:
        volume = 12
    elif vr >= 1.2:
        volume = 7
    else:
        volume = max(0, int(vr * 3))
    volume = min(volume, 20)

    # 3. Pattern (0-20)
    pattern_score = min(patterns["strength"], 20)

    # 4. Momentum (0-20)
    momentum = 0
    rsi = indicators["rsi"]
    if 55 <= rsi <= 68:
        momentum += 10
    elif 50 <= rsi <= 70:
        momentum += 7
    if indicators["macd_hist"] > 0:
        momentum += 5
    if (
        indicators["prev_macd"] <= indicators["prev_macd_signal_line"]
        and indicators["macd"] > indicators["macd_signal_line"]
    ):
        momentum += 5
    momentum = min(momentum, 20)

    # 5. Institutional (0-20)
    institutional = 0
    if smart["break_of_structure"]:
        institutional += 8
    if smart["higher_highs"]:
        institutional += 4
    if smart["liquidity_grab"]:
        institutional += 4
    if smart["volume_accumulation"]:
        institutional += 4
    institutional = min(institutional, 20)

    total = trend + volume + pattern_score + momentum + institutional
    return {
        "trend_strength": trend,
        "volume_strength": volume,
        "pattern_strength": pattern_score,
        "momentum": momentum,
        "institutional": institutional,
        "total": total,
    }


def hard_conditions(
    indicators: dict[str, Any],
    patterns: dict[str, Any],
    smart: dict[str, Any],
) -> tuple[bool, list[str]]:
    failures: list[str] = []
    if indicators["close"] <= indicators["ema_200"]:
        failures.append("price_below_200ema")
    if not (indicators["ema_20"] > indicators["ema_50"] > indicators["ema_200"]):
        failures.append("ema_not_stacked")
    if patterns["strength"] < 15:
        failures.append("no_strong_bullish_pattern")
    if indicators["volume_ratio"] <= 1.5:
        failures.append("volume_not_elevated")
    if not smart["break_of_structure"]:
        failures.append("no_break_of_structure")
    if not (50 <= indicators["rsi"] <= 70):
        failures.append("rsi_out_of_zone")
    if smart["strong_resistance_nearby"]:
        failures.append("resistance_within_3pct")
    return (len(failures) == 0, failures)


# ---------------------------------------------------------------------------
# Claude narrative (replaces GPT-4o)
# ---------------------------------------------------------------------------


async def generate_gpt_analysis(context: dict[str, Any]) -> str:
    """Generate trade narrative using Claude API (falls back to heuristic)."""
    if not ANTHROPIC_API_KEY:
        return _heuristic_narrative(context)
    try:
        import httpx

        system_prompt = (
            "You are a senior quantitative trader at a long/short equity desk. "
            "Write a crisp, honest 3-4 sentence trade thesis based ONLY on the actual data provided. "
            "If gates have failed (e.g. EMA not stacked, volume low, resistance nearby), "
            "acknowledge those weaknesses explicitly — do NOT fabricate bullish conditions that aren't present. "
            "Reference the pattern, actual EMA structure, volume ratio, and R/R. "
            "No disclaimers. No bullet points."
        )
        payload = {
            "model": "claude-haiku-4-5-20251001",
            "max_tokens": 280,
            "temperature": 0.2,
            "system": system_prompt,
            "messages": [{"role": "user", "content": _format_context(context)}],
        }
        headers = {
            "x-api-key": ANTHROPIC_API_KEY,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.post(
                "https://api.anthropic.com/v1/messages",
                json=payload,
                headers=headers,
            )
            resp.raise_for_status()
            data = resp.json()
            text = (data.get("content") or [{}])[0].get("text", "").strip()
            return text or _heuristic_narrative(context)
    except Exception as exc:
        logger.warning("Claude narrative failed: %s", exc)
        return _heuristic_narrative(context)


def _format_context(ctx: dict[str, Any]) -> str:
    failures = ctx.get("failures", [])
    failures_str = ", ".join(failures) if failures else "none"
    ema_stacked = ctx["ema20"] > ctx["ema50"] > ctx["ema200"]
    resistance_clear = "no_resistance_within_3pct" not in failures
    return (
        f"Symbol: {ctx['symbol']}  Sector: {ctx['sector']}\n"
        f"Price: {ctx['price']:.2f}  Entry: {ctx['entry']:.2f}  Stop: {ctx['stop']:.2f}  "
        f"Target: {ctx['target']:.2f}  R/R: {ctx['rr']:.2f}  Expected return: {ctx['ret']:.2f}%\n"
        f"Pattern: {ctx['pattern']}  RSI: {ctx['rsi']:.1f}  Volume ratio: {ctx['vr']:.2f}x\n"
        f"EMA 20/50/200: {ctx['ema20']:.2f} / {ctx['ema50']:.2f} / {ctx['ema200']:.2f}  "
        f"EMA stacked (bullish): {ema_stacked}\n"
        f"MACD hist: {ctx['macd_hist']:+.3f}  Break of structure: {ctx['bos']}  "
        f"Resistance clear (>3%): {resistance_clear}  "
        f"Liquidity grab: {ctx['lg']}  Institutional acc: {ctx['vol_acc']}\n"
        f"Combined score: {ctx.get('combined_score', 'N/A')}/100  "
        f"Gates failed: {failures_str}\n"
        f"Score breakdown: {ctx['breakdown']}"
    )


def _heuristic_narrative(ctx: dict[str, Any]) -> str:
    """Honest heuristic narrative that reflects actual gate results."""
    failures = ctx.get("failures", [])
    combined_score = ctx.get("combined_score", 0)
    ema_stacked = ctx["ema20"] > ctx["ema50"] > ctx["ema200"]
    resistance_clear = "no_resistance_within_3pct" not in failures
    volume_ok = "volume_not_elevated" not in failures

    # EMA structure sentence
    if ema_stacked:
        ema_str = f"EMAs are bullishly stacked (20 > 50 > 200) confirming the uptrend"
    else:
        ema_str = (
            f"EMAs are not fully stacked (20: {ctx['ema20']:.0f} / 50: {ctx['ema50']:.0f} / "
            f"200: {ctx['ema200']:.0f}), signalling a mixed trend structure"
        )

    # Volume sentence
    vol_str = (
        f"{ctx['vr']:.1f}x average volume confirms institutional participation"
        if volume_ok
        else f"volume at {ctx['vr']:.1f}x average is below the 1.5x threshold, limiting conviction"
    )

    # Resistance sentence
    res_str = (
        "with no major resistance within 3% above entry"
        if resistance_clear
        else "though strong resistance sits within 3% of entry, capping near-term upside"
    )

    # Conviction sentence
    if combined_score >= 80:
        conviction = f"R/R of {ctx['rr']:.2f} on a score of {combined_score}/100 makes this a high-conviction setup."
    elif combined_score >= 60:
        conviction = (
            f"R/R of {ctx['rr']:.2f} is acceptable but the score of {combined_score}/100 "
            f"suggests waiting for {', '.join(failures[:2]) if failures else 'confirmation'} to improve."
        )
    else:
        conviction = (
            f"With a score of {combined_score}/100 and failures in "
            f"{', '.join(failures[:3]) if failures else 'multiple gates'}, "
            f"this setup lacks sufficient conviction — pass or reduce size significantly."
        )

    return (
        f"{ctx['symbol']} is showing a {ctx['pattern']} with RSI {ctx['rsi']:.1f}, {ema_str}. "
        f"The {vol_str}, {res_str}. {conviction}"
    )


# ---------------------------------------------------------------------------
# AlphaScan orchestration
# ---------------------------------------------------------------------------


async def _evaluate_symbol(client: httpx.AsyncClient, symbol: str) -> Optional[dict[str, Any]]:
    df = await fetch_ohlcv(client, symbol)
    if df is None or len(df) < MIN_OHLCV_ROWS:
        return None
    try:
        indicators = compute_indicators(df)
        patterns = detect_patterns(df)
        smart = detect_smart_money(df, indicators)
        scores = score_stock(indicators, patterns, smart)
        passed, failures = hard_conditions(indicators, patterns, smart)
        return {
            "symbol": symbol,
            "indicators": indicators,
            "patterns": patterns,
            "smart": smart,
            "scores": scores,
            "passed": passed,
            "failures": failures,
        }
    except Exception as exc:
        logger.debug("Evaluate failed for %s: %s", symbol, exc)
        return None


async def _run_alpha_scan() -> ScanResponse:
    cached = _cache_get(_scan_cache, "latest", SCAN_CACHE_TTL_SECONDS)
    if cached is not None:
        return cached

    async with httpx.AsyncClient() as client:
        tasks = [_evaluate_symbol(client, s) for s in NIFTY_UNIVERSE]
        evaluations = await asyncio.gather(*tasks)

    evaluated = [e for e in evaluations if e is not None]
    winners = [e for e in evaluated if e["passed"] and e["scores"]["total"] >= MIN_SCORE]

    now_iso = datetime.now(timezone.utc).isoformat()

    if not winners:
        response = ScanResponse(
            trade_found=False,
            no_trade=NoTradeResult(
                message="No trade opportunity today.",
                reason=(
                    f"Scanned {len(evaluated)} stocks; none cleared the 7-gate filter with a "
                    f"score >= {MIN_SCORE}."
                ),
                scan_timestamp=now_iso,
                stocks_scanned=len(evaluated),
            ),
        )
        _cache_set(_scan_cache, "latest", response)
        return response

    winners.sort(key=lambda e: e["scores"]["total"], reverse=True)
    best = winners[0]
    ind = best["indicators"]
    symbol = best["symbol"]

    entry = ind["close"]
    atr = max(ind["atr"], entry * 0.005)
    stop = entry - 1.5 * atr
    target = entry + 3.0 * atr
    expected_return = (target - entry) / entry * 100
    risk_reward = (target - entry) / max(entry - stop, 1e-9)

    macd_signal = "Bullish" if ind["macd"] > ind["macd_signal_line"] else "Bearish"

    gpt_context = {
        "symbol": symbol,
        "sector": SECTOR_MAP.get(symbol, "Diversified"),
        "price": entry,
        "entry": entry,
        "stop": stop,
        "target": target,
        "rr": risk_reward,
        "ret": expected_return,
        "pattern": best["patterns"]["name"],
        "rsi": ind["rsi"],
        "vr": ind["volume_ratio"],
        "ema20": ind["ema_20"],
        "ema50": ind["ema_50"],
        "ema200": ind["ema_200"],
        "macd_hist": ind["macd_hist"],
        "bos": best["smart"]["break_of_structure"],
        "lg": best["smart"]["liquidity_grab"],
        "vol_acc": best["smart"]["volume_accumulation"],
        "breakdown": best["scores"],
        "failures": best.get("failures", []),
        "combined_score": int(best["scores"]["total"]),
    }
    analysis = await generate_gpt_analysis(gpt_context)

    trade = TradeResult(
        symbol=symbol,
        entry_price=round(entry, 2),
        stop_loss=round(stop, 2),
        target_price=round(target, 2),
        expected_return=round(expected_return, 2),
        confidence_score=int(best["scores"]["total"]),
        analysis=analysis,
        pattern_detected=best["patterns"]["name"],
        rsi=round(ind["rsi"], 2),
        macd_signal=macd_signal,
        volume_ratio=round(ind["volume_ratio"], 2),
        ema_20=round(ind["ema_20"], 2),
        ema_50=round(ind["ema_50"], 2),
        ema_200=round(ind["ema_200"], 2),
        atr=round(ind["atr"], 2),
        risk_reward=round(risk_reward, 2),
        sector=SECTOR_MAP.get(symbol, "Diversified"),
        scan_timestamp=now_iso,
    )
    response = ScanResponse(trade_found=True, result=trade)
    _cache_set(_scan_cache, "latest", response)
    return response


# ===========================================================================
# DEEP ANALYZER — fundamentals + technical + backtest, with strict gating.
# ===========================================================================
#
# Upgrades /scan-best-stock and powers /deep-analyze/{symbol}. Designed so a
# symbol is only emitted when EVERY gate passes and the combined score >= 90.
# Nothing here is Claude-sourced; all numbers are authoritative.


# ---- Thresholds (tuned for Indian equities) -------------------------------

FUND_MAX_PE = 50.0
FUND_MAX_DE = 1.0
FUND_MIN_ROE = 12.0
FUND_MIN_REV_GROWTH = 10.0      # percent YoY
FUND_MIN_PROFIT_GROWTH = 0.0    # percent YoY
FUND_MIN_PROMOTER = 40.0        # percent
FUND_MIN_FII_DII = 15.0         # percent combined
FUND_MAX_PROMOTER_PLEDGE = 5.0  # percent; soft gate when unknown

BT_MIN_TRADES = 10
BT_MIN_WIN_RATE = 55.0          # percent
BT_MIN_PROFIT_FACTOR = 1.5
BT_MIN_AVG_RETURN = 2.0         # percent
BT_FORWARD_DAYS = 20
BT_HISTORY_DAYS = 750           # ~3y

COMBINED_MIN_SCORE = 90
COMBINED_WEIGHTS = {
    "technical": 40,
    "fundamentals": 25,
    "backtest": 20,
    "risk_reward": 15,
}


# ---- Fundamentals fetcher -------------------------------------------------


_fundamentals_cache: dict[str, tuple[float, "FundamentalsSnapshot"]] = {}
_fundamentals_cache_ttl = 3600  # 1 hour — fundamentals change slowly


@dataclass
class FundamentalsSnapshot:
    symbol: str
    pe_ratio: Optional[float] = None
    debt_to_equity: Optional[float] = None
    roe: Optional[float] = None  # percent
    revenue_growth: Optional[float] = None  # percent YoY
    profit_growth: Optional[float] = None  # percent YoY
    book_value: Optional[float] = None
    market_cap: Optional[float] = None
    promoter_holding: Optional[float] = None  # percent
    fii_holding: Optional[float] = None
    dii_holding: Optional[float] = None
    promoter_pledge: Optional[float] = None
    sector: str = ""
    industry: str = ""
    currency: str = "INR"
    sources: list[str] = field(default_factory=list)


def _to_float(value: Any) -> Optional[float]:
    try:
        if value is None:
            return None
        f = float(value)
        if not (f == f):  # NaN check
            return None
        return f
    except (TypeError, ValueError):
        return None


async def _fetch_twelvedata_statistics(symbol: str) -> dict[str, Any]:
    """Fetch fundamentals from TwelveData /statistics endpoint.
    Returns normalised dict; all values None on failure.
    TwelveData JSON schema: data.statistics.{valuations_metrics, financials, stock_statistics}
    """
    if not TWELVEDATA_API_KEY:
        return {}
    td_symbol = _twelvedata_symbol(symbol)
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.get(
                "https://api.twelvedata.com/statistics",
                params={"symbol": td_symbol, "apikey": TWELVEDATA_API_KEY},
            )
            if r.status_code != 200:
                logger.debug("TwelveData statistics %s → HTTP %s", td_symbol, r.status_code)
                return {}
            data = r.json()
            if data.get("status") == "error" or "statistics" not in data:
                logger.debug("TwelveData statistics %s error: %s", td_symbol, data.get("message"))
                return {}
            st    = data["statistics"]
            val   = st.get("valuations_metrics", {}) or {}
            fin   = st.get("financials", {}) or {}
            stock = st.get("stock_statistics", {}) or {}

            pe    = _to_float(val.get("trailing_pe") or val.get("forward_pe"))
            mcap  = _to_float(val.get("market_capitalization"))
            # book_value_per_share_mrq lives in financials; eps_ttm in stock_statistics
            bv    = _to_float(fin.get("book_value_per_share_mrq") or stock.get("book_value_per_share"))
            eps   = _to_float(stock.get("eps_ttm") or fin.get("diluted_eps_ttm"))
            de    = _to_float(fin.get("total_debt_to_equity_mrq"))
            roe   = _to_float(fin.get("return_on_equity_ttm"))
            rev_g = _to_float(fin.get("quarterly_revenue_growth_yoy"))
            ni_g  = _to_float(fin.get("quarterly_earnings_growth_yoy"))
            # insider / institution holding proxies
            insider_pct = _to_float(stock.get("percent_held_by_insiders"))
            inst_pct    = _to_float(stock.get("percent_held_by_institutions"))

            # normalise fractions → percent where needed
            if roe is not None and -2 <= roe <= 2:
                roe *= 100
            if rev_g is not None and -2 <= rev_g <= 2:
                rev_g *= 100
            if ni_g is not None and -2 <= ni_g <= 2:
                ni_g *= 100
            if insider_pct is not None and 0 <= insider_pct <= 1:
                insider_pct *= 100
            if inst_pct is not None and 0 <= inst_pct <= 1:
                inst_pct *= 100

            result = {
                "pe_ratio": pe, "debt_to_equity": de, "roe": roe,
                "revenue_growth": rev_g, "profit_growth": ni_g,
                "book_value": bv, "market_cap": mcap, "eps": eps,
                "insider_pct": insider_pct, "institutions_pct": inst_pct,
            }
            populated = sum(1 for v in result.values() if v is not None)
            logger.debug("TwelveData statistics %s → %d fields populated", td_symbol, populated)
            return result
    except Exception as exc:
        logger.debug("TwelveData statistics failed for %s: %s", symbol, exc)
        return {}


async def _fetch_alphavantage_overview(symbol: str) -> dict[str, Any]:
    """Fetch fundamentals from Alpha Vantage OVERVIEW endpoint.
    For Indian stocks uses {SYMBOL}.BSE which has the best AV coverage.
    Returns normalised dict; all values None on failure.
    """
    if not ALPHA_VANTAGE_API_KEY:
        return {}
    # Alpha Vantage India coverage: try BSE-suffixed symbol first, then plain
    for av_symbol in (f"{symbol.upper()}.BSE", symbol.upper()):
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.get(
                    "https://www.alphavantage.co/query",
                    params={
                        "function": "OVERVIEW",
                        "symbol": av_symbol,
                        "apikey": ALPHA_VANTAGE_API_KEY,
                    },
                )
                if r.status_code != 200:
                    continue
                data = r.json()
                if "Symbol" not in data or not data.get("Name"):
                    # Empty response or rate-limit note
                    logger.debug("AlphaVantage OVERVIEW %s: no data — %s", av_symbol, str(data)[:80])
                    continue

                def av(key: str) -> Optional[float]:
                    v = data.get(key)
                    if v in (None, "None", "N/A", "-", ""):
                        return None
                    return _to_float(v)

                # Verified AV OVERVIEW field names (as of 2024-25)
                pe    = av("PERatio") or av("ForwardPE")
                roe   = av("ReturnOnEquityTTM")
                rev_g = av("QuarterlyRevenueGrowthYOY")
                ni_g  = av("QuarterlyEarningsGrowthYOY")
                bv    = av("BookValue")
                mcap  = av("MarketCapitalization")
                # AV does not expose D/E directly; will be None → fall to yfinance
                de    = None
                sector   = data.get("Sector") or ""
                industry = data.get("Industry") or ""

                if roe is not None and -2 <= roe <= 2:
                    roe *= 100
                if rev_g is not None and -2 <= rev_g <= 2:
                    rev_g *= 100
                if ni_g is not None and -2 <= ni_g <= 2:
                    ni_g *= 100

                result = {
                    "pe_ratio": pe, "debt_to_equity": de, "roe": roe,
                    "revenue_growth": rev_g, "profit_growth": ni_g,
                    "book_value": bv, "market_cap": mcap,
                    "sector": sector, "industry": industry,
                }
                populated = sum(1 for v in result.values() if v is not None)
                logger.debug("AlphaVantage OVERVIEW %s → %d fields populated", av_symbol, populated)
                return result
        except Exception as exc:
            logger.debug("AlphaVantage OVERVIEW failed for %s: %s", av_symbol, exc)
            continue
    return {}


def _fetch_yahoo_fundamentals_sync(yahoo_symbol: str) -> dict[str, Any]:
    """Best-effort Yahoo Finance fundamentals via yfinance. Returns a dict of raw values.

    Strategy:
    1. ticker.fast_info  — lightweight, not rate-limited: market_cap, shares, last_price
    2. ticker.info       — may be rate-limited; used for pe_ratio, book_value, sector
    3. ticker.financials / balance_sheet — derive any still-missing metrics
    """
    import yfinance as yf

    ticker = yf.Ticker(yahoo_symbol)

    # ---- Step 1: fast_info (reliable even under rate limiting) ----------------
    fast_market_cap: Optional[float] = None
    fast_shares:     Optional[float] = None
    fast_price:      Optional[float] = None
    try:
        fi = ticker.fast_info
        fast_market_cap = _to_float(getattr(fi, "market_cap", None))
        fast_shares     = _to_float(getattr(fi, "shares", None))
        fast_price      = _to_float(getattr(fi, "last_price", None))
    except Exception as exc:
        logger.debug("yfinance fast_info failed for %s: %s", yahoo_symbol, exc)

    # ---- Step 2: .info (best-effort; may be empty if rate-limited) -----------
    info: dict[str, Any] = {}
    try:
        info = ticker.info or {}
    except Exception as exc:
        logger.debug("yfinance info failed for %s: %s", yahoo_symbol, exc)

    def _pick(*keys: str) -> Optional[float]:
        for k in keys:
            v = _to_float(info.get(k))
            if v is not None:
                return v
        return None

    rev_growth    = _pick("revenueGrowth", "revenueQuarterlyGrowth")
    profit_growth = _pick("earningsGrowth", "earningsQuarterlyGrowth")
    debt_to_equity= _pick("debtToEquity")
    roe           = _pick("returnOnEquity")
    pe_ratio      = _pick("trailingPE", "forwardPE")
    book_value    = _pick("bookValue")
    market_cap    = _pick("marketCap") or fast_market_cap   # fast_info as fallback
    insider_pct   = _pick("heldPercentInsiders")
    inst_pct      = _pick("heldPercentInstitutions")

    # normalise fractions → percent
    if rev_growth is not None and -2 <= rev_growth <= 2:
        rev_growth *= 100
    if profit_growth is not None and -2 <= profit_growth <= 2:
        profit_growth *= 100
    if roe is not None and -2 <= roe <= 2:
        roe *= 100
    if insider_pct is not None and 0 <= insider_pct <= 1:
        insider_pct *= 100
    if inst_pct is not None and 0 <= inst_pct <= 1:
        inst_pct *= 100
    if debt_to_equity is not None and debt_to_equity > 10:
        debt_to_equity = debt_to_equity / 100

    # ---- Step 3: derive from financial statements (most reliable fallback) ---
    try:
        fin = ticker.financials      # annual income statement
        bs  = ticker.balance_sheet   # annual balance sheet

        has_fin = fin is not None and not fin.empty
        has_bs  = bs  is not None and not bs.empty

        # Helper: latest value for a row, trying multiple name aliases
        def _bs_val(*row_names: str) -> Optional[float]:
            if not has_bs:
                return None
            for rn in row_names:
                if rn in bs.index:
                    return _to_float(bs.loc[rn].iloc[0])
            return None

        def _fin_val(*row_names: str) -> Optional[float]:
            if not has_fin:
                return None
            for rn in row_names:
                if rn in fin.index:
                    return _to_float(fin.loc[rn].iloc[0])
            return None

        equity = _bs_val(
            "Stockholders Equity", "Total Stockholder Equity",
            "Total Equity Gross Minority Interest", "Common Stock Equity",
        )
        net_income = _fin_val("Net Income", "Net Income Common Stockholders")
        total_debt = _bs_val("Total Debt", "Long Term Debt", "Short Long Term Debt")
        shares_out = (
            fast_shares
            or _to_float(info.get("sharesOutstanding") or info.get("impliedSharesOutstanding"))
        )
        price = fast_price or _to_float(
            info.get("currentPrice") or info.get("regularMarketPrice")
        )

        # ROE
        if roe is None and net_income and equity and equity > 0:
            roe = net_income / equity * 100

        # Revenue growth (YoY from annual)
        if rev_growth is None and has_fin and "Total Revenue" in fin.index and fin.shape[1] >= 2:
            r_new = _to_float(fin.loc["Total Revenue"].iloc[0])
            r_old = _to_float(fin.loc["Total Revenue"].iloc[1])
            if r_new and r_old and r_old > 0:
                rev_growth = (r_new - r_old) / r_old * 100

        # Profit growth (YoY from annual)
        if profit_growth is None and has_fin and "Net Income" in fin.index and fin.shape[1] >= 2:
            n_new = _to_float(fin.loc["Net Income"].iloc[0])
            n_old = _to_float(fin.loc["Net Income"].iloc[1])
            if n_new and n_old and n_old > 0:
                profit_growth = (n_new - n_old) / n_old * 100

        # D/E from balance sheet
        if debt_to_equity is None and total_debt is not None and equity and equity > 0:
            debt_to_equity = total_debt / equity

        # Market cap: fast_info first, then price × shares
        if market_cap is None and price and shares_out:
            market_cap = price * shares_out

        # Book value per share from equity / shares
        if book_value is None and equity and shares_out and shares_out > 0:
            book_value = equity / shares_out

        # P/E from price / (net_income / shares)
        if pe_ratio is None:
            # Try trailingEps from info first
            eps = _to_float(info.get("trailingEps") or info.get("epsTrailingTwelveMonths"))
            # Derive EPS from financials if missing
            if eps is None and net_income and shares_out and shares_out > 0:
                eps = net_income / shares_out
            if eps and eps > 0 and price:
                pe_ratio = price / eps

    except Exception as exc:
        logger.debug("Fundamentals financials fallback failed for %s: %s", yahoo_symbol, exc)

    return {
        "pe_ratio": pe_ratio,
        "debt_to_equity": debt_to_equity,
        "roe": roe,
        "revenue_growth": rev_growth,
        "profit_growth": profit_growth,
        "book_value": book_value,
        "market_cap": market_cap,
        "sector": info.get("sector") or "",
        "industry": info.get("industry") or "",
        "currency": info.get("currency") or "INR",
        "insider_pct": insider_pct,  # proxy for promoter holding (Indian equities)
        "institutions_pct": inst_pct,  # proxy for FII+DII combined
    }


# NSE cookie jar / session reused across calls within a process.
_nse_client_lock = asyncio.Lock()
_nse_cookies_ready = False


async def _prime_nse_cookies(client: httpx.AsyncClient) -> None:
    """NSE requires a warm cookie from the homepage before /api/* calls work."""
    global _nse_cookies_ready
    if _nse_cookies_ready:
        return
    try:
        await client.get("https://www.nseindia.com/", timeout=5)
        await client.get(
            "https://www.nseindia.com/market-data/live-equity-market", timeout=5
        )
        _nse_cookies_ready = True
    except Exception as exc:
        logger.debug("NSE cookie warm-up failed: %s", exc)


async def _fetch_nse_shareholding(symbol: str) -> dict[str, Optional[float]]:
    """Pull shareholding pattern (promoter / FII / DII / pledge) from NSE.
    Best-effort — returns all-None if the network / corp-proxy blocks it.
    """
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://www.nseindia.com/",
    }
    result: dict[str, Optional[float]] = {
        "promoter_holding": None,
        "fii_holding": None,
        "dii_holding": None,
        "promoter_pledge": None,
    }
    async with _nse_client_lock:
        async with httpx.AsyncClient(
            headers=headers, timeout=6, follow_redirects=True
        ) as client:
            await _prime_nse_cookies(client)
            try:
                r = await client.get(
                    "https://www.nseindia.com/api/quote-equity",
                    params={"symbol": symbol.upper()},
                    timeout=6,
                )
                if r.status_code != 200:
                    return result
                payload = r.json()
            except Exception as exc:
                logger.debug("NSE quote-equity failed for %s: %s", symbol, exc)
                return result

    # Shareholding fields — NSE schema varies; be defensive.
    sh = payload.get("securityInfo", {}) or {}
    holdings = payload.get("shareholdingPattern", {}) or {}

    promoter = _to_float(
        holdings.get("promoterAndPromoterGroup")
        or sh.get("promoterAndPromoterGroup")
        or holdings.get("promoter")
    )
    fii = _to_float(
        holdings.get("foreignInstitutions")
        or holdings.get("foreignPortfolioInvestors")
        or holdings.get("fii")
    )
    dii = _to_float(
        holdings.get("domesticInstitutions")
        or holdings.get("mutualFunds")
        or holdings.get("dii")
    )
    pledge = _to_float(sh.get("pledgeOfShare") or sh.get("pledge"))

    result.update(
        promoter_holding=promoter,
        fii_holding=fii,
        dii_holding=dii,
        promoter_pledge=pledge,
    )
    return result


async def fetch_fundamentals(symbol: str, exchange: str = "NSE") -> FundamentalsSnapshot:
    cached = _cache_get(_fundamentals_cache, symbol.upper(), _fundamentals_cache_ttl)
    if cached is not None:
        return cached

    yahoo_symbol = _normalize_yahoo_symbol(symbol, exchange)

    # Run all four sources in parallel: TwelveData, AlphaVantage, Yahoo, NSE shareholding
    td_task    = _fetch_twelvedata_statistics(symbol)
    av_task    = _fetch_alphavantage_overview(symbol)
    yahoo_task = asyncio.to_thread(_fetch_yahoo_fundamentals_sync, yahoo_symbol)
    nse_task   = _fetch_nse_shareholding(symbol) if exchange.upper() == "NSE" else asyncio.sleep(0, result={})

    td_data, av_data, yahoo_data, nse_data = await asyncio.gather(
        td_task, av_task, yahoo_task, nse_task, return_exceptions=True
    )
    if isinstance(td_data, Exception):
        logger.debug("TwelveData fundamentals raised: %s", td_data); td_data = {}
    if isinstance(av_data, Exception):
        logger.debug("AlphaVantage fundamentals raised: %s", av_data); av_data = {}
    if isinstance(yahoo_data, Exception):
        logger.debug("Yahoo fundamentals raised: %s", yahoo_data); yahoo_data = {}
    if isinstance(nse_data, Exception):
        logger.debug("NSE shareholding raised: %s", nse_data); nse_data = {}

    # Merge: TwelveData → AlphaVantage → Yahoo (first non-None wins per field)
    def _first(*vals: Any) -> Optional[float]:
        for v in vals:
            if v is not None:
                f = _to_float(v)
                if f is not None:
                    return f
        return None

    sources: list[str] = []
    if any(v is not None for v in td_data.values()):
        sources.append("twelvedata")
    if any(v is not None for v in av_data.values() if v not in ("", None)):
        sources.append("alphavantage")
    if yahoo_data:
        sources.append("yahoo")

    pe_ratio      = _first(td_data.get("pe_ratio"),      av_data.get("pe_ratio"),      yahoo_data.get("pe_ratio"))
    debt_to_equity= _first(td_data.get("debt_to_equity"),av_data.get("debt_to_equity"),yahoo_data.get("debt_to_equity"))
    roe           = _first(td_data.get("roe"),            av_data.get("roe"),            yahoo_data.get("roe"))
    revenue_growth= _first(td_data.get("revenue_growth"),av_data.get("revenue_growth"),yahoo_data.get("revenue_growth"))
    profit_growth = _first(td_data.get("profit_growth"), av_data.get("profit_growth"), yahoo_data.get("profit_growth"))
    book_value    = _first(td_data.get("book_value"),     av_data.get("book_value"),     yahoo_data.get("book_value"))
    market_cap    = _first(td_data.get("market_cap"),     av_data.get("market_cap"),     yahoo_data.get("market_cap"))
    sector        = av_data.get("sector") or yahoo_data.get("sector") or SECTOR_MAP.get(symbol.upper(), "")
    industry      = av_data.get("industry") or yahoo_data.get("industry") or ""

    # Shareholding: NSE API → TwelveData insiders proxy → Yahoo insider proxy
    promoter_holding = nse_data.get("promoter_holding")
    fii              = nse_data.get("fii_holding")
    dii              = nse_data.get("dii_holding")
    promoter_pledge  = nse_data.get("promoter_pledge")
    nse_available    = any(v is not None for v in nse_data.values())
    if nse_available:
        sources.append("nse")

    # Fallback promoter proxy: TwelveData insider % → Yahoo insider %
    if promoter_holding is None:
        for proxy_src, proxy_data, proxy_label in [
            ("td_insider_proxy", td_data, "twelvedata-insider-proxy"),
            ("yf_insider_proxy", yahoo_data, "yahoo-insider-proxy"),
        ]:
            if proxy_data.get("insider_pct") is not None:
                promoter_holding = proxy_data["insider_pct"]
                sources.append(proxy_label)
                break

    # Fallback FII+DII proxy: TwelveData institutions % → Yahoo institutions %
    if fii is None and dii is None:
        for proxy_data, proxy_label in [
            (td_data, "twelvedata-institutions-proxy"),
            (yahoo_data, "yahoo-institutions-proxy"),
        ]:
            if proxy_data.get("institutions_pct") is not None:
                combined = proxy_data["institutions_pct"]
                fii = combined / 2
                dii = combined / 2
                sources.append(proxy_label)
                break

    logger.info(
        "Fundamentals %s: pe=%s d/e=%s roe=%s rev_g=%s sources=%s",
        symbol.upper(), pe_ratio, debt_to_equity, roe, revenue_growth, sources,
    )

    snap = FundamentalsSnapshot(
        symbol=symbol.upper(),
        pe_ratio=pe_ratio,
        debt_to_equity=debt_to_equity,
        roe=roe,
        revenue_growth=revenue_growth,
        profit_growth=profit_growth,
        book_value=book_value,
        market_cap=market_cap,
        promoter_holding=promoter_holding,
        fii_holding=fii,
        dii_holding=dii,
        promoter_pledge=promoter_pledge,
        sector=sector,
        industry=industry,
        currency=yahoo_data.get("currency", "INR"),
        sources=sources,
    )
    _cache_set(_fundamentals_cache, symbol.upper(), snap)
    return snap


# ---- Fundamentals gate ---------------------------------------------------


def evaluate_fundamentals(snap: FundamentalsSnapshot) -> dict[str, Any]:
    checks: list[dict[str, Any]] = []

    def check(name: str, passed: Optional[bool], detail: str, weight: int, hard: bool = True):
        checks.append({
            "name": name,
            "passed": bool(passed) if passed is not None else None,
            "detail": detail,
            "weight": weight,
            "hard": hard,
        })

    pe = snap.pe_ratio
    check(
        "pe_le_50",
        (pe is not None and pe <= FUND_MAX_PE),
        f"P/E {pe:.2f} (≤ {FUND_MAX_PE})" if pe is not None else "P/E unavailable",
        4,
    )
    de = snap.debt_to_equity
    check(
        "debt_le_1",
        (de is not None and de <= FUND_MAX_DE),
        f"D/E {de:.2f} (≤ {FUND_MAX_DE})" if de is not None else "D/E unavailable",
        4,
    )
    roe = snap.roe
    check(
        "roe_ge_12",
        (roe is not None and roe >= FUND_MIN_ROE),
        f"ROE {roe:.2f}% (≥ {FUND_MIN_ROE}%)" if roe is not None else "ROE unavailable",
        4,
    )
    rg = snap.revenue_growth
    check(
        "rev_growth_ge_10",
        (rg is not None and rg >= FUND_MIN_REV_GROWTH),
        f"Revenue growth {rg:.2f}% (≥ {FUND_MIN_REV_GROWTH}%)" if rg is not None else "Revenue growth unavailable",
        3,
    )
    pg = snap.profit_growth
    check(
        "profit_growth_pos",
        (pg is not None and pg > FUND_MIN_PROFIT_GROWTH),
        f"Profit growth {pg:.2f}% (> 0)" if pg is not None else "Profit growth unavailable",
        3,
    )
    pr = snap.promoter_holding
    check(
        "promoter_ge_40",
        (pr is not None and pr >= FUND_MIN_PROMOTER),
        f"Promoter {pr:.2f}% (≥ {FUND_MIN_PROMOTER}%)" if pr is not None else "Promoter holding unavailable",
        4,
    )
    fii_dii = (snap.fii_holding or 0) + (snap.dii_holding or 0) if (snap.fii_holding is not None or snap.dii_holding is not None) else None
    check(
        "fii_dii_ge_15",
        (fii_dii is not None and fii_dii >= FUND_MIN_FII_DII),
        f"FII+DII {fii_dii:.2f}% (≥ {FUND_MIN_FII_DII}%)" if fii_dii is not None else "FII/DII unavailable",
        2,
    )
    pledge = snap.promoter_pledge
    check(
        "promoter_pledge_low",
        (pledge is None or pledge <= FUND_MAX_PROMOTER_PLEDGE),
        f"Promoter pledge {pledge:.2f}% (≤ {FUND_MAX_PROMOTER_PLEDGE}%)" if pledge is not None else "No pledge data (treated as clean)",
        1,
        hard=False,
    )

    hard_checks = [c for c in checks if c["hard"]]
    hard_passed = all(c["passed"] is True for c in hard_checks)
    raw = sum(c["weight"] for c in checks if c["passed"] is True)
    max_raw = sum(c["weight"] for c in checks)
    score = round((raw / max_raw) * COMBINED_WEIGHTS["fundamentals"]) if max_raw else 0

    return {
        "passed": hard_passed,
        "score": score,
        "max_score": COMBINED_WEIGHTS["fundamentals"],
        "checks": checks,
        "raw_passed": raw,
        "raw_max": max_raw,
    }


# ---- Backtest engine -----------------------------------------------------


@dataclass
class BacktestMetrics:
    num_trades: int
    wins: int
    losses: int
    win_rate: float
    profit_factor: float
    avg_return: float
    avg_win: float
    avg_loss: float
    best_trade: float
    worst_trade: float
    sharpe: float


def _entry_condition(ind: dict[str, Any], patterns: dict[str, Any], smart: dict[str, Any]) -> bool:
    """The same 7 hard conditions the live scanner uses."""
    return (
        ind["close"] > ind["ema_200"]
        and ind["ema_20"] > ind["ema_50"] > ind["ema_200"]
        and patterns["strength"] >= 15
        and ind["volume_ratio"] > 1.5
        and smart["break_of_structure"]
        and 50 <= ind["rsi"] <= 70
        and not smart["strong_resistance_nearby"]
    )


def _compute_indicators_window(df: pd.DataFrame) -> dict[str, Any]:
    """Lightweight indicator snapshot at the LAST bar of `df` (for walk-forward)."""
    close = df["close"]
    ema20 = _ema(close, 20).iloc[-1]
    ema50 = _ema(close, 50).iloc[-1]
    ema200 = _ema(close, 200).iloc[-1]
    rsi = _rsi(close, 14).iloc[-1]
    macd_line, signal_line, hist = _macd(close)
    atr = _atr(df, 14).iloc[-1]
    vol_avg20 = df["volume"].rolling(20).mean().iloc[-1]
    vol = df["volume"].iloc[-1]
    vol_ratio = vol / vol_avg20 if vol_avg20 and vol_avg20 > 0 else 0.0
    return {
        "close": float(close.iloc[-1]),
        "ema_20": float(ema20),
        "ema_50": float(ema50),
        "ema_200": float(ema200),
        "rsi": float(rsi),
        "macd": float(macd_line.iloc[-1]),
        "macd_signal_line": float(signal_line.iloc[-1]),
        "macd_hist": float(hist.iloc[-1]),
        "atr": float(atr),
        "volume_ratio": float(vol_ratio),
    }


def run_symbol_backtest(df: pd.DataFrame, forward_days: int = BT_FORWARD_DAYS) -> BacktestMetrics:
    """Walk-forward backtest on a single symbol. Fires the same entry rule used
    live, holds for `forward_days` bars, records return. Requires ≥ 250 rows.
    """
    if df is None or len(df) < 250:
        return BacktestMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    returns: list[float] = []
    start = 200  # need 200 EMA warmup
    last_signal_idx = -10  # debounce

    for i in range(start, len(df) - forward_days):
        # Need ≥ 3 candles for pattern logic and ≥ 20 for swings; already true when i ≥ 200.
        if i - last_signal_idx < 5:  # no more than one signal per 5 bars
            continue
        window = df.iloc[: i + 1]
        try:
            ind = _compute_indicators_window(window)
            patterns = detect_patterns(window)
            smart = detect_smart_money(window, ind)
        except Exception:
            continue
        if not _entry_condition(ind, patterns, smart):
            continue
        entry = ind["close"]
        exit_price = float(df["close"].iloc[i + forward_days])
        ret_pct = (exit_price - entry) / entry * 100
        returns.append(ret_pct)
        last_signal_idx = i

    n = len(returns)
    if n == 0:
        return BacktestMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    wins_list = [r for r in returns if r > 0]
    losses_list = [r for r in returns if r <= 0]
    gross_profit = sum(wins_list)
    gross_loss = -sum(losses_list)
    win_rate = len(wins_list) / n * 100
    profit_factor = (gross_profit / gross_loss) if gross_loss > 0 else (gross_profit if gross_profit > 0 else 0.0)
    avg_ret = sum(returns) / n
    avg_win = (gross_profit / len(wins_list)) if wins_list else 0.0
    avg_loss = (-gross_loss / len(losses_list)) if losses_list else 0.0
    best = max(returns)
    worst = min(returns)

    arr = np.array(returns)
    sd = float(np.std(arr)) if len(arr) > 1 else 0.0
    # Annualised Sharpe assuming non-overlapping 20-day trades → ~12/yr
    sharpe = (avg_ret / sd * np.sqrt(12)) if sd > 0 else 0.0

    return BacktestMetrics(
        num_trades=n,
        wins=len(wins_list),
        losses=len(losses_list),
        win_rate=round(win_rate, 2),
        profit_factor=round(profit_factor, 2),
        avg_return=round(avg_ret, 2),
        avg_win=round(avg_win, 2),
        avg_loss=round(avg_loss, 2),
        best_trade=round(best, 2),
        worst_trade=round(worst, 2),
        sharpe=round(float(sharpe), 2),
    )


def evaluate_backtest(m: BacktestMetrics) -> dict[str, Any]:
    checks = [
        {"name": "min_trades", "passed": m.num_trades >= BT_MIN_TRADES, "detail": f"{m.num_trades} historical signals (≥ {BT_MIN_TRADES})", "weight": 4},
        {"name": "win_rate",  "passed": m.win_rate >= BT_MIN_WIN_RATE, "detail": f"Win rate {m.win_rate}% (≥ {BT_MIN_WIN_RATE}%)", "weight": 6},
        {"name": "profit_factor", "passed": m.profit_factor >= BT_MIN_PROFIT_FACTOR, "detail": f"Profit factor {m.profit_factor} (≥ {BT_MIN_PROFIT_FACTOR})", "weight": 5},
        {"name": "avg_return", "passed": m.avg_return >= BT_MIN_AVG_RETURN, "detail": f"Avg return {m.avg_return}% (≥ {BT_MIN_AVG_RETURN}%)", "weight": 5},
    ]
    passed = all(c["passed"] for c in checks)
    raw = sum(c["weight"] for c in checks if c["passed"])
    max_raw = sum(c["weight"] for c in checks)
    score = round((raw / max_raw) * COMBINED_WEIGHTS["backtest"]) if max_raw else 0
    return {
        "passed": passed,
        "score": score,
        "max_score": COMBINED_WEIGHTS["backtest"],
        "checks": checks,
        "metrics": {
            "num_trades": m.num_trades,
            "wins": m.wins,
            "losses": m.losses,
            "win_rate": m.win_rate,
            "profit_factor": m.profit_factor,
            "avg_return": m.avg_return,
            "avg_win": m.avg_win,
            "avg_loss": m.avg_loss,
            "best_trade": m.best_trade,
            "worst_trade": m.worst_trade,
            "sharpe": m.sharpe,
        },
    }


# ---- Risk/reward gate ----------------------------------------------------


def evaluate_risk_reward(entry: float, stop: float, target: float, atr: float) -> dict[str, Any]:
    risk = max(entry - stop, 1e-9)
    reward = target - entry
    rr = reward / risk
    risk_pct = (risk / entry) * 100
    reward_pct = (reward / entry) * 100

    checks = [
        {"name": "rr_ge_2", "passed": rr >= 2.0, "detail": f"R/R {rr:.2f} (≥ 2.0)", "weight": 6},
        {"name": "risk_le_3pct", "passed": risk_pct <= 3.0, "detail": f"Risk {risk_pct:.2f}% of price (≤ 3.0%)", "weight": 4},
        {"name": "atr_le_5pct", "passed": (atr / entry * 100) <= 5.0, "detail": f"ATR {atr:.2f} = {atr/entry*100:.2f}% of price (≤ 5.0%)", "weight": 5},
    ]
    passed = all(c["passed"] for c in checks)
    raw = sum(c["weight"] for c in checks if c["passed"])
    max_raw = sum(c["weight"] for c in checks)
    score = round((raw / max_raw) * COMBINED_WEIGHTS["risk_reward"]) if max_raw else 0
    return {
        "passed": passed,
        "score": score,
        "max_score": COMBINED_WEIGHTS["risk_reward"],
        "checks": checks,
        "rr": round(rr, 2),
        "risk_pct": round(risk_pct, 2),
        "reward_pct": round(reward_pct, 2),
    }


# ---- Deep analyzer -------------------------------------------------------


async def _fetch_long_ohlcv(symbol: str, years: int = 3) -> Optional[pd.DataFrame]:
    """Longer OHLCV for the backtest (yfinance — reliable for Indian equities)."""
    yahoo_symbol = _normalize_yahoo_symbol(symbol, "NSE")

    def _fetch() -> Optional[pd.DataFrame]:
        import yfinance as yf

        t = yf.Ticker(yahoo_symbol)
        h = t.history(period=f"{years}y", interval="1d", auto_adjust=False)
        if h is None or h.empty:
            return None
        h = h.rename(columns={"Open": "open", "High": "high", "Low": "low", "Close": "close", "Volume": "volume"})
        h = h[["open", "high", "low", "close", "volume"]].astype(float).dropna()
        return h.reset_index(drop=True) if len(h) else None

    try:
        return await asyncio.to_thread(_fetch)
    except Exception as exc:
        logger.debug("long OHLCV fetch failed for %s: %s", symbol, exc)
        return None


async def deep_analyze(symbol: str, exchange: str = "NSE") -> dict[str, Any]:
    """Full multi-factor analysis: fundamentals + technical + backtest + RR.

    Returns a dict with:
      - overall { passed, score, min_score }
      - gates: fundamentals, technical, backtest, risk_reward (each with checks[])
      - levels: entry, stop, target1, target2, expected_return, rr
      - indicators, patterns, smart_money (existing)
      - fundamentals (snapshot)
      - ai_analysis (GPT-4o or heuristic)
    """
    df = await _fetch_long_ohlcv(symbol, years=3)
    if df is None or len(df) < 220:
        raise HTTPException(
            status_code=404,
            detail=f"Not enough historical data to analyze '{symbol}' (need ≥ 220 daily bars).",
        )

    # Technical snapshot on the most recent bar.
    indicators = compute_indicators(df)
    patterns = detect_patterns(df)
    smart = detect_smart_money(df, indicators)
    tech_scores = score_stock(indicators, patterns, smart)
    tech_hard_pass, tech_failures = hard_conditions(indicators, patterns, smart)
    tech_score_scaled = round(tech_scores["total"] / 100 * COMBINED_WEIGHTS["technical"])
    tech_checks = [
        {"name": "price_above_200ema", "passed": indicators["close"] > indicators["ema_200"], "detail": f"Close {indicators['close']:.2f} vs 200-EMA {indicators['ema_200']:.2f}"},
        {"name": "ema_stacked", "passed": indicators["ema_20"] > indicators["ema_50"] > indicators["ema_200"], "detail": f"20/50/200 = {indicators['ema_20']:.2f} / {indicators['ema_50']:.2f} / {indicators['ema_200']:.2f}"},
        {"name": "pattern_strength_ge_15", "passed": patterns["strength"] >= 15, "detail": f"{patterns['name']} strength {patterns['strength']}"},
        {"name": "volume_gt_1_5x", "passed": indicators["volume_ratio"] > 1.5, "detail": f"Volume {indicators['volume_ratio']:.2f}x 20-day avg"},
        {"name": "break_of_structure", "passed": smart["break_of_structure"], "detail": "Recent higher-high confirmed" if smart["break_of_structure"] else "No BOS"},
        {"name": "rsi_50_70", "passed": 50 <= indicators["rsi"] <= 70, "detail": f"RSI {indicators['rsi']:.1f}"},
        {"name": "no_resistance_within_3pct", "passed": not smart["strong_resistance_nearby"], "detail": "Clear overhead" if not smart["strong_resistance_nearby"] else "Resistance < 3% away"},
    ]
    technical_gate = {
        "passed": tech_hard_pass,
        "score": tech_score_scaled,
        "max_score": COMBINED_WEIGHTS["technical"],
        "checks": tech_checks,
        "scores": tech_scores,
        "failures": tech_failures,
    }

    # Levels & R/R
    entry = indicators["close"]
    atr = max(indicators["atr"], entry * 0.005)
    stop = entry - 1.5 * atr
    target1 = entry + 3.0 * atr
    target2 = entry + 5.0 * atr
    expected_return = (target1 - entry) / entry * 100
    rr = (target1 - entry) / max(entry - stop, 1e-9)
    risk_reward_gate = evaluate_risk_reward(entry, stop, target1, atr)

    # Fundamentals (in parallel with backtest)
    fundamentals_task = fetch_fundamentals(symbol, exchange)
    backtest_task = asyncio.to_thread(run_symbol_backtest, df, BT_FORWARD_DAYS)
    fund_snap, bt_metrics = await asyncio.gather(fundamentals_task, backtest_task)

    fundamentals_gate = evaluate_fundamentals(fund_snap)
    backtest_gate = evaluate_backtest(bt_metrics)

    combined_score = (
        technical_gate["score"]
        + fundamentals_gate["score"]
        + backtest_gate["score"]
        + risk_reward_gate["score"]
    )
    all_passed = (
        technical_gate["passed"]
        and fundamentals_gate["passed"]
        and backtest_gate["passed"]
        and risk_reward_gate["passed"]
    )
    high_probability = all_passed and combined_score >= COMBINED_MIN_SCORE

    # AI narrative
    # Collect all gate failures for honest narrative
    all_failures = (
        technical_gate.get("failures", [])
        + [c["name"] for c in fundamentals_gate.get("checks", []) if not c["passed"]]
        + [c["name"] for c in backtest_gate.get("checks", []) if not c["passed"]]
        + [c["name"] for c in risk_reward_gate.get("checks", []) if not c["passed"]]
    )
    gpt_ctx = {
        "symbol": symbol.upper(),
        "sector": fund_snap.sector or SECTOR_MAP.get(symbol.upper(), "Diversified"),
        "price": entry,
        "entry": entry,
        "stop": stop,
        "target": target1,
        "rr": rr,
        "ret": expected_return,
        "pattern": patterns["name"],
        "rsi": indicators["rsi"],
        "vr": indicators["volume_ratio"],
        "ema20": indicators["ema_20"],
        "ema50": indicators["ema_50"],
        "ema200": indicators["ema_200"],
        "macd_hist": indicators["macd_hist"],
        "bos": smart["break_of_structure"],
        "lg": smart["liquidity_grab"],
        "vol_acc": smart["volume_accumulation"],
        "breakdown": tech_scores,
        "failures": all_failures,
        "combined_score": combined_score,
    }
    try:
        ai_text = await generate_gpt_analysis(gpt_ctx)
    except Exception:
        ai_text = _heuristic_narrative(gpt_ctx)

    return {
        "symbol": symbol.upper(),
        "sector": fund_snap.sector or SECTOR_MAP.get(symbol.upper(), "Diversified"),
        "industry": fund_snap.industry,
        "scan_timestamp": datetime.now(timezone.utc).isoformat(),
        "overall": {
            "all_gates_passed": all_passed,
            "high_probability": high_probability,
            "combined_score": combined_score,
            "min_score": COMBINED_MIN_SCORE,
            "weights": COMBINED_WEIGHTS,
        },
        "levels": {
            "entry": round(entry, 2),
            "stop": round(stop, 2),
            "target_1": round(target1, 2),
            "target_2": round(target2, 2),
            "atr": round(atr, 2),
            "expected_return_pct": round(expected_return, 2),
            "risk_pct": round((entry - stop) / entry * 100, 2),
            "risk_reward": round(rr, 2),
        },
        "gates": {
            "technical": technical_gate,
            "fundamentals": fundamentals_gate,
            "backtest": backtest_gate,
            "risk_reward": risk_reward_gate,
        },
        "indicators": {
            "ema_20": round(indicators["ema_20"], 2),
            "ema_50": round(indicators["ema_50"], 2),
            "ema_200": round(indicators["ema_200"], 2),
            "rsi": round(indicators["rsi"], 2),
            "macd": round(indicators["macd"], 4),
            "macd_signal": round(indicators["macd_signal_line"], 4),
            "macd_hist": round(indicators["macd_hist"], 4),
            "vwap": round(indicators["vwap"], 2),
            "atr": round(indicators["atr"], 2),
            "volume_ratio": round(indicators["volume_ratio"], 2),
        },
        "patterns": patterns,
        "smart_money": smart,
        "fundamentals_snapshot": {
            "pe_ratio": fund_snap.pe_ratio,
            "debt_to_equity": fund_snap.debt_to_equity,
            "roe": fund_snap.roe,
            "revenue_growth": fund_snap.revenue_growth,
            "profit_growth": fund_snap.profit_growth,
            "book_value": fund_snap.book_value,
            "market_cap": fund_snap.market_cap,
            "promoter_holding": fund_snap.promoter_holding,
            "fii_holding": fund_snap.fii_holding,
            "dii_holding": fund_snap.dii_holding,
            "promoter_pledge": fund_snap.promoter_pledge,
            "currency": fund_snap.currency,
            "sources": fund_snap.sources,
        },
        "ai_analysis": ai_text,
    }


# ===========================================================================
# PERSISTENT HISTORY — every /deep-analyze run is saved to SQLite so users
# can re-view any prior analysis with its original date/time and full payload.
# ===========================================================================


DB_PATH = Path(__file__).resolve().parent / "data" / "quantedge.db"
DB_PATH.parent.mkdir(parents=True, exist_ok=True)

_db_lock = threading.Lock()  # single SQLite writer; reads are safe concurrently


def _db_connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=10.0, isolation_level=None)
    conn.row_factory = sqlite3.Row
    # WAL keeps readers unblocked while writers commit; good default for a single-file DB.
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    return conn


def _init_history_schema() -> None:
    with _db_lock, _db_connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS deep_analysis_history (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol           TEXT    NOT NULL,
                exchange         TEXT    NOT NULL DEFAULT 'NSE',
                sector           TEXT,
                industry         TEXT,
                analyzed_at      TEXT    NOT NULL,
                combined_score   INTEGER NOT NULL,
                min_score        INTEGER NOT NULL,
                high_probability INTEGER NOT NULL,
                all_gates_passed INTEGER NOT NULL,
                technical_score   INTEGER NOT NULL,
                fundamentals_score INTEGER NOT NULL,
                backtest_score   INTEGER NOT NULL,
                risk_reward_score INTEGER NOT NULL,
                entry_price      REAL,
                stop_loss        REAL,
                target_1         REAL,
                target_2         REAL,
                risk_reward      REAL,
                expected_return  REAL,
                pattern_detected TEXT,
                rsi              REAL,
                payload          TEXT    NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_history_symbol ON deep_analysis_history(symbol)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_history_analyzed_at ON deep_analysis_history(analyzed_at DESC)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_history_high_prob ON deep_analysis_history(high_probability)"
        )


_init_history_schema()


def _history_save_sync(payload: dict[str, Any]) -> int:
    """Insert a single deep-analyze result; return the new row id."""
    symbol = payload.get("symbol", "").upper()
    overall = payload.get("overall", {}) or {}
    gates = payload.get("gates", {}) or {}
    levels = payload.get("levels", {}) or {}
    patterns = payload.get("patterns", {}) or {}
    indicators = payload.get("indicators", {}) or {}

    row = (
        symbol,
        "NSE",
        payload.get("sector", "") or "",
        payload.get("industry", "") or "",
        payload.get("scan_timestamp") or datetime.now(timezone.utc).isoformat(),
        int(overall.get("combined_score", 0) or 0),
        int(overall.get("min_score", COMBINED_MIN_SCORE) or COMBINED_MIN_SCORE),
        1 if overall.get("high_probability") else 0,
        1 if overall.get("all_gates_passed") else 0,
        int((gates.get("technical") or {}).get("score", 0) or 0),
        int((gates.get("fundamentals") or {}).get("score", 0) or 0),
        int((gates.get("backtest") or {}).get("score", 0) or 0),
        int((gates.get("risk_reward") or {}).get("score", 0) or 0),
        levels.get("entry"),
        levels.get("stop"),
        levels.get("target_1"),
        levels.get("target_2"),
        levels.get("risk_reward"),
        levels.get("expected_return_pct"),
        patterns.get("name") or "None",
        indicators.get("rsi"),
        json.dumps(payload, default=str),
    )
    with _db_lock, _db_connect() as conn:
        cursor = conn.execute(
            """
            INSERT INTO deep_analysis_history (
                symbol, exchange, sector, industry, analyzed_at,
                combined_score, min_score, high_probability, all_gates_passed,
                technical_score, fundamentals_score, backtest_score, risk_reward_score,
                entry_price, stop_loss, target_1, target_2, risk_reward, expected_return,
                pattern_detected, rsi, payload
            ) VALUES (?,?,?,?,?, ?,?,?,?, ?,?,?,?, ?,?,?,?,?,?, ?,?,?)
            """,
            row,
        )
        return int(cursor.lastrowid or 0)


async def save_history(payload: dict[str, Any]) -> int:
    return await asyncio.to_thread(_history_save_sync, payload)


def _history_list_sync(
    limit: int = 50,
    offset: int = 0,
    symbol: Optional[str] = None,
    high_probability_only: bool = False,
) -> dict[str, Any]:
    limit = max(1, min(int(limit), 500))
    offset = max(0, int(offset))
    clauses: list[str] = []
    params: list[Any] = []
    if symbol:
        clauses.append("symbol = ?")
        params.append(symbol.strip().upper())
    if high_probability_only:
        clauses.append("high_probability = 1")
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""

    with _db_connect() as conn:
        total_row = conn.execute(
            f"SELECT COUNT(*) AS n FROM deep_analysis_history {where}", params
        ).fetchone()
        total = int(total_row["n"]) if total_row else 0
        rows = conn.execute(
            f"""
            SELECT id, symbol, exchange, sector, industry, analyzed_at,
                   combined_score, min_score, high_probability, all_gates_passed,
                   technical_score, fundamentals_score, backtest_score, risk_reward_score,
                   entry_price, stop_loss, target_1, target_2, risk_reward,
                   expected_return, pattern_detected, rsi
            FROM deep_analysis_history
            {where}
            ORDER BY analyzed_at DESC, id DESC
            LIMIT ? OFFSET ?
            """,
            [*params, limit, offset],
        ).fetchall()

    items = [
        {
            **dict(row),
            "high_probability": bool(row["high_probability"]),
            "all_gates_passed": bool(row["all_gates_passed"]),
        }
        for row in rows
    ]
    return {"total": total, "limit": limit, "offset": offset, "items": items}


def _history_get_sync(item_id: int) -> Optional[dict[str, Any]]:
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT * FROM deep_analysis_history WHERE id = ?", (int(item_id),)
        ).fetchone()
    if row is None:
        return None
    payload_json = row["payload"]
    try:
        payload = json.loads(payload_json)
    except Exception:
        payload = {"_parse_error": True, "raw": payload_json}
    return {
        "id": row["id"],
        "symbol": row["symbol"],
        "exchange": row["exchange"],
        "analyzed_at": row["analyzed_at"],
        "combined_score": row["combined_score"],
        "high_probability": bool(row["high_probability"]),
        "all_gates_passed": bool(row["all_gates_passed"]),
        "payload": payload,
    }


def _history_delete_sync(item_id: int) -> bool:
    with _db_lock, _db_connect() as conn:
        cursor = conn.execute(
            "DELETE FROM deep_analysis_history WHERE id = ?", (int(item_id),)
        )
        return cursor.rowcount > 0


def _history_clear_sync(symbol: Optional[str] = None) -> int:
    with _db_lock, _db_connect() as conn:
        if symbol:
            cursor = conn.execute(
                "DELETE FROM deep_analysis_history WHERE symbol = ?", (symbol.upper(),)
            )
        else:
            cursor = conn.execute("DELETE FROM deep_analysis_history")
        return int(cursor.rowcount)


# ===========================================================================
# PAPER TRADING ENGINE
# ===========================================================================
#
# In-process paper-trading desk. Every Deep Analyze with combined_score above
# AUTO_PAPER_TRADE_THRESHOLD opens a paper BUY at the recommended entry/stop/
# target. A background monitor marks positions SL_HIT / TARGET_HIT / TIME_EXIT
# based on the live Yahoo price. All state is persisted in the same SQLite DB
# so restarts are safe.


PAPER_CAPITAL_INR: float = float(os.getenv("PAPER_CAPITAL", "1000000"))  # ₹10L default
PAPER_RISK_PER_TRADE_PCT: float = float(os.getenv("PAPER_RISK_PCT", "2.0"))
PAPER_MAX_OPEN_POSITIONS: int = int(os.getenv("PAPER_MAX_OPEN", "5"))
PAPER_MAX_HOLD_DAYS: int = int(os.getenv("PAPER_MAX_HOLD_DAYS", "20"))
PAPER_MONITOR_INTERVAL_SECONDS: int = int(os.getenv("PAPER_MONITOR_INTERVAL", "10"))

AUTO_PAPER_TRADE_ENABLED: bool = os.getenv("AUTO_PAPER_TRADE_ENABLED", "true").lower() in {"1", "true", "yes"}
AUTO_PAPER_TRADE_THRESHOLD: int = int(os.getenv("AUTO_PAPER_TRADE_THRESHOLD", "70"))
AUTO_TRADE_MARKET_OPEN_ONLY: bool = os.getenv("AUTO_TRADE_MARKET_OPEN_ONLY", "false").lower() in {"1", "true", "yes"}

# Mutable runtime settings (can be flipped at runtime via /paper-settings).
_paper_settings_lock = threading.Lock()
_paper_settings: dict[str, Any] = {
    "auto_trade_enabled": AUTO_PAPER_TRADE_ENABLED,
    "auto_trade_threshold": AUTO_PAPER_TRADE_THRESHOLD,
    "auto_trade_market_open_only": AUTO_TRADE_MARKET_OPEN_ONLY,
    "starting_capital": PAPER_CAPITAL_INR,
    "risk_per_trade_pct": PAPER_RISK_PER_TRADE_PCT,
    "max_open_positions": PAPER_MAX_OPEN_POSITIONS,
    "max_hold_days": PAPER_MAX_HOLD_DAYS,
    "telegram_on_open": True,
    "telegram_on_close": True,
    "telegram_on_error": True,
    "telegram_on_high_probability": True,
    "telegram_high_probability_threshold": 85,
    "telegram_daily_summary": False,
}

# ---- NSE market hours ----------------------------------------------------
#
# NSE regular equity session: Mon-Fri, 09:15 IST to 15:30 IST.
# Pre-open auction: 09:00-09:15 IST.
# Public holidays aren't embedded (would require a maintained calendar);
# if the exchange is closed on a weekday holiday this helper will still return
# OPEN, and orders will simply park at the last-known close — same behaviour as
# before the market-status upgrade, but now visible to the user.

IST = ZoneInfo("Asia/Kolkata")
MARKET_OPEN_HM = (9, 15)
MARKET_CLOSE_HM = (15, 30)
PRE_OPEN_HM = (9, 0)

# NSE (via XBOM — same trading holidays as XNSE; BSE/NSE share SEBI-set calendar).
# Fallback hard-coded holiday list keeps the system working if the exchange_calendars
# library is missing or its wheel can't be installed.
_XBOM_CAL = None
try:
    import exchange_calendars as _ec

    _XBOM_CAL = _ec.get_calendar("XBOM")
except Exception as exc:  # pragma: no cover — graceful fallback
    logger.warning("exchange_calendars unavailable (%s); falling back to hard-coded NSE holidays.", exc)

# Seed list (refresh annually). Source: NSE official trading holidays.
NSE_HOLIDAYS_FALLBACK: dict[str, str] = {
    # 2025 (NSE & BSE jointly)
    "2025-02-26": "Mahashivratri",
    "2025-03-14": "Holi",
    "2025-03-31": "Id-Ul-Fitr",
    "2025-04-10": "Mahavir Jayanti",
    "2025-04-14": "Dr. B.R. Ambedkar Jayanti",
    "2025-04-18": "Good Friday",
    "2025-05-01": "Maharashtra Day",
    "2025-08-15": "Independence Day",
    "2025-08-27": "Ganesh Chaturthi",
    "2025-10-02": "Mahatma Gandhi Jayanti / Dussehra",
    "2025-10-21": "Diwali Laxmi Pujan (muhurat — closed for regular session)",
    "2025-10-22": "Diwali-Balipratipada",
    "2025-11-05": "Guru Nanak Jayanti",
    "2025-12-25": "Christmas",
    # 2026 (provisional — verify on NSE when published)
    "2026-01-26": "Republic Day",
    "2026-02-15": "Mahashivratri (provisional)",
    "2026-03-03": "Holi (provisional)",
    "2026-03-19": "Id-Ul-Fitr (provisional)",
    "2026-04-01": "Annual Bank Closing",
    "2026-04-03": "Good Friday (provisional)",
    "2026-04-14": "Dr. B.R. Ambedkar Jayanti",
    "2026-05-01": "Maharashtra Day",
    "2026-08-15": "Independence Day",
    "2026-10-02": "Mahatma Gandhi Jayanti",
    "2026-11-09": "Diwali (provisional)",
    "2026-12-25": "Christmas",
}


def _ist_now() -> datetime:
    return datetime.now(IST)


def _holiday_name_for(date_iso: str) -> Optional[str]:
    """Best-effort human name for a NSE holiday date (YYYY-MM-DD, IST)."""
    return NSE_HOLIDAYS_FALLBACK.get(date_iso)


def _is_trading_day(date_: datetime) -> bool:
    """True if NSE holds a regular session on this IST date."""
    ist_date = date_.astimezone(IST).date() if date_.tzinfo else date_.date()
    iso = ist_date.isoformat()
    if ist_date.weekday() >= 5:
        return False
    if _XBOM_CAL is not None:
        try:
            import pandas as _pd

            return bool(_XBOM_CAL.is_session(_pd.Timestamp(iso)))
        except Exception:  # pragma: no cover
            pass
    # Fallback — hard-coded list.
    return iso not in NSE_HOLIDAYS_FALLBACK


def _next_session_open_ist(after: datetime) -> datetime:
    """Next NSE open timestamp (IST) strictly after `after`."""
    cur = after.astimezone(IST)
    candidate = cur.replace(hour=MARKET_OPEN_HM[0], minute=MARKET_OPEN_HM[1], second=0, microsecond=0)
    if candidate <= cur:
        candidate += timedelta(days=1)
    # Walk forward (max 14 days safety) until we land on a trading day.
    for _ in range(14):
        if _is_trading_day(candidate):
            return candidate
        candidate += timedelta(days=1)
    return candidate  # fallback — shouldn't happen


def _nse_holidays_in_year(year: int) -> list[dict[str, Any]]:
    """All NSE holidays (date + human name) in the given calendar year."""
    import datetime as _dt

    year_start = _dt.date(year, 1, 1)
    year_end = _dt.date(year, 12, 31)
    out: list[dict[str, Any]] = []

    if _XBOM_CAL is not None:
        try:
            import pandas as _pd

            # Adhoc + regular holidays fall on weekdays; schedule shows only sessions.
            idx = _pd.date_range(year_start, year_end, freq="B")
            sessions = set(
                _XBOM_CAL.sessions_in_range(
                    _pd.Timestamp(year_start), _pd.Timestamp(year_end)
                ).strftime("%Y-%m-%d")
            )
            for ts in idx:
                iso = ts.strftime("%Y-%m-%d")
                if iso in sessions:
                    continue
                out.append({"date": iso, "name": NSE_HOLIDAYS_FALLBACK.get(iso, "NSE holiday")})
        except Exception as exc:  # pragma: no cover
            logger.warning("Holiday listing via exchange_calendars failed: %s", exc)

    if not out:
        out = [
            {"date": k, "name": v}
            for k, v in NSE_HOLIDAYS_FALLBACK.items()
            if k.startswith(str(year))
        ]
    out.sort(key=lambda r: r["date"])
    return out


def get_market_status(now: Optional[datetime] = None) -> dict[str, Any]:
    """Return NSE market status with IST-aware timestamps.

    Status values:
      OPEN       — regular session (09:15-15:30 IST, Mon-Fri, not a holiday)
      PRE_OPEN   — pre-open auction window (09:00-09:15 IST, Mon-Fri, session day)
      PRE_MARKET — before 09:00 IST on a session day
      POST_CLOSE — after 15:30 IST on a session day
      WEEKEND    — Saturday / Sunday
      HOLIDAY    — weekday but NSE is closed (Republic Day, Diwali, etc.)
    """
    now_ist = (now.astimezone(IST) if now and now.tzinfo else now) or _ist_now()
    weekday = now_ist.weekday()
    today_open = now_ist.replace(hour=MARKET_OPEN_HM[0], minute=MARKET_OPEN_HM[1], second=0, microsecond=0)
    today_close = now_ist.replace(hour=MARKET_CLOSE_HM[0], minute=MARKET_CLOSE_HM[1], second=0, microsecond=0)
    today_preopen = now_ist.replace(hour=PRE_OPEN_HM[0], minute=PRE_OPEN_HM[1], second=0, microsecond=0)
    today_iso = now_ist.date().isoformat()
    is_session_today = _is_trading_day(now_ist)
    holiday_name: Optional[str] = None

    if weekday >= 5:
        status = "WEEKEND"
        holiday_name = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][weekday]
    elif not is_session_today:
        status = "HOLIDAY"
        holiday_name = _holiday_name_for(today_iso) or "NSE holiday"
    elif now_ist < today_preopen:
        status = "PRE_MARKET"
    elif now_ist < today_open:
        status = "PRE_OPEN"
    elif now_ist <= today_close:
        status = "OPEN"
    else:
        status = "POST_CLOSE"

    is_open = status == "OPEN"

    if status == "OPEN":
        next_open = today_open
        next_close = today_close
    else:
        anchor = now_ist if now_ist < today_open else today_open
        next_open = _next_session_open_ist(anchor)
        next_close = next_open.replace(hour=MARKET_CLOSE_HM[0], minute=MARKET_CLOSE_HM[1])

    # Days-until-open stated as distinct IST calendar days for the UI.
    next_open_date_iso = next_open.date().isoformat()
    # Friendly hint: explain why market is closed right now.
    if status == "WEEKEND":
        next_open_reason = f"Weekend — next session on {next_open.strftime('%A, %d %b')}"
    elif status == "HOLIDAY":
        next_open_reason = f"{holiday_name} — next session on {next_open.strftime('%A, %d %b')}"
    elif status == "POST_CLOSE":
        next_open_reason = f"Market closed for today — next session on {next_open.strftime('%A, %d %b')}"
    elif status in ("PRE_MARKET", "PRE_OPEN"):
        next_open_reason = f"Pre-market — session opens at {MARKET_OPEN_HM[0]:02d}:{MARKET_OPEN_HM[1]:02d} IST"
    else:
        next_open_reason = None

    minutes_to_open = max(int((next_open - now_ist).total_seconds() // 60), 0)

    return {
        "status": status,
        "is_open": is_open,
        "now_ist": now_ist.isoformat(timespec="seconds"),
        "today_ist": today_iso,
        "is_session_today": is_session_today,
        "holiday_name": holiday_name,
        "next_open_ist": next_open.isoformat(timespec="seconds"),
        "next_close_ist": next_close.isoformat(timespec="seconds"),
        "next_open_date_ist": next_open_date_iso,
        "minutes_to_open": minutes_to_open,
        "session": {
            "open_hm": f"{MARKET_OPEN_HM[0]:02d}:{MARKET_OPEN_HM[1]:02d}",
            "close_hm": f"{MARKET_CLOSE_HM[0]:02d}:{MARKET_CLOSE_HM[1]:02d}",
            "timezone": "Asia/Kolkata",
            "exchange": "NSE",
        },
        "calendar_source": "XBOM via exchange_calendars" if _XBOM_CAL else "bundled fallback",
        "_hint_next_open_reason": next_open_reason,  # best-effort friendly tag
    }

# Exit reasons
EXIT_SL = "SL_HIT"
EXIT_TARGET = "TARGET_HIT"
EXIT_TIME = "TIME_EXIT"
EXIT_MANUAL = "MANUAL_CLOSE"


# ---- Telegram notifier ---------------------------------------------------
#
# Send-only integration. Token lives in the secrets vault; chat id is in .env.
# All send calls swallow errors — alerts are never on the critical path for
# trades or monitor execution.


TELEGRAM_API_BASE = "https://api.telegram.org"
_telegram_debounce_lock = threading.Lock()
_telegram_debounce: dict[str, float] = {}
_telegram_failure_counts: dict[str, int] = {}
TELEGRAM_DEBOUNCE_SECONDS = 60


def _tg_enabled() -> bool:
    return bool(TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID)


def _tg_debounced(key: str, ttl: int = TELEGRAM_DEBOUNCE_SECONDS) -> bool:
    """Return True if an event with this key was sent less than ttl seconds ago."""
    now = time.time()
    with _telegram_debounce_lock:
        last = _telegram_debounce.get(key, 0.0)
        if now - last < ttl:
            return True
        _telegram_debounce[key] = now
        return False


# MarkdownV2 requires escaping a large set of chars.
_TG_MD_SPECIALS = r"_*[]()~`>#+-=|{}.!\\"


def _tg_escape(text: str) -> str:
    out = []
    for ch in str(text):
        if ch in _TG_MD_SPECIALS:
            out.append("\\" + ch)
        else:
            out.append(ch)
    return "".join(out)


async def _tg_send(text: str, *, parse_mode: str = "MarkdownV2") -> dict[str, Any]:
    """Low-level sendMessage. Returns {ok: bool, detail?: ...}. Does not raise."""
    if not _tg_enabled():
        return {"ok": False, "detail": "telegram not configured (missing token or chat id)"}
    url = f"{TELEGRAM_API_BASE}/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.post(
                url,
                json={
                    "chat_id": TELEGRAM_CHAT_ID,
                    "text": text,
                    "parse_mode": parse_mode,
                    "disable_web_page_preview": True,
                },
            )
        if r.status_code >= 400:
            try:
                return {"ok": False, "status": r.status_code, "detail": r.json()}
            except Exception:
                return {"ok": False, "status": r.status_code, "detail": r.text[:400]}
        payload = r.json()
        return {"ok": bool(payload.get("ok")), "detail": payload}
    except Exception as exc:
        return {"ok": False, "detail": f"{type(exc).__name__}: {exc}"}


async def telegram_send_test() -> dict[str, Any]:
    text = (
        "✅ *QuantEdge AI* alerts are wired up\\.\n"
        f"Test ping at `{_tg_escape(datetime.now(timezone.utc).isoformat(timespec='seconds'))}`\n"
        "You'll receive pings here when trades open, close, or hit stops\\."
    )
    return await _tg_send(text)


async def telegram_send_trade_opened(trade: dict[str, Any]) -> None:
    settings = _paper_settings_snapshot()
    if not settings.get("telegram_on_open") or not _tg_enabled():
        return
    symbol = trade.get("symbol", "?")
    aftermarket = (trade.get("source") or "") == "auto-aftermarket"
    if _tg_debounced(f"open:{symbol}:{trade.get('id')}"):
        return
    emoji = "📈" if (trade.get("source") == "auto") else ("🕒" if aftermarket else "🧑‍💻")
    score_part = f" · score {trade['combined_score']}" if trade.get("combined_score") else ""
    notes_part = "\n⚠️ Opened after market — entry = last close" if aftermarket else ""
    msg = (
        f"{emoji} *{_tg_escape(symbol)}* opened{_tg_escape(score_part)}\n"
        f"qty `{trade.get('quantity')}` · entry `₹{_tg_escape(round(float(trade['entry_price']), 2))}`\n"
        f"stop `₹{_tg_escape(round(float(trade['stop_loss']), 2))}` · target `₹{_tg_escape(round(float(trade['target_price']), 2))}`\n"
        f"risk `₹{_tg_escape(round(float(trade.get('risk_amount') or 0), 0))}` · id `#{trade.get('id')}`"
        f"{_tg_escape(notes_part) if notes_part else ''}"
    )
    await _tg_send(msg)


async def telegram_send_trade_closed(trade: dict[str, Any]) -> None:
    settings = _paper_settings_snapshot()
    if not settings.get("telegram_on_close") or not _tg_enabled():
        return
    if _tg_debounced(f"close:{trade.get('id')}"):
        return
    symbol = trade.get("symbol", "?")
    reason = str(trade.get("exit_reason") or "CLOSE")
    pnl = float(trade.get("pnl_amount") or 0)
    pnl_pct = float(trade.get("pnl_pct") or 0)
    sign = "+" if pnl >= 0 else ""
    emoji = (
        "✅" if reason == "TARGET_HIT"
        else "❌" if reason == "SL_HIT"
        else "⏰" if reason == "TIME_EXIT"
        else "🔚"
    )
    msg = (
        f"{emoji} *{_tg_escape(symbol)}* {_tg_escape(reason)}\n"
        f"P&L `{_tg_escape(sign)}₹{_tg_escape(round(pnl, 0))}` "
        f"\\({_tg_escape(sign)}{_tg_escape(round(pnl_pct, 2))}%\\)\n"
        f"entry `₹{_tg_escape(round(float(trade.get('entry_price') or 0), 2))}` "
        f"→ exit `₹{_tg_escape(round(float(trade.get('close_price') or 0), 2))}` · id `#{trade.get('id')}`"
    )
    await _tg_send(msg)


async def telegram_send_monitor_failure(symbol: str, reason: str) -> None:
    settings = _paper_settings_snapshot()
    if not settings.get("telegram_on_error") or not _tg_enabled():
        return
    # Debounce monitor errors more aggressively — 15 minutes per symbol.
    if _tg_debounced(f"mon-err:{symbol}", ttl=900):
        return
    msg = (
        f"⚠️ *Monitor* can't refresh {_tg_escape(symbol)}\n"
        f"`{_tg_escape(reason[:160])}`"
    )
    await _tg_send(msg)


async def telegram_send_high_probability(symbol: str, score: int) -> None:
    settings = _paper_settings_snapshot()
    if not settings.get("telegram_on_high_probability") or not _tg_enabled():
        return
    threshold = int(settings.get("telegram_high_probability_threshold", 85))
    if score < threshold:
        return
    if _tg_debounced(f"hp:{symbol}:{score}", ttl=3600):
        return
    msg = (
        f"🎯 *{_tg_escape(symbol)}* scored *{score}* "
        f"\\(≥ {threshold}\\) — high probability setup"
    )
    await _tg_send(msg)


def _init_paper_schema() -> None:
    with _db_lock, _db_connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS paper_trades (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol         TEXT    NOT NULL,
                exchange       TEXT    NOT NULL DEFAULT 'NSE',
                status         TEXT    NOT NULL,  -- OPEN, CLOSED
                entry_price    REAL    NOT NULL,
                stop_loss      REAL    NOT NULL,
                target_price   REAL    NOT NULL,
                quantity       INTEGER NOT NULL,
                risk_amount    REAL    NOT NULL,
                opened_at      TEXT    NOT NULL,
                closed_at      TEXT,
                close_price    REAL,
                exit_reason    TEXT,             -- SL_HIT, TARGET_HIT, TIME_EXIT, MANUAL_CLOSE
                pnl_amount     REAL,
                pnl_pct        REAL,
                max_hold_days  INTEGER NOT NULL DEFAULT 20,
                source         TEXT    NOT NULL, -- auto | manual
                history_id     INTEGER,          -- deep_analysis_history FK
                combined_score INTEGER,
                notes          TEXT,
                last_price     REAL,
                last_marked_at TEXT
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_paper_status ON paper_trades(status)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_paper_symbol ON paper_trades(symbol)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_paper_opened ON paper_trades(opened_at DESC)"
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS paper_orders (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                trade_id      INTEGER NOT NULL,
                side          TEXT    NOT NULL,  -- BUY, SELL
                reason        TEXT    NOT NULL,  -- OPEN, SL_HIT, TARGET_HIT, TIME_EXIT, MANUAL_CLOSE
                price         REAL    NOT NULL,
                quantity      INTEGER NOT NULL,
                executed_at   TEXT    NOT NULL,
                FOREIGN KEY (trade_id) REFERENCES paper_trades(id) ON DELETE CASCADE
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_paper_orders_trade ON paper_orders(trade_id)"
        )


_init_paper_schema()


# ---------------------------------------------------------------------------
# ML Training schema
# ---------------------------------------------------------------------------


def _init_ml_schema() -> None:
    with _db_lock, _db_connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS ml_training_runs (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                trained_at      TEXT    NOT NULL,
                symbols_used    INTEGER NOT NULL,
                dataset_size    INTEGER NOT NULL,
                training_period TEXT    NOT NULL,
                best_model      TEXT    NOT NULL,
                best_auc        REAL    NOT NULL,
                payload         TEXT    NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_ml_trained_at ON ml_training_runs(trained_at DESC)"
        )


_init_ml_schema()


def _init_broker_schema() -> None:
    with _db_lock, _db_connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS broker_orders (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                paper_trade_id INTEGER,
                order_id       TEXT,
                symbol         TEXT    NOT NULL,
                exchange       TEXT    NOT NULL DEFAULT 'NSE',
                side           TEXT    NOT NULL,
                qty            INTEGER NOT NULL,
                price          REAL,
                order_type     TEXT    NOT NULL,
                status         TEXT,
                placed_at      TEXT    NOT NULL,
                updated_at     TEXT,
                response       TEXT
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_broker_placed ON broker_orders(placed_at DESC)"
        )


_init_broker_schema()


# ---- Helpers -------------------------------------------------------------


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _row_to_trade_dict(row: sqlite3.Row) -> dict[str, Any]:
    d = dict(row)
    d["status"] = d["status"].upper()
    return d


def _fetch_open_trade_for_symbol_sync(symbol: str) -> Optional[dict[str, Any]]:
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT * FROM paper_trades WHERE symbol = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1",
            (symbol.upper(),),
        ).fetchone()
    return _row_to_trade_dict(row) if row else None


def _count_open_positions_sync() -> int:
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n FROM paper_trades WHERE status = 'OPEN'"
        ).fetchone()
    return int(row["n"] if row else 0)


def _sum_realized_pnl_sync() -> float:
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT COALESCE(SUM(pnl_amount), 0) AS total FROM paper_trades WHERE status = 'CLOSED'"
        ).fetchone()
    return float(row["total"] if row else 0.0)


def _list_trades_sync(status: Optional[str] = None, symbol: Optional[str] = None, limit: int = 100) -> list[dict[str, Any]]:
    limit = max(1, min(int(limit), 500))
    clauses: list[str] = []
    params: list[Any] = []
    if status:
        clauses.append("status = ?")
        params.append(status.upper())
    if symbol:
        clauses.append("symbol = ?")
        params.append(symbol.upper())
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with _db_connect() as conn:
        rows = conn.execute(
            f"""
            SELECT * FROM paper_trades
            {where}
            ORDER BY (CASE status WHEN 'OPEN' THEN 0 ELSE 1 END),
                     opened_at DESC, id DESC
            LIMIT ?
            """,
            [*params, limit],
        ).fetchall()
    return [_row_to_trade_dict(r) for r in rows]


def _fetch_trade_sync(trade_id: int) -> Optional[dict[str, Any]]:
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT * FROM paper_trades WHERE id = ?", (int(trade_id),)
        ).fetchone()
        if row is None:
            return None
        orders = conn.execute(
            "SELECT * FROM paper_orders WHERE trade_id = ? ORDER BY id ASC",
            (int(trade_id),),
        ).fetchall()
    trade = _row_to_trade_dict(row)
    trade["orders"] = [dict(o) for o in orders]
    return trade


def _insert_trade_sync(trade: dict[str, Any]) -> int:
    """Insert an OPEN trade and its BUY order atomically."""
    with _db_lock, _db_connect() as conn:
        cursor = conn.execute(
            """
            INSERT INTO paper_trades (
                symbol, exchange, status, entry_price, stop_loss, target_price,
                quantity, risk_amount, opened_at, max_hold_days, source,
                history_id, combined_score, notes, last_price, last_marked_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                trade["symbol"].upper(),
                trade.get("exchange", "NSE"),
                "OPEN",
                float(trade["entry_price"]),
                float(trade["stop_loss"]),
                float(trade["target_price"]),
                int(trade["quantity"]),
                float(trade["risk_amount"]),
                trade.get("opened_at") or _now_iso(),
                int(trade.get("max_hold_days", PAPER_MAX_HOLD_DAYS)),
                trade.get("source", "manual"),
                trade.get("history_id"),
                trade.get("combined_score"),
                trade.get("notes"),
                float(trade["entry_price"]),
                trade.get("opened_at") or _now_iso(),
            ),
        )
        tid = int(cursor.lastrowid or 0)
        conn.execute(
            """
            INSERT INTO paper_orders (trade_id, side, reason, price, quantity, executed_at)
            VALUES (?,?,?,?,?,?)
            """,
            (tid, "BUY", "OPEN", float(trade["entry_price"]), int(trade["quantity"]), trade.get("opened_at") or _now_iso()),
        )
    return tid


def _close_trade_sync(
    trade_id: int,
    close_price: float,
    exit_reason: str,
    closed_at: Optional[str] = None,
) -> Optional[dict[str, Any]]:
    closed_at = closed_at or _now_iso()
    with _db_lock, _db_connect() as conn:
        row = conn.execute(
            "SELECT * FROM paper_trades WHERE id = ? AND status = 'OPEN'", (int(trade_id),)
        ).fetchone()
        if row is None:
            return None
        entry = float(row["entry_price"])
        qty = int(row["quantity"])
        pnl = (float(close_price) - entry) * qty
        pnl_pct = (float(close_price) - entry) / entry * 100 if entry else 0.0
        conn.execute(
            """
            UPDATE paper_trades
            SET status='CLOSED',
                close_price=?,
                closed_at=?,
                exit_reason=?,
                pnl_amount=?,
                pnl_pct=?,
                last_price=?,
                last_marked_at=?
            WHERE id=?
            """,
            (
                float(close_price),
                closed_at,
                exit_reason,
                pnl,
                pnl_pct,
                float(close_price),
                closed_at,
                int(trade_id),
            ),
        )
        conn.execute(
            """
            INSERT INTO paper_orders (trade_id, side, reason, price, quantity, executed_at)
            VALUES (?,?,?,?,?,?)
            """,
            (int(trade_id), "SELL", exit_reason, float(close_price), qty, closed_at),
        )
    return _fetch_trade_sync(trade_id)


def _mark_trade_price_sync(trade_id: int, price: float) -> None:
    with _db_lock, _db_connect() as conn:
        conn.execute(
            "UPDATE paper_trades SET last_price=?, last_marked_at=? WHERE id=? AND status='OPEN'",
            (float(price), _now_iso(), int(trade_id)),
        )


# ---- Sizing --------------------------------------------------------------


def _compute_qty(entry: float, stop: float, risk_rupees: float) -> int:
    per_share_risk = max(entry - stop, 0.01)
    qty = int(risk_rupees // per_share_risk)
    return max(qty, 0)


def _current_equity_sync() -> float:
    settings = _paper_settings_snapshot()
    realised = _sum_realized_pnl_sync()
    return float(settings["starting_capital"]) + realised


def _paper_settings_snapshot() -> dict[str, Any]:
    with _paper_settings_lock:
        return dict(_paper_settings)


def _paper_settings_update(updates: dict[str, Any]) -> dict[str, Any]:
    """Validate and commit runtime settings changes."""
    allowed = {
        "auto_trade_enabled",
        "auto_trade_threshold",
        "auto_trade_market_open_only",
        "risk_per_trade_pct",
        "max_open_positions",
        "max_hold_days",
        "telegram_on_open",
        "telegram_on_close",
        "telegram_on_error",
        "telegram_on_high_probability",
        "telegram_high_probability_threshold",
        "telegram_daily_summary",
    }
    bool_keys = {
        "auto_trade_enabled",
        "auto_trade_market_open_only",
        "telegram_on_open",
        "telegram_on_close",
        "telegram_on_error",
        "telegram_on_high_probability",
        "telegram_daily_summary",
    }
    int_keys = {
        "auto_trade_threshold",
        "max_open_positions",
        "max_hold_days",
        "telegram_high_probability_threshold",
    }
    float_keys = {"risk_per_trade_pct"}
    with _paper_settings_lock:
        for k, v in updates.items():
            if k not in allowed:
                continue
            if k in bool_keys:
                _paper_settings[k] = bool(v)
            elif k in int_keys:
                _paper_settings[k] = int(v)
            elif k in float_keys:
                _paper_settings[k] = float(v)
        return dict(_paper_settings)


# ---- Public APIs ---------------------------------------------------------


class PaperTradeError(Exception):
    pass


async def open_paper_trade(
    symbol: str,
    entry_price: float,
    stop_loss: float,
    target_price: float,
    *,
    source: str = "manual",
    history_id: Optional[int] = None,
    combined_score: Optional[int] = None,
    exchange: str = "NSE",
    notes: Optional[str] = None,
) -> dict[str, Any]:
    settings = _paper_settings_snapshot()

    # Validation
    if not (stop_loss < entry_price < target_price):
        raise PaperTradeError(
            "Invalid levels: require stop_loss < entry_price < target_price."
        )

    # Dedup — one OPEN position per symbol.
    existing = await asyncio.to_thread(_fetch_open_trade_for_symbol_sync, symbol.upper())
    if existing:
        raise PaperTradeError(
            f"An open position for {symbol.upper()} already exists (#${existing['id']})."
        )

    open_count = await asyncio.to_thread(_count_open_positions_sync)
    if open_count >= settings["max_open_positions"]:
        raise PaperTradeError(
            f"Max open positions reached ({open_count}/{settings['max_open_positions']})."
        )

    equity = await asyncio.to_thread(_current_equity_sync)
    risk_rupees = equity * (settings["risk_per_trade_pct"] / 100)
    qty = _compute_qty(entry_price, stop_loss, risk_rupees)
    if qty <= 0:
        raise PaperTradeError(
            "Computed quantity is zero — risk too small or stop too far. Check levels."
        )

    payload = {
        "symbol": symbol,
        "exchange": exchange,
        "entry_price": entry_price,
        "stop_loss": stop_loss,
        "target_price": target_price,
        "quantity": qty,
        "risk_amount": risk_rupees,
        "source": source,
        "history_id": history_id,
        "combined_score": combined_score,
        "notes": notes,
        "opened_at": _now_iso(),
    }
    tid = await asyncio.to_thread(_insert_trade_sync, payload)
    logger.info(
        "Paper trade #%d OPEN %s qty=%d entry=%.2f stop=%.2f target=%.2f risk=₹%.0f source=%s",
        tid, symbol.upper(), qty, entry_price, stop_loss, target_price, risk_rupees, source,
    )
    return await asyncio.to_thread(_fetch_trade_sync, tid)


async def close_paper_trade(
    trade_id: int,
    *,
    price: Optional[float] = None,
    reason: str = EXIT_MANUAL,
) -> dict[str, Any]:
    trade = await asyncio.to_thread(_fetch_trade_sync, trade_id)
    if trade is None:
        raise PaperTradeError(f"Trade {trade_id} not found.")
    if trade["status"] != "OPEN":
        raise PaperTradeError(f"Trade {trade_id} is not OPEN.")

    close_price = price
    if close_price is None:
        try:
            data = await asyncio.to_thread(
                _fetch_live_stock_data, trade["symbol"], trade.get("exchange", "NSE")
            )
            close_price = float(data["price"])
        except Exception as exc:
            raise PaperTradeError(
                f"Failed to fetch live price for {trade['symbol']}: {exc}"
            ) from exc

    closed = await asyncio.to_thread(
        _close_trade_sync, int(trade_id), float(close_price), reason
    )
    if closed is None:
        raise PaperTradeError(f"Trade {trade_id} was already closed.")
    logger.info(
        "Paper trade #%d CLOSE %s @ %.2f reason=%s pnl=₹%.0f (%.2f%%)",
        trade_id, closed["symbol"], float(close_price), reason,
        closed.get("pnl_amount") or 0.0, closed.get("pnl_pct") or 0.0,
    )
    try:
        asyncio.create_task(telegram_send_trade_closed(closed))
    except Exception as exc:
        logger.debug("telegram_send_trade_closed scheduling failed: %s", exc)
    return closed


async def maybe_auto_paper_trade(
    symbol: str,
    analysis: dict[str, Any],
    history_id: Optional[int] = None,
) -> tuple[Optional[dict[str, Any]], dict[str, Any]]:
    """If settings allow, open a paper trade from a Deep Analyze result.

    Returns (trade_or_none, meta) where meta always carries the reason so the
    frontend can show a helpful banner ("Market closed", "Below threshold", ...).
    """
    settings = _paper_settings_snapshot()
    market = get_market_status()
    overall = analysis.get("overall") or {}
    score = int(overall.get("combined_score", 0) or 0)
    threshold = int(settings["auto_trade_threshold"])

    meta: dict[str, Any] = {
        "enabled": bool(settings["auto_trade_enabled"]),
        "threshold": threshold,
        "combined_score": score,
        "market_status": market["status"],
        "market_open": market["is_open"],
        "market_open_only": bool(settings.get("auto_trade_market_open_only")),
    }

    if not settings["auto_trade_enabled"]:
        meta["reason"] = "Auto-trade is disabled in Paper Trading settings."
        return None, meta
    if score < threshold:
        meta["reason"] = f"Score {score} is below the {threshold} threshold."
        return None, meta
    if settings.get("auto_trade_market_open_only") and not market["is_open"]:
        meta["reason"] = (
            f"Market is {market['status']} — auto-trade is set to fire only during NSE hours. "
            f"Next open: {market['next_open_ist']}."
        )
        return None, meta

    levels = analysis.get("levels") or {}
    entry = levels.get("entry")
    stop = levels.get("stop")
    target = levels.get("target_1")
    if not (isinstance(entry, (int, float)) and isinstance(stop, (int, float)) and isinstance(target, (int, float))):
        meta["reason"] = "Analysis is missing valid entry/stop/target levels."
        return None, meta

    effective_source = "auto" if market["is_open"] else "auto-aftermarket"
    note_parts = [f"Auto-opened from Deep Analyze score {score} · market {market['status']}"]
    if not market["is_open"]:
        note_parts.append(
            f"Entry is the last session close ({analysis.get('scan_timestamp')}); "
            f"treat as a pending-at-open fill."
        )
    try:
        trade = await open_paper_trade(
            symbol,
            float(entry),
            float(stop),
            float(target),
            source=effective_source,
            history_id=history_id,
            combined_score=score,
            notes=" | ".join(note_parts),
        )
        meta["reason"] = (
            "Auto-trade opened."
            if market["is_open"]
            else "Auto-trade opened AFTER MARKET HOURS — entry = last close."
        )
        # Fire-and-forget Telegram ping; never block the trade path on alerts.
        try:
            asyncio.create_task(telegram_send_trade_opened(trade))
        except Exception as exc:
            logger.debug("telegram_send_trade_opened scheduling failed: %s", exc)
        return trade, meta
    except PaperTradeError as exc:
        logger.info("Auto paper-trade skipped for %s: %s", symbol, exc)
        meta["reason"] = str(exc)
        return None, meta
    except Exception as exc:
        logger.warning("Auto paper-trade raised for %s: %s", symbol, exc)
        meta["reason"] = f"Internal error: {exc}"
        return None, meta


# ---- Portfolio snapshot --------------------------------------------------


async def paper_portfolio_snapshot() -> dict[str, Any]:
    settings = _paper_settings_snapshot()
    starting_capital = float(settings["starting_capital"])
    realised = await asyncio.to_thread(_sum_realized_pnl_sync)
    equity = starting_capital + realised

    open_trades = await asyncio.to_thread(_list_trades_sync, "OPEN", None, 500)
    # Mark-to-market open positions using cached live prices.
    unrealised = 0.0
    marked_positions: list[dict[str, Any]] = []
    for t in open_trades:
        sym = t["symbol"]
        current = t.get("last_price") or t["entry_price"]
        try:
            data = await asyncio.to_thread(_fetch_live_stock_data, sym, t.get("exchange", "NSE"))
            current = float(data["price"])
            await asyncio.to_thread(_mark_trade_price_sync, int(t["id"]), current)
            t["last_price"] = current
            t["last_marked_at"] = _now_iso()
        except Exception as exc:
            logger.debug("Mark-to-market failed for %s: %s", sym, exc)
        entry = float(t["entry_price"])
        qty = int(t["quantity"])
        unrealised_trade = (float(current) - entry) * qty
        t["unrealised_pnl"] = round(unrealised_trade, 2)
        t["unrealised_pnl_pct"] = round((float(current) - entry) / entry * 100, 2) if entry else 0.0
        unrealised += unrealised_trade
        marked_positions.append(t)

    with _db_connect() as conn:
        stats_row = conn.execute(
            """
            SELECT
              COUNT(*) AS n_closed,
              SUM(CASE WHEN pnl_amount > 0 THEN 1 ELSE 0 END) AS n_wins,
              SUM(CASE WHEN pnl_amount <= 0 THEN 1 ELSE 0 END) AS n_losses,
              AVG(pnl_pct) AS avg_pnl_pct,
              MIN(pnl_pct) AS worst_pct,
              MAX(pnl_pct) AS best_pct
            FROM paper_trades
            WHERE status = 'CLOSED'
            """
        ).fetchone()

    n_closed = int(stats_row["n_closed"] or 0)
    n_wins = int(stats_row["n_wins"] or 0)
    n_losses = int(stats_row["n_losses"] or 0)
    win_rate = round(n_wins / n_closed * 100, 2) if n_closed else 0.0

    return {
        "settings": settings,
        "market_status": get_market_status(),
        "capital": {
            "starting": starting_capital,
            "realised_pnl": round(realised, 2),
            "unrealised_pnl": round(unrealised, 2),
            "total_equity": round(equity + unrealised, 2),
            "equity_ex_open": round(equity, 2),
        },
        "positions": {
            "open_count": len(marked_positions),
            "max_open": int(settings["max_open_positions"]),
            "open": marked_positions,
        },
        "stats": {
            "closed_count": n_closed,
            "wins": n_wins,
            "losses": n_losses,
            "win_rate": win_rate,
            "avg_pnl_pct": round(float(stats_row["avg_pnl_pct"] or 0), 2),
            "best_pct": round(float(stats_row["best_pct"] or 0), 2),
            "worst_pct": round(float(stats_row["worst_pct"] or 0), 2),
        },
    }


# ---- Monitor loop --------------------------------------------------------


async def _monitor_open_positions_once() -> dict[str, Any]:
    """Evaluate exits for every OPEN position. Returns {closed, skipped_market_closed, market_status}."""
    open_trades = await asyncio.to_thread(_list_trades_sync, "OPEN", None, 500)
    closed = 0
    settings = _paper_settings_snapshot()
    max_hold = int(settings["max_hold_days"])
    market = get_market_status()
    market_is_open = bool(market["is_open"])

    for t in open_trades:
        sym = t["symbol"]
        try:
            data = await asyncio.to_thread(_fetch_live_stock_data, sym, t.get("exchange", "NSE"))
            _telegram_failure_counts[sym] = 0  # reset on success
        except Exception as exc:
            logger.debug("Monitor fetch failed for %s: %s", sym, exc)
            # Alert after 3 consecutive failures for the same symbol.
            _telegram_failure_counts[sym] = _telegram_failure_counts.get(sym, 0) + 1
            if _telegram_failure_counts[sym] >= 3:
                try:
                    asyncio.create_task(
                        telegram_send_monitor_failure(sym, f"{type(exc).__name__}: {exc}")
                    )
                except Exception:
                    pass
            continue
        price = float(data["price"])
        await asyncio.to_thread(_mark_trade_price_sync, int(t["id"]), price)

        stop = float(t["stop_loss"])
        target = float(t["target_price"])

        # Intraday SL/TARGET evaluation only when the NSE regular session is live.
        # Outside market hours the "live" price is the last session close — evaluating
        # stops/targets against a stale bar produces false exits, especially on aftermarket-opened trades.
        if market_is_open:
            hit_stop = price <= stop
            hit_target = price >= target
            if hit_stop:
                await close_paper_trade(int(t["id"]), price=stop, reason=EXIT_SL)
                closed += 1
                continue
            if hit_target:
                await close_paper_trade(int(t["id"]), price=target, reason=EXIT_TARGET)
                closed += 1
                continue

        # Time-based exit always applies (calendar days).
        try:
            opened = datetime.fromisoformat(t["opened_at"].replace("Z", "+00:00"))
        except Exception:
            opened = datetime.now(timezone.utc)
        age_days = (datetime.now(timezone.utc) - opened).days
        if age_days >= max_hold:
            await close_paper_trade(int(t["id"]), price=price, reason=EXIT_TIME)
            closed += 1

    return {
        "closed": closed,
        "evaluated": len(open_trades),
        "market_status": market["status"],
        "market_open": market_is_open,
        "sl_target_checks_skipped": (not market_is_open) and len(open_trades) > 0,
    }


# ---------------------------------------------------------------------------
# WebSocket live price streaming (Phase 3)
# ---------------------------------------------------------------------------

WS_POLL_INTERVAL_SECONDS: int = int(os.getenv("WS_POLL_INTERVAL", "5"))


class PriceStreamManager:
    """Manages WebSocket connections subscribed to real-time price symbols."""

    def __init__(self) -> None:
        # symbol -> set of WebSocket connections
        self._subs: dict[str, set[WebSocket]] = {}
        # ws -> set of symbols it subscribed to
        self._ws_symbols: dict[int, set[str]] = {}
        self._lock = asyncio.Lock()

    async def connect(self, ws: WebSocket, symbols: list[str]) -> None:
        async with self._lock:
            ws_id = id(ws)
            if ws_id not in self._ws_symbols:
                self._ws_symbols[ws_id] = set()
            for sym in symbols:
                sym = sym.upper()
                self._subs.setdefault(sym, set()).add(ws)
                self._ws_symbols[ws_id].add(sym)

    async def unsubscribe(self, ws: WebSocket, symbols: list[str]) -> None:
        async with self._lock:
            ws_id = id(ws)
            for sym in symbols:
                sym = sym.upper()
                if sym in self._subs:
                    self._subs[sym].discard(ws)
                    if not self._subs[sym]:
                        del self._subs[sym]
                if ws_id in self._ws_symbols:
                    self._ws_symbols[ws_id].discard(sym)

    async def disconnect(self, ws: WebSocket) -> None:
        async with self._lock:
            ws_id = id(ws)
            syms = self._ws_symbols.pop(ws_id, set())
            for sym in syms:
                if sym in self._subs:
                    self._subs[sym].discard(ws)
                    if not self._subs[sym]:
                        del self._subs[sym]

    def all_symbols(self) -> list[str]:
        return list(self._subs.keys())

    async def broadcast(self, symbol: str, data: dict[str, Any]) -> None:
        conns = list(self._subs.get(symbol, set()))
        dead: list[WebSocket] = []
        for ws in conns:
            try:
                await ws.send_json(data)
            except Exception:
                dead.append(ws)
        for ws in dead:
            await self.disconnect(ws)


_price_stream = PriceStreamManager()
_price_poll_task: Optional[asyncio.Task] = None


async def _price_poll_loop() -> None:
    """Poll yfinance every WS_POLL_INTERVAL_SECONDS for subscribed symbols and broadcast."""
    logger.info("WebSocket price poller started (interval=%ds).", WS_POLL_INTERVAL_SECONDS)
    try:
        while True:
            await asyncio.sleep(WS_POLL_INTERVAL_SECONDS)
            symbols = _price_stream.all_symbols()
            if not symbols:
                continue
            try:
                import yfinance as yf
                tickers_str = " ".join(s + ".NS" for s in symbols)
                data = yf.download(
                    tickers_str,
                    period="1d",
                    interval="1m",
                    progress=False,
                    auto_adjust=True,
                )
                ts = datetime.now(timezone.utc).isoformat()
                if data.empty:
                    continue
                # yfinance returns a MultiIndex for multiple tickers
                if isinstance(data.columns, pd.MultiIndex):
                    for sym in symbols:
                        ticker = sym + ".NS"
                        try:
                            close_col = ("Close", ticker)
                            if close_col not in data.columns:
                                continue
                            prices = data[close_col].dropna()
                            if prices.empty:
                                continue
                            last_price = float(prices.iloc[-1])
                            open_price = float(data[("Open", ticker)].dropna().iloc[0])
                            vol = float(data[("Volume", ticker)].dropna().sum())
                            change_pct = ((last_price - open_price) / open_price * 100) if open_price else 0.0
                            await _price_stream.broadcast(sym, {
                                "type":       "price_update",
                                "symbol":     sym,
                                "price":      round(last_price, 2),
                                "change_pct": round(change_pct, 3),
                                "volume":     int(vol),
                                "ts":         ts,
                            })
                        except Exception as e:
                            logger.debug("WS price update failed for %s: %s", sym, e)
                else:
                    # Single ticker — data.columns is flat
                    sym = symbols[0]
                    try:
                        prices = data["Close"].dropna()
                        if prices.empty:
                            continue
                        last_price = float(prices.iloc[-1])
                        open_price = float(data["Open"].dropna().iloc[0])
                        vol = float(data["Volume"].dropna().sum())
                        change_pct = ((last_price - open_price) / open_price * 100) if open_price else 0.0
                        await _price_stream.broadcast(sym, {
                            "type":       "price_update",
                            "symbol":     sym,
                            "price":      round(last_price, 2),
                            "change_pct": round(change_pct, 3),
                            "volume":     int(vol),
                            "ts":         ts,
                        })
                    except Exception as e:
                        logger.debug("WS price update failed for %s: %s", sym, e)
            except Exception as exc:
                logger.warning("Price poll tick failed: %s", exc)
    except asyncio.CancelledError:
        logger.info("WebSocket price poller cancelled.")


_monitor_task: Optional[asyncio.Task] = None


async def _monitor_loop() -> None:
    logger.info(
        "Paper-trade monitor started (interval=%ds, threshold=%d, auto=%s).",
        PAPER_MONITOR_INTERVAL_SECONDS,
        _paper_settings["auto_trade_threshold"],
        _paper_settings["auto_trade_enabled"],
    )
    try:
        while True:
            try:
                await _monitor_open_positions_once()
            except Exception as exc:
                logger.warning("Paper monitor tick failed: %s", exc)
            await asyncio.sleep(PAPER_MONITOR_INTERVAL_SECONDS)
    except asyncio.CancelledError:
        logger.info("Paper-trade monitor cancelled.")


@app.on_event("startup")
async def _start_background_tasks() -> None:
    global _monitor_task, _price_poll_task
    if _monitor_task is None or _monitor_task.done():
        _monitor_task = asyncio.create_task(_monitor_loop())
    if _price_poll_task is None or _price_poll_task.done():
        _price_poll_task = asyncio.create_task(_price_poll_loop())


@app.on_event("shutdown")
async def _stop_background_tasks() -> None:
    for task in (_monitor_task, _price_poll_task):
        if task and not task.done():
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass


def _history_stats_sync() -> dict[str, Any]:
    with _db_connect() as conn:
        row = conn.execute(
            """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN high_probability = 1 THEN 1 ELSE 0 END) AS winners,
                   MAX(analyzed_at) AS last_analyzed_at,
                   AVG(combined_score) AS avg_score
            FROM deep_analysis_history
            """
        ).fetchone()
        top = conn.execute(
            """
            SELECT symbol, COUNT(*) AS n
            FROM deep_analysis_history
            GROUP BY symbol
            ORDER BY n DESC
            LIMIT 5
            """
        ).fetchall()
    return {
        "total": int(row["total"] or 0),
        "winners": int(row["winners"] or 0),
        "last_analyzed_at": row["last_analyzed_at"],
        "avg_score": round(float(row["avg_score"] or 0), 2),
        "top_symbols": [{"symbol": r["symbol"], "count": int(r["n"])} for r in top],
    }


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@app.get("/api-info")
async def api_info() -> dict[str, Any]:
    return {
        "service": "QuantEdge AI",
        "version": "2.0.0",
        "endpoints": [
            "GET /health",
            "POST /proxy/claude",
            "GET /stock-data/{symbol}",
            "GET /deep-analyze/{symbol}",
            "GET /deep-analysis-history",
            "GET /deep-analysis-history/stats",
            "GET /deep-analysis-history/{id}",
            "DELETE /deep-analysis-history/{id}",
            "DELETE /deep-analysis-history",
            "GET /market-status",
            "GET /market-holidays",
            "GET /paper-portfolio",
            "GET /paper-equity-curve",
            "GET /paper-settings",
            "PATCH /paper-settings",
            "GET /paper-trades",
            "POST /paper-trades",
            "GET /paper-trades/{id}",
            "POST /paper-trades/{id}/close",
            "POST /paper-trades/monitor-now",
            "GET /telegram-status",
            "POST /telegram-test",
            "POST /scan-best-stock",
            "POST /high-probability-scan",
        ],
        "universe_size": len(NIFTY_UNIVERSE),
        "strict_gates": {
            "min_score": COMBINED_MIN_SCORE,
            "fundamentals": {
                "max_pe": FUND_MAX_PE,
                "max_debt_to_equity": FUND_MAX_DE,
                "min_roe_pct": FUND_MIN_ROE,
                "min_revenue_growth_pct": FUND_MIN_REV_GROWTH,
                "min_profit_growth_pct": FUND_MIN_PROFIT_GROWTH,
                "min_promoter_pct": FUND_MIN_PROMOTER,
                "min_fii_plus_dii_pct": FUND_MIN_FII_DII,
                "max_promoter_pledge_pct": FUND_MAX_PROMOTER_PLEDGE,
            },
            "backtest": {
                "min_trades": BT_MIN_TRADES,
                "min_win_rate_pct": BT_MIN_WIN_RATE,
                "min_profit_factor": BT_MIN_PROFIT_FACTOR,
                "min_avg_return_pct": BT_MIN_AVG_RETURN,
                "forward_days": BT_FORWARD_DAYS,
            },
        },
    }


@app.get("/health")
async def health() -> dict[str, Any]:
    return {"status": "ok", "timestamp": datetime.now(timezone.utc).isoformat()}


# ---- Secrets-vault endpoints --------------------------------------------


class UnlockRequest(BaseModel):
    password: str = Field(min_length=1, max_length=512)


@app.get("/lock-status")
async def lock_status() -> dict[str, Any]:
    return {
        "configured": vault.configured,
        "unlocked": vault.unlocked,
        "retry_after_seconds": vault.remaining_backoff(),
        "session_ttl_seconds": SESSION_TTL_SECONDS,
        "deps_available": _VAULT_DEPS_OK,
    }


@app.post("/unlock")
async def unlock_endpoint(req: UnlockRequest) -> dict[str, Any]:
    if not _VAULT_DEPS_OK:
        raise HTTPException(status_code=500, detail="cryptography/PyJWT not installed on the server.")
    if not vault.configured:
        raise HTTPException(
            status_code=409,
            detail={
                "code": "not_configured",
                "message": "No vault configured. Run `python scripts/setup_secrets.py` on the server.",
            },
        )
    if vault.unlocked:
        # Already unlocked — hand back a fresh session token rather than erroring.
        return {"already_unlocked": True, **_issue_session_token()}

    try:
        ok = vault.unlock(req.password)
    except PermissionError as exc:
        raise HTTPException(
            status_code=429,
            detail={"code": "rate_limited", "message": str(exc), "retry_after_seconds": vault.remaining_backoff()},
        ) from exc
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=409,
            detail={"code": "not_configured", "message": str(exc)},
        ) from exc

    if not ok:
        raise HTTPException(
            status_code=401,
            detail={
                "code": "wrong_password",
                "message": "Invalid master password.",
                "retry_after_seconds": vault.remaining_backoff(),
                "failed_attempts": vault._failed_attempts,
            },
        )
    return {"ok": True, **_issue_session_token()}


@app.post("/lock")
async def lock_endpoint() -> dict[str, Any]:
    vault.lock()
    return {"locked": True}


def _build_claude_route() -> tuple[str, dict[str, str], str]:
    """Return (url, headers, mode) for the Claude call.

    Modes, in priority order:
      portkey-virtual   — PORTKEY_API_KEY + PORTKEY_VIRTUAL_KEY
      portkey-bring-own — PORTKEY_API_KEY + ANTHROPIC_API_KEY
      portkey-default   — PORTKEY_API_KEY only (uses your Portkey workspace
                          defaults / attached virtual key / PORTKEY_CONFIG)
      direct            — ANTHROPIC_API_KEY only, hit api.anthropic.com directly
    """
    portkey_messages_url = f"{PORTKEY_BASE_URL.rstrip('/')}/messages"

    if PORTKEY_API_KEY and PORTKEY_VIRTUAL_KEY:
        headers = {
            "x-portkey-api-key": PORTKEY_API_KEY,
            "x-portkey-virtual-key": PORTKEY_VIRTUAL_KEY,
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        }
        if PORTKEY_CONFIG:
            headers["x-portkey-config"] = PORTKEY_CONFIG
        return (portkey_messages_url, headers, "portkey-virtual")

    if PORTKEY_API_KEY and ANTHROPIC_API_KEY:
        headers = {
            "x-portkey-api-key": PORTKEY_API_KEY,
            "x-portkey-provider": "anthropic",
            "Authorization": f"Bearer {ANTHROPIC_API_KEY}",
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        }
        if PORTKEY_CONFIG:
            headers["x-portkey-config"] = PORTKEY_CONFIG
        return (portkey_messages_url, headers, "portkey-bring-own")

    if PORTKEY_API_KEY:
        # "Default" mode: only the Portkey API key is set. Portkey will route to
        # whichever provider is wired up to the workspace / config. Works when
        # you have a default virtual key attached in the Portkey UI or a
        # PORTKEY_CONFIG id with provider bindings.
        headers = {
            "x-portkey-api-key": PORTKEY_API_KEY,
            "x-portkey-provider": "anthropic",
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        }
        if PORTKEY_CONFIG:
            headers["x-portkey-config"] = PORTKEY_CONFIG
        return (portkey_messages_url, headers, "portkey-default")

    if ANTHROPIC_API_KEY:
        headers = {
            "x-api-key": ANTHROPIC_API_KEY,
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        }
        return (ANTHROPIC_DIRECT_URL, headers, "direct")

    raise HTTPException(
        status_code=503,
        detail=(
            "No Claude credentials configured. Set either PORTKEY_API_KEY (+ optional "
            "PORTKEY_VIRTUAL_KEY) or ANTHROPIC_API_KEY in backend/.env."
        ),
    )


@app.post("/proxy/claude")
async def proxy_claude(req: ClaudeProxyRequest) -> dict[str, Any]:
    url, headers, mode = _build_claude_route()

    body: dict[str, Any] = {
        "model": req.model or ANTHROPIC_DEFAULT_MODEL,
        "max_tokens": req.max_tokens,
        "temperature": req.temperature,
        "messages": [m.model_dump() for m in req.messages],
    }
    if req.system:
        body["system"] = req.system

    want_search = req.use_search
    # Bedrock (the typical Portkey backend) does not accept the
    # web_search_20250305 tool. Strip it unless the operator has explicitly
    # opted in via CLAUDE_FORCE_WEB_SEARCH=true.
    search_enabled = want_search and (
        mode == "direct" or CLAUDE_FORCE_WEB_SEARCH
    )
    if search_enabled:
        body["tools"] = [{"type": "web_search_20250305", "name": "web_search"}]
    if want_search and not search_enabled:
        logger.info(
            "Dropped web_search tool for mode=%s (Bedrock doesn't support it). "
            "Set CLAUDE_FORCE_WEB_SEARCH=true to override.",
            mode,
        )

    logger.debug(
        "Claude call via %s (model=%s, tools=%s)",
        mode,
        body["model"],
        search_enabled,
    )

    try:
        async with httpx.AsyncClient(timeout=60) as client:
            r = await client.post(url, json=body, headers=headers)
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Upstream Claude error via {mode}: {exc}") from exc

    if r.status_code >= 400:
        # Bubble up the upstream error body so the caller can see the real cause.
        try:
            detail = r.json()
        except Exception:
            detail = r.text
        if isinstance(detail, dict):
            detail = {**detail, "_route": mode}
        raise HTTPException(status_code=r.status_code, detail=detail)
    return r.json()


# ---------------------------------------------------------------------------
# Live per-symbol stock data (Yahoo Finance, via yfinance).
# ---------------------------------------------------------------------------
#
# The Scanner tab previously asked Claude for prices. On Bedrock (which doesn't
# support the web_search tool) Claude answered from training data and
# hallucinated numbers for small/mid-caps. We now pull authoritative data from
# Yahoo Finance and compute indicators locally. Cached per symbol for 60 s.


_stock_data_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_stock_data_cache_ttl = 60  # seconds


def _normalize_yahoo_symbol(symbol: str, exchange: str) -> str:
    """Map a user-friendly symbol + exchange to Yahoo's format."""
    sym = symbol.strip().upper()
    if "." in sym:
        return sym  # already qualified (RELIANCE.NS / AAPL)
    if exchange.upper() == "BSE":
        return f"{sym}.BO"
    if exchange.upper() in {"NSE", "NS"}:
        return f"{sym}.NS"
    return sym  # assume US or user-specified


def _compute_rsi(closes: pd.Series, period: int = 14) -> float:
    delta = closes.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1 / period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    rsi_series = 100 - (100 / (1 + rs))
    return float(rsi_series.iloc[-1]) if len(rsi_series) else 50.0


def _derive_trend_label(closes: pd.Series) -> str:
    if len(closes) < 50:
        return "Unknown"
    ema20 = closes.ewm(span=20, adjust=False).mean()
    ema50 = closes.ewm(span=50, adjust=False).mean()
    ema200 = (
        closes.ewm(span=200, adjust=False).mean() if len(closes) >= 200 else None
    )
    latest = float(closes.iloc[-1])
    above20 = latest > float(ema20.iloc[-1])
    above50 = latest > float(ema50.iloc[-1])
    above200 = ema200 is not None and latest > float(ema200.iloc[-1])
    if above20 and above50 and above200:
        return "Strong Uptrend"
    if above50 and above200:
        return "Uptrend"
    if (not above20) and (not above50) and ema200 is not None and not above200:
        return "Strong Downtrend"
    if (not above50) and ema200 is not None and not above200:
        return "Downtrend"
    return "Sideways"


def _derive_sentiment_label(change_pct: float) -> str:
    if change_pct is None:
        return "Neutral"
    if change_pct >= 1.0:
        return "Positive"
    if change_pct <= -1.0:
        return "Negative"
    return "Neutral"


def _fetch_live_stock_data(symbol: str, exchange: str) -> dict[str, Any]:
    """Synchronous yfinance fetch. Called via asyncio.to_thread from the route."""
    import yfinance as yf  # local import so the backend still starts if yfinance is missing

    yahoo_symbol = _normalize_yahoo_symbol(symbol, exchange)
    ticker = yf.Ticker(yahoo_symbol)

    history = ticker.history(period="1y", interval="1d", auto_adjust=False)
    if history is None or history.empty:
        raise HTTPException(
            status_code=404,
            detail=f"No market data found for '{yahoo_symbol}'. Check the symbol or exchange.",
        )

    closes = history["Close"].astype(float)
    volumes = history["Volume"].astype(float)

    last_price = float(closes.iloc[-1])
    prev_close_series = closes.iloc[-2] if len(closes) >= 2 else closes.iloc[-1]
    prev_close = float(prev_close_series)
    change_pct = ((last_price - prev_close) / prev_close * 100) if prev_close else 0.0

    week52_high = float(history["High"].astype(float).max())
    week52_low = float(history["Low"].astype(float).min())

    last_volume = float(volumes.iloc[-1])
    avg_volume_30 = float(volumes.tail(30).mean()) if len(volumes) >= 30 else float(volumes.mean())

    rsi_estimate = _compute_rsi(closes)
    trend_label = _derive_trend_label(closes)
    sentiment_label = _derive_sentiment_label(change_pct)

    # Fundamentals. yfinance's .info can fail / be rate-limited; best-effort.
    info: dict[str, Any] = {}
    try:
        info = ticker.info or {}
    except Exception as exc:
        logger.debug("yfinance info failed for %s: %s", yahoo_symbol, exc)

    def _num(key: str) -> Optional[float]:
        val = info.get(key)
        if isinstance(val, (int, float)) and not (val is None):
            return float(val)
        return None

    market_cap = _num("marketCap")
    pe_ratio = _num("trailingPE")
    book_value = _num("bookValue")
    dividend_yield_raw = _num("dividendYield")
    # yfinance returns 0.11 for 0.11% on some versions and 0.0011 (fraction) on others.
    dividend_yield = dividend_yield_raw
    if dividend_yield_raw is not None and dividend_yield_raw < 0.05:
        dividend_yield = dividend_yield_raw * 100  # fraction → percent

    roe = _num("returnOnEquity")
    if roe is not None and -1 <= roe <= 1:
        roe = roe * 100  # fraction → percent

    # Support / resistance: recent swing low/high over last 30 bars.
    recent = history.tail(30)
    support = float(recent["Low"].astype(float).min())
    resistance = float(recent["High"].astype(float).max())

    sector = info.get("sector") or SECTOR_MAP.get(symbol.upper(), "")
    industry = info.get("industry") or ""
    long_name = info.get("longName") or info.get("shortName") or symbol.upper()

    quote_type = (info.get("quoteType") or "").upper()
    currency = info.get("currency") or ("INR" if yahoo_symbol.endswith((".NS", ".BO")) else "USD")

    return {
        "symbol": symbol.upper(),
        "yahoo_symbol": yahoo_symbol,
        "name": long_name,
        "sector": sector,
        "industry": industry,
        "exchange": "NSE" if yahoo_symbol.endswith(".NS") else (
            "BSE" if yahoo_symbol.endswith(".BO") else exchange.upper() or "UNKNOWN"
        ),
        "currency": currency,
        "quote_type": quote_type or "EQUITY",
        "price": round(last_price, 2),
        "previous_close": round(prev_close, 2),
        "change_pct": round(change_pct, 2),
        "volume": int(last_volume),
        "avg_volume": int(avg_volume_30) if avg_volume_30 else 0,
        "week52_high": round(week52_high, 2),
        "week52_low": round(week52_low, 2),
        "market_cap": market_cap,
        "pe_ratio": pe_ratio,
        "book_value": book_value,
        "dividend_yield": dividend_yield,
        "roe": roe,
        "rsi_estimate": round(rsi_estimate, 2),
        "trend": trend_label,
        "sentiment": sentiment_label,
        "support": round(support, 2),
        "resistance": round(resistance, 2),
        "news_summary": "",
        "data_source": "yahoo_finance",
        "last_bar": str(history.index[-1].date()),
    }


@app.get("/stock-data/{symbol}")
async def stock_data(symbol: str, exchange: str = "NSE") -> dict[str, Any]:
    """Authoritative live data for a single symbol.

    Backed by Yahoo Finance (yfinance). Results cached in-memory for 60 seconds.
    Used by the Scanner tab in place of the old Claude-based fetch.
    """
    cache_key = f"{symbol.upper()}|{exchange.upper()}"
    cached = _cache_get(_stock_data_cache, cache_key, _stock_data_cache_ttl)
    if cached is not None:
        return cached

    try:
        payload = await asyncio.to_thread(_fetch_live_stock_data, symbol, exchange)
    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Stock data fetch failed for %s: %s", symbol, exc)
        raise HTTPException(status_code=502, detail=f"Upstream error: {exc}") from exc

    _cache_set(_stock_data_cache, cache_key, payload)
    return payload


@app.get("/deep-analyze/{symbol}")
async def deep_analyze_endpoint(
    symbol: str,
    exchange: str = "NSE",
    save: bool = True,
) -> dict[str, Any]:
    """Multi-factor deep analysis of a single symbol.

    Runs four independent gates (technical, fundamentals, backtest, risk/reward)
    and reports per-gate pass/fail with a combined 0-100 score. A symbol is only
    labelled `high_probability=true` when EVERY hard gate passes and the
    combined score is ≥ the configured minimum (default 90).

    By default every result is persisted to the local history DB so users can
    re-view it later via /deep-analysis-history. Pass `save=false` to skip.
    """
    result = await deep_analyze(symbol, exchange=exchange)
    history_id: Optional[int] = None
    if save:
        try:
            history_id = await save_history(result)
            result = {**result, "history_id": history_id}
        except Exception as exc:
            logger.warning("Saving deep-analysis history failed for %s: %s", symbol, exc)

    # Always surface the current market status so the UI can advise correctly.
    market = get_market_status()
    result = {**result, "market_status": market}

    # Auto paper-trade hook: opens a trade if settings allow and score ≥ threshold.
    try:
        auto_trade, auto_meta = await maybe_auto_paper_trade(symbol, result, history_id=history_id)
        result = {
            **result,
            "auto_paper_trade": auto_trade,
            "auto_paper_trade_status": auto_meta,
        }
    except Exception as exc:
        logger.warning("Auto paper-trade hook failed for %s: %s", symbol, exc)

    # High-probability Telegram ping — independent of whether auto-trade fired
    # (might be blocked by dedup / max-open but user still wants to know).
    try:
        score = int((result.get("overall") or {}).get("combined_score") or 0)
        if score > 0:
            asyncio.create_task(telegram_send_high_probability(symbol.upper(), score))
    except Exception as exc:
        logger.debug("telegram_send_high_probability scheduling failed: %s", exc)

    return result


# ---- Paper-trading endpoints ---------------------------------------------


class PaperOpenRequest(BaseModel):
    symbol: str
    entry_price: float = Field(..., gt=0)
    stop_loss: float = Field(..., gt=0)
    target_price: float = Field(..., gt=0)
    exchange: str = "NSE"
    notes: Optional[str] = None


class PaperCloseRequest(BaseModel):
    price: Optional[float] = Field(default=None, gt=0)
    reason: str = Field(default="MANUAL_CLOSE")


class PaperSettingsUpdate(BaseModel):
    auto_trade_enabled: Optional[bool] = None
    auto_trade_threshold: Optional[int] = Field(default=None, ge=0, le=100)
    auto_trade_market_open_only: Optional[bool] = None
    risk_per_trade_pct: Optional[float] = Field(default=None, gt=0, le=100)
    max_open_positions: Optional[int] = Field(default=None, ge=1, le=50)
    max_hold_days: Optional[int] = Field(default=None, ge=1, le=365)
    telegram_on_open: Optional[bool] = None
    telegram_on_close: Optional[bool] = None
    telegram_on_error: Optional[bool] = None
    telegram_on_high_probability: Optional[bool] = None
    telegram_high_probability_threshold: Optional[int] = Field(default=None, ge=0, le=100)
    telegram_daily_summary: Optional[bool] = None


@app.get("/paper-portfolio")
async def paper_portfolio() -> dict[str, Any]:
    return await paper_portfolio_snapshot()


@app.get("/paper-settings")
async def paper_settings_get() -> dict[str, Any]:
    return _paper_settings_snapshot()


@app.patch("/paper-settings")
async def paper_settings_patch(update: PaperSettingsUpdate) -> dict[str, Any]:
    payload = {k: v for k, v in update.model_dump().items() if v is not None}
    return _paper_settings_update(payload)


@app.get("/paper-trades")
async def list_paper_trades(
    status: Optional[str] = None,
    symbol: Optional[str] = None,
    limit: int = 100,
) -> dict[str, Any]:
    trades = await asyncio.to_thread(_list_trades_sync, status, symbol, limit)
    return {"count": len(trades), "items": trades}


@app.get("/paper-trades/{trade_id}")
async def get_paper_trade(trade_id: int) -> dict[str, Any]:
    trade = await asyncio.to_thread(_fetch_trade_sync, trade_id)
    if trade is None:
        raise HTTPException(status_code=404, detail=f"Paper trade {trade_id} not found.")
    return trade


@app.post("/paper-trades")
async def create_paper_trade(req: PaperOpenRequest) -> dict[str, Any]:
    try:
        return await open_paper_trade(
            req.symbol,
            req.entry_price,
            req.stop_loss,
            req.target_price,
            exchange=req.exchange,
            notes=req.notes,
            source="manual",
        )
    except PaperTradeError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/paper-trades/{trade_id}/close")
async def close_paper_trade_endpoint(trade_id: int, req: PaperCloseRequest) -> dict[str, Any]:
    try:
        return await close_paper_trade(trade_id, price=req.price, reason=req.reason or EXIT_MANUAL)
    except PaperTradeError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/paper-trades/monitor-now")
async def paper_monitor_now() -> dict[str, Any]:
    return await _monitor_open_positions_once()


# ---- Telegram endpoints --------------------------------------------------


@app.get("/telegram-status")
async def telegram_status_endpoint() -> dict[str, Any]:
    return {
        "configured": _tg_enabled(),
        "token_present": bool(TELEGRAM_BOT_TOKEN),
        "chat_id_present": bool(TELEGRAM_CHAT_ID),
        "token_prefix": f"{TELEGRAM_BOT_TOKEN[:6]}…" if TELEGRAM_BOT_TOKEN else None,
        "chat_id": TELEGRAM_CHAT_ID or None,
    }


@app.post("/telegram-test")
async def telegram_test_endpoint() -> dict[str, Any]:
    if not _tg_enabled():
        raise HTTPException(
            status_code=503,
            detail=(
                "Telegram is not configured. Add TELEGRAM_BOT_TOKEN to the vault "
                "(python scripts/setup_secrets.py --add-secret TELEGRAM_BOT_TOKEN=...) "
                "and set TELEGRAM_CHAT_ID in backend/.env."
            ),
        )
    result = await telegram_send_test()
    if not result.get("ok"):
        # Surface the actual Telegram error so the UI can show what's wrong.
        raise HTTPException(status_code=502, detail=result)
    return {"ok": True, "detail": result.get("detail")}


# ---- Equity curve --------------------------------------------------------


def _list_closed_trades_chrono_sync() -> list[dict[str, Any]]:
    """Return every CLOSED trade ordered by the moment it was closed."""
    with _db_connect() as conn:
        rows = conn.execute(
            """
            SELECT id, symbol, entry_price, close_price, quantity,
                   pnl_amount, pnl_pct, exit_reason, opened_at, closed_at
            FROM paper_trades
            WHERE status = 'CLOSED' AND closed_at IS NOT NULL
            ORDER BY closed_at ASC, id ASC
            """
        ).fetchall()
    return [dict(r) for r in rows]


async def build_equity_curve() -> dict[str, Any]:
    """Synthesize an equity + drawdown time series from the paper-trade ledger.

    Start point is the starting capital; each closed trade step-changes the
    running equity; the final point is a live "mark-to-market" that includes
    any currently-open positions' unrealised P&L.

    Drawdown at each point is `(equity - running_peak) / running_peak * 100`,
    always 0 or negative.
    """
    settings = _paper_settings_snapshot()
    starting_capital = float(settings["starting_capital"])
    closed = await asyncio.to_thread(_list_closed_trades_chrono_sync)

    points: list[dict[str, Any]] = []
    peak_equity = starting_capital
    max_drawdown_pct = 0.0
    max_drawdown_at: Optional[str] = None

    # Seed with the starting-capital anchor. We use the opened_at of the first
    # trade as the anchor timestamp so the chart doesn't start at epoch.
    if closed:
        first_open_iso = closed[0]["opened_at"] or closed[0]["closed_at"]
    else:
        first_open_iso = _now_iso()
    points.append(
        {
            "t": first_open_iso,
            "equity": round(starting_capital, 2),
            "realised": 0.0,
            "unrealised": 0.0,
            "peak": round(starting_capital, 2),
            "drawdown_pct": 0.0,
            "label": "START",
            "event": "start",
        }
    )

    running_equity = starting_capital
    running_realised = 0.0
    for trade in closed:
        pnl = float(trade.get("pnl_amount") or 0.0)
        running_realised += pnl
        running_equity += pnl
        peak_equity = max(peak_equity, running_equity)
        dd_pct = ((running_equity - peak_equity) / peak_equity * 100) if peak_equity else 0.0
        if dd_pct < max_drawdown_pct:
            max_drawdown_pct = dd_pct
            max_drawdown_at = trade["closed_at"]
        points.append(
            {
                "t": trade["closed_at"],
                "equity": round(running_equity, 2),
                "realised": round(running_realised, 2),
                "unrealised": 0.0,
                "peak": round(peak_equity, 2),
                "drawdown_pct": round(dd_pct, 2),
                "symbol": trade["symbol"],
                "pnl_amount": round(pnl, 2),
                "pnl_pct": float(trade.get("pnl_pct") or 0.0),
                "exit_reason": trade.get("exit_reason"),
                "event": "close",
            }
        )

    # Live mark-to-market tail: use the portfolio snapshot's unrealised figure.
    snapshot = await paper_portfolio_snapshot()
    unrealised = float(snapshot["capital"]["unrealised_pnl"])
    realised_from_snap = float(snapshot["capital"]["realised_pnl"])
    total_equity = float(snapshot["capital"]["total_equity"])

    # Guard: if nothing closed, running_realised = 0 but snapshot still has
    # realised_from_snap = 0 too; keep them consistent.
    current_peak = max(peak_equity, total_equity)
    current_dd = ((total_equity - current_peak) / current_peak * 100) if current_peak else 0.0
    if current_dd < max_drawdown_pct:
        max_drawdown_pct = current_dd
        max_drawdown_at = _now_iso()

    points.append(
        {
            "t": _now_iso(),
            "equity": round(total_equity, 2),
            "realised": round(realised_from_snap, 2),
            "unrealised": round(unrealised, 2),
            "peak": round(current_peak, 2),
            "drawdown_pct": round(current_dd, 2),
            "label": "MARK",
            "event": "mark",
            "open_positions": snapshot["positions"]["open_count"],
        }
    )

    return {
        "starting_capital": round(starting_capital, 2),
        "current_equity": round(total_equity, 2),
        "realised_pnl": round(realised_from_snap, 2),
        "unrealised_pnl": round(unrealised, 2),
        "peak_equity": round(current_peak, 2),
        "peak_return_pct": round((current_peak - starting_capital) / starting_capital * 100, 2)
        if starting_capital else 0.0,
        "current_return_pct": round((total_equity - starting_capital) / starting_capital * 100, 2)
        if starting_capital else 0.0,
        "current_drawdown_pct": round(current_dd, 2),
        "max_drawdown_pct": round(max_drawdown_pct, 2),
        "max_drawdown_at": max_drawdown_at,
        "closed_count": len(closed),
        "points": points,
    }


@app.get("/paper-equity-curve")
async def paper_equity_curve() -> dict[str, Any]:
    """Equity-curve time series for the Paper Trading Desk chart."""
    return await build_equity_curve()


@app.get("/market-status")
async def market_status_endpoint() -> dict[str, Any]:
    """NSE regular-session status (IST-aware, holiday-aware via exchange_calendars XBOM)."""
    return get_market_status()


@app.get("/market-holidays")
async def market_holidays(year: Optional[int] = None) -> dict[str, Any]:
    """List NSE trading holidays for a given calendar year (default: current year)."""
    target_year = int(year) if year else _ist_now().year
    holidays = _nse_holidays_in_year(target_year)
    return {
        "year": target_year,
        "count": len(holidays),
        "source": "XBOM via exchange_calendars" if _XBOM_CAL else "bundled fallback",
        "holidays": holidays,
    }


# ---- History endpoints ----------------------------------------------------


@app.get("/deep-analysis-history")
async def list_deep_analysis_history(
    limit: int = 50,
    offset: int = 0,
    symbol: Optional[str] = None,
    high_probability_only: bool = False,
) -> dict[str, Any]:
    """List persisted deep-analysis runs, newest first.

    Query params:
      * `limit`  — 1..500, default 50
      * `offset` — 0-based pagination cursor
      * `symbol` — filter (case-insensitive exact match)
      * `high_probability_only` — only return runs flagged high_probability
    """
    return await asyncio.to_thread(
        _history_list_sync, limit, offset, symbol, high_probability_only
    )


@app.get("/deep-analysis-history/stats")
async def deep_analysis_history_stats() -> dict[str, Any]:
    return await asyncio.to_thread(_history_stats_sync)


@app.get("/deep-analysis-history/{item_id}")
async def get_deep_analysis_history(item_id: int) -> dict[str, Any]:
    item = await asyncio.to_thread(_history_get_sync, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail=f"History entry {item_id} not found.")
    return item


@app.delete("/deep-analysis-history/{item_id}")
async def delete_deep_analysis_history(item_id: int) -> dict[str, Any]:
    deleted = await asyncio.to_thread(_history_delete_sync, item_id)
    if not deleted:
        raise HTTPException(status_code=404, detail=f"History entry {item_id} not found.")
    return {"deleted": True, "id": item_id}


@app.delete("/deep-analysis-history")
async def clear_deep_analysis_history(symbol: Optional[str] = None) -> dict[str, Any]:
    removed = await asyncio.to_thread(_history_clear_sync, symbol)
    return {"deleted": removed, "symbol": symbol}


# ---- High-probability universe scan --------------------------------------


_deep_scan_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_deep_scan_cache_ttl = 900  # 15 minutes


async def _run_deep_scan(max_symbols: int | None = None) -> dict[str, Any]:
    """Run deep_analyze over the NIFTY universe with controlled concurrency.

    Returns every stock that passes ALL hard gates AND has combined score ≥ 90.
    Sorted best-first. Also returns the top-scoring near-miss (for diagnostics).
    """
    cached = _cache_get(_deep_scan_cache, "latest", _deep_scan_cache_ttl)
    if cached is not None:
        return cached

    universe = NIFTY_UNIVERSE if max_symbols is None else NIFTY_UNIVERSE[:max_symbols]
    sem = asyncio.Semaphore(MAX_CONCURRENT_FETCHES)

    async def one(sym: str) -> Optional[dict[str, Any]]:
        async with sem:
            try:
                return await deep_analyze(sym, "NSE")
            except HTTPException:
                return None
            except Exception as exc:
                logger.debug("Deep analyze failed for %s: %s", sym, exc)
                return None

    results = await asyncio.gather(*(one(s) for s in universe))
    evaluated = [r for r in results if r is not None]
    winners = [r for r in evaluated if r["overall"]["high_probability"]]
    near_miss_pool = [r for r in evaluated if not r["overall"]["high_probability"]]
    near_miss_pool.sort(key=lambda r: r["overall"]["combined_score"], reverse=True)
    winners.sort(key=lambda r: r["overall"]["combined_score"], reverse=True)

    response = {
        "scan_timestamp": datetime.now(timezone.utc).isoformat(),
        "universe_size": len(universe),
        "analysed": len(evaluated),
        "high_probability_count": len(winners),
        "high_probability": winners,
        "near_misses": [
            {
                "symbol": r["symbol"],
                "combined_score": r["overall"]["combined_score"],
                "gates_failed": [
                    name for name, gate in r["gates"].items() if not gate["passed"]
                ],
            }
            for r in near_miss_pool[:10]
        ],
        "gates_threshold": COMBINED_MIN_SCORE,
    }
    _cache_set(_deep_scan_cache, "latest", response)
    return response


@app.post("/high-probability-scan")
async def high_probability_scan(max_symbols: int = 0) -> dict[str, Any]:
    """Scan the NIFTY universe with the strict multi-factor gate.

    Query parameter `max_symbols` caps how many symbols to evaluate. Pass 0 (the
    default) to scan the entire universe. Cached for 15 minutes.
    """
    cap = max_symbols if max_symbols > 0 else None
    return await _run_deep_scan(max_symbols=cap)


@app.post("/scan-best-stock", response_model=ScanResponse)
async def scan_best_stock() -> ScanResponse:
    """Return the single highest-conviction winner that passes every gate.

    Delegates to the deep scan; if no symbol clears the 90-score bar it returns
    a `no_trade` payload with a reason (how many were analysed, what failed).
    """
    deep = await _run_deep_scan()
    winners = deep.get("high_probability") or []
    now_iso = datetime.now(timezone.utc).isoformat()

    if not winners:
        return ScanResponse(
            trade_found=False,
            no_trade=NoTradeResult(
                message="No trade opportunity today.",
                reason=(
                    f"Analysed {deep.get('analysed', 0)} of {deep.get('universe_size', 0)} "
                    f"NIFTY stocks through the strict 4-gate filter (technical, fundamentals, "
                    f"backtest, risk/reward). None cleared the combined-score floor of "
                    f"{COMBINED_MIN_SCORE}/100."
                ),
                scan_timestamp=now_iso,
                stocks_scanned=deep.get("analysed", 0),
            ),
        )

    best = winners[0]
    lvl = best["levels"]
    ind = best["indicators"]
    trade = TradeResult(
        symbol=best["symbol"],
        entry_price=lvl["entry"],
        stop_loss=lvl["stop"],
        target_price=lvl["target_1"],
        expected_return=lvl["expected_return_pct"],
        confidence_score=int(best["overall"]["combined_score"]),
        analysis=best["ai_analysis"],
        pattern_detected=best["patterns"]["name"],
        rsi=ind["rsi"],
        macd_signal="Bullish" if ind["macd"] > ind["macd_signal"] else "Bearish",
        volume_ratio=ind["volume_ratio"],
        ema_20=ind["ema_20"],
        ema_50=ind["ema_50"],
        ema_200=ind["ema_200"],
        atr=ind["atr"],
        risk_reward=lvl["risk_reward"],
        sector=best["sector"],
        scan_timestamp=now_iso,
    )
    return ScanResponse(trade_found=True, result=trade)


# ---------------------------------------------------------------------------
# ML Model Training (Phase 2)
# ---------------------------------------------------------------------------

# Representative NIFTY 500 symbols for training data when no list is passed
_ML_DEFAULT_SYMBOLS = [
    "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
    "HINDUNILVR", "SBIN", "BHARTIARTL", "ITC", "KOTAKBANK",
    "LT", "AXISBANK", "ASIANPAINT", "WIPRO", "MARUTI",
    "TITAN", "SUNPHARMA", "BAJFINANCE", "NESTLEIND", "TECHM",
]

_NIFTY500_TICKERS_FOR_ML = [s + ".NS" for s in _ML_DEFAULT_SYMBOLS]


def _ema_series(s: pd.Series, period: int) -> pd.Series:
    return s.ewm(span=period, adjust=False).mean()


def _rsi_series(s: pd.Series, period: int = 14) -> pd.Series:
    delta = s.diff()
    gain = delta.clip(lower=0).rolling(period).mean()
    loss = (-delta.clip(upper=0)).rolling(period).mean()
    rs = gain / loss.replace(0, np.nan)
    return 100 - 100 / (1 + rs)


def _build_ml_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute feature matrix from OHLCV dataframe.

    Returns a DataFrame with features + binary label ``is_profitable`` (1 if
    forward 20-bar return > 3%, else 0). Drops rows where any feature is NaN.
    """
    close = df["Close"].astype(float)
    high  = df["High"].astype(float)
    low   = df["Low"].astype(float)
    vol   = df["Volume"].astype(float)

    ema20  = _ema_series(close, 20)
    ema50  = _ema_series(close, 50)
    ema200 = _ema_series(close, 200)

    # MACD
    exp12 = _ema_series(close, 12)
    exp26 = _ema_series(close, 26)
    macd  = exp12 - exp26
    signal_line = _ema_series(macd, 9)
    macd_hist = macd - signal_line

    # RSI
    rsi14 = _rsi_series(close, 14)

    # ATR
    prev_close = close.shift(1)
    tr = pd.concat([
        high - low,
        (high - prev_close).abs(),
        (low  - prev_close).abs(),
    ], axis=1).max(axis=1)
    atr14 = tr.rolling(14).mean()
    atr_pct = atr14 / close

    # Volume ratio (vs 20-bar average)
    vol_avg = vol.rolling(20).mean()
    volume_ratio = vol / vol_avg.replace(0, np.nan)

    # Pattern strength: % above 20-day EMA
    pattern_strength = (close - ema20) / ema20 * 100

    # Breakout-of-structure flag: new 20-day high
    bos_flag = (close >= close.rolling(20).max().shift(1)).astype(int)

    # Price vs EMAs
    price_above_200ema = (close > ema200).astype(int)

    # VWAP estimate (proxy: typical price / typical-price EMA)
    typical_price = (high + low + close) / 3
    vwap_est = typical_price.ewm(span=20, adjust=False).mean()
    vwap_ratio = close / vwap_est

    # MACD above signal
    macd_above_signal = (macd > signal_line).astype(int)

    feat = pd.DataFrame({
        "rsi_14":            rsi14,
        "macd_hist":         macd_hist,
        "ema_20_50_ratio":   ema20 / ema50,
        "ema_50_200_ratio":  ema50 / ema200,
        "volume_ratio":      volume_ratio,
        "atr_pct":           atr_pct,
        "pattern_strength":  pattern_strength,
        "bos_flag":          bos_flag,
        "price_above_200ema": price_above_200ema,
        "vwap_ratio":        vwap_ratio,
        "macd_above_signal": macd_above_signal,
    }, index=df.index)

    # Label: 1 if forward 20-bar close return > 3 %
    fwd_return = close.shift(-20) / close - 1
    feat["is_profitable"] = (fwd_return > 0.03).astype(int)

    # Drop the last 20 rows (no forward return yet) and any NaN rows
    feat = feat.iloc[:-20].dropna()
    return feat


async def _train_ml_models(
    symbols: list[str],
    lookback_days: int = 750,
    forward_days: int = 20,
    target_return_pct: float = 3.0,
) -> dict[str, Any]:
    """Fetch OHLCV for each symbol, engineer features, train 4 classifiers with
    TimeSeriesSplit CV, and return a payload matching the frontend ml state shape."""
    try:
        import time as _time
        from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
        from sklearn.metrics import (
            accuracy_score, f1_score, precision_score, recall_score, roc_auc_score,
        )
        from sklearn.model_selection import TimeSeriesSplit
        import xgboost as xgb
        import lightgbm as lgb
    except ImportError as exc:
        raise HTTPException(
            status_code=503,
            detail=f"ML packages not installed. Run: pip install scikit-learn xgboost lightgbm joblib. Error: {exc}",
        )

    # Fetch OHLCV for all symbols in parallel (reuse fetch_ohlcv which already
    # handles retries/fallbacks). Limit concurrency to 5.
    sem = asyncio.Semaphore(5)

    async def _fetch(sym: str) -> Optional[pd.DataFrame]:
        async with sem:
            async with httpx.AsyncClient(timeout=30) as client:
                return await fetch_ohlcv(client, sym)

    tasks = [_fetch(s) for s in symbols]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    frames: list[pd.DataFrame] = []
    for sym, res in zip(symbols, results):
        if isinstance(res, pd.DataFrame) and len(res) >= 250:
            try:
                feat = _build_ml_features(res.tail(lookback_days + forward_days))
                if len(feat) >= 50:
                    frames.append(feat)
            except Exception as e:
                logger.warning("ML feature build failed for %s: %s", sym, e)

    if not frames:
        raise HTTPException(status_code=422, detail="Not enough data to train ML models.")

    combined = pd.concat(frames, ignore_index=True)
    combined = combined.sample(frac=1, random_state=42).reset_index(drop=True)  # shuffle

    feature_cols = [c for c in combined.columns if c != "is_profitable"]
    X = combined[feature_cols].values
    y = combined["is_profitable"].values

    tscv = TimeSeriesSplit(n_splits=5)

    classifiers = {
        "XGBoost":            xgb.XGBClassifier(n_estimators=200, max_depth=4, learning_rate=0.05, use_label_encoder=False, eval_metric="logloss", random_state=42, n_jobs=-1),
        "LightGBM":           lgb.LGBMClassifier(n_estimators=200, max_depth=4, learning_rate=0.05, random_state=42, n_jobs=-1, verbose=-1),
        "RandomForest":       RandomForestClassifier(n_estimators=200, max_depth=8, random_state=42, n_jobs=-1),
        "GradientBoosting":   GradientBoostingClassifier(n_estimators=100, max_depth=4, learning_rate=0.05, random_state=42),
    }

    model_results: list[dict[str, Any]] = []
    best_importance: dict[str, float] = {}

    for name, clf in classifiers.items():
        fold_metrics: list[dict] = []
        fold_importances: list[np.ndarray] = []
        t0 = _time.perf_counter()

        for train_idx, val_idx in tscv.split(X):
            X_tr, X_val = X[train_idx], X[val_idx]
            y_tr, y_val = y[train_idx], y[val_idx]
            if len(np.unique(y_tr)) < 2:
                continue
            clf.fit(X_tr, y_tr)
            y_pred = clf.predict(X_val)
            try:
                y_prob = clf.predict_proba(X_val)[:, 1]
                auc = roc_auc_score(y_val, y_prob)
            except Exception:
                auc = 0.5
            fold_metrics.append({
                "acc":  accuracy_score(y_val, y_pred),
                "prec": precision_score(y_val, y_pred, zero_division=0),
                "rec":  recall_score(y_val, y_pred, zero_division=0),
                "f1":   f1_score(y_val, y_pred, zero_division=0),
                "auc":  auc,
            })
            if hasattr(clf, "feature_importances_"):
                fold_importances.append(clf.feature_importances_)

        elapsed = round(_time.perf_counter() - t0, 2)
        if not fold_metrics:
            continue

        avg = {k: float(np.mean([fm[k] for fm in fold_metrics])) for k in fold_metrics[0]}
        model_results.append({
            "name":         name,
            "acc":          round(avg["acc"],  4),
            "prec":         round(avg["prec"], 4),
            "rec":          round(avg["rec"],  4),
            "f1":           round(avg["f1"],   4),
            "auc":          round(avg["auc"],  4),
            "train_time_s": elapsed,
            "best":         False,
        })
        if fold_importances:
            mean_imp = np.mean(fold_importances, axis=0)
            if name not in best_importance or avg["auc"] > max(
                mr["auc"] for mr in model_results[:-1] if mr["name"] == name
            ):
                best_importance = dict(zip(feature_cols, mean_imp.tolist()))

    if not model_results:
        raise HTTPException(status_code=422, detail="All models failed to train.")

    # Mark best (highest AUC)
    best_idx = max(range(len(model_results)), key=lambda i: model_results[i]["auc"])
    model_results[best_idx]["best"] = True
    best_model_name = model_results[best_idx]["name"]
    best_auc = model_results[best_idx]["auc"]

    # Feature importance from best model
    total_imp = sum(best_importance.values()) or 1
    direction_map = {
        "rsi_14":             "bullish",
        "macd_hist":          "bullish",
        "ema_20_50_ratio":    "bullish",
        "ema_50_200_ratio":   "bullish",
        "volume_ratio":       "bullish",
        "atr_pct":            "neutral",
        "pattern_strength":   "bullish",
        "bos_flag":           "bullish",
        "price_above_200ema": "bullish",
        "vwap_ratio":         "bullish",
        "macd_above_signal":  "bullish",
    }
    features = [
        {
            "name":       col,
            "importance": round((imp / total_imp) * 100, 2),
            "direction":  direction_map.get(col, "neutral"),
        }
        for col, imp in sorted(best_importance.items(), key=lambda kv: kv[1], reverse=True)
    ]

    trained_at = datetime.now(timezone.utc).isoformat()
    period_label = f"Last {len(combined)} rows · {len(symbols)} symbols"
    payload = {
        "models":           model_results,
        "features":         features,
        "dataset_size":     len(combined),
        "training_period":  period_label,
        "cv_folds":         5,
        "best_threshold":   0.5,
        "analysis_note":    (
            f"Trained on {len(combined)} samples from {len(symbols)} NSE symbols. "
            f"Best model: {best_model_name} (AUC {best_auc:.3f}). "
            f"Label: 20-bar forward return > {target_return_pct}%."
        ),
        "trained_at":       trained_at,
    }

    # Persist to DB
    with _db_lock, _db_connect() as conn:
        conn.execute(
            """
            INSERT INTO ml_training_runs
              (trained_at, symbols_used, dataset_size, training_period, best_model, best_auc, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (trained_at, len(symbols), len(combined), period_label, best_model_name, best_auc, json.dumps(payload)),
        )

    return payload


class MLTrainRequest(BaseModel):
    symbols: Optional[list[str]] = None
    lookback_days: int = Field(default=750, ge=100, le=2000)
    forward_days: int = Field(default=20, ge=5, le=60)
    target_return_pct: float = Field(default=3.0, ge=0.5, le=20.0)


@app.post("/train-ml")
async def train_ml(body: MLTrainRequest) -> dict[str, Any]:
    """Train XGBoost, LightGBM, RandomForest and GradientBoosting on real NSE OHLCV data.

    Returns a payload matching the frontend ``ml`` state shape:
    ``{ models, features, dataset_size, training_period, cv_folds, best_threshold, analysis_note, trained_at }``
    """
    symbols = body.symbols or _ML_DEFAULT_SYMBOLS
    # Normalise: strip .NS suffix if present, take up to 20
    symbols = [s.upper().replace(".NS", "").replace(".BSE", "").strip() for s in symbols[:20]]
    return await _train_ml_models(
        symbols,
        lookback_days=body.lookback_days,
        forward_days=body.forward_days,
        target_return_pct=body.target_return_pct,
    )


@app.get("/train-ml/latest")
async def train_ml_latest() -> dict[str, Any]:
    """Return the most recent training run payload, or 404 if none exists."""
    with _db_connect() as conn:
        row = conn.execute(
            "SELECT payload FROM ml_training_runs ORDER BY id DESC LIMIT 1"
        ).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="No training run found.")
    return json.loads(row["payload"])


@app.get("/train-ml/history")
async def train_ml_history() -> dict[str, Any]:
    """Return the last 10 training run summaries (no full payload)."""
    with _db_connect() as conn:
        rows = conn.execute(
            """
            SELECT id, trained_at, symbols_used, dataset_size, training_period, best_model, best_auc
            FROM ml_training_runs ORDER BY id DESC LIMIT 10
            """
        ).fetchall()
    return {"runs": [dict(r) for r in rows]}


# ---------------------------------------------------------------------------
# WebSocket — live price feed (Phase 3)
# ---------------------------------------------------------------------------


@app.websocket("/ws/live-prices")
async def ws_live_prices(websocket: WebSocket) -> None:
    """WebSocket endpoint for live NSE price streaming.

    Protocol
    --------
    Client → Server (JSON):
      ``{"action": "subscribe",   "symbols": ["RELIANCE", "TCS"]}``
      ``{"action": "unsubscribe", "symbols": ["RELIANCE"]}``

    Server → Client (JSON):
      ``{"type": "price_update", "symbol": "RELIANCE", "price": 2850.5,
         "change_pct": 0.45, "volume": 1234567, "ts": "..."}``
      ``{"type": "error", "message": "..."}``
    """
    await websocket.accept()
    try:
        while True:
            msg = await websocket.receive_json()
            action = msg.get("action", "")
            symbols = [s.upper().strip() for s in (msg.get("symbols") or []) if s]
            if action == "subscribe" and symbols:
                await _price_stream.connect(websocket, symbols)
                await websocket.send_json({"type": "subscribed", "symbols": symbols})
            elif action == "unsubscribe" and symbols:
                await _price_stream.unsubscribe(websocket, symbols)
                await websocket.send_json({"type": "unsubscribed", "symbols": symbols})
            else:
                await websocket.send_json({"type": "error", "message": f"Unknown action: {action!r}"})
    except WebSocketDisconnect:
        pass
    except Exception as exc:
        logger.debug("WebSocket client error: %s", exc)
    finally:
        await _price_stream.disconnect(websocket)


# ---------------------------------------------------------------------------
# Angel One SmartAPI broker integration
# ---------------------------------------------------------------------------

# SmartAPI optional imports (graceful degradation when not installed).
try:
    from SmartApi import SmartConnect as _SmartConnect  # type: ignore
    import pyotp as _pyotp  # type: ignore
    _SMARTAPI_OK = True
except ImportError:
    _SmartConnect = None  # type: ignore
    _pyotp = None  # type: ignore
    _SMARTAPI_OK = False
    logger.warning("smartapi-python / pyotp not installed. Broker features disabled.")

# --------------- In-memory session state ---------------
_broker_lock = asyncio.Lock()
_broker_session: dict = {
    "connected": False,
    "client_id": None,
    "auth_token": None,
    "feed_token": None,
    "refresh_token": None,
    "last_connected_at": None,
    "obj": None,          # SmartConnect instance (or None)
}


def _broker_keys_configured() -> bool:
    return bool(ANGEL_CLIENT_ID and ANGEL_PASSWORD and ANGEL_TOTP_SECRET and ANGEL_API_KEY)


async def _get_smart_obj():
    """Return the current authenticated SmartConnect object, or raise 503."""
    async with _broker_lock:
        if not _broker_session["connected"] or _broker_session["obj"] is None:
            raise HTTPException(status_code=503, detail="Broker not connected. Call POST /broker/connect first.")
        return _broker_session["obj"]


# --------------- Endpoints ---------------

@app.get("/broker/status")
async def broker_status():
    """Return connection status and key-configuration flag."""
    async with _broker_lock:
        return {
            "connected": _broker_session["connected"],
            "client_id": _broker_session["client_id"],
            "last_connected_at": _broker_session["last_connected_at"],
            "keys_configured": _broker_keys_configured(),
            "smartapi_installed": _SMARTAPI_OK,
        }


@app.post("/broker/connect")
async def broker_connect():
    """Authenticate with Angel One via TOTP and persist the session."""
    if not _SMARTAPI_OK:
        raise HTTPException(status_code=501, detail="smartapi-python not installed. Run: pip install smartapi-python pyotp")
    if not _broker_keys_configured():
        raise HTTPException(status_code=400, detail="Angel One credentials not configured. Add them to the vault.")
    try:
        obj = _SmartConnect(api_key=ANGEL_API_KEY)
        totp_val = _pyotp.TOTP(ANGEL_TOTP_SECRET).now()
        data = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: obj.generateSession(ANGEL_CLIENT_ID, ANGEL_PASSWORD, totp_val),
        )
        if not data or data.get("status") is False:
            msg = data.get("message", "Authentication failed") if data else "No response from Angel One"
            raise HTTPException(status_code=401, detail=msg)
        session_data = data.get("data", {})
        auth_token = session_data.get("jwtToken", "")
        refresh_token = session_data.get("refreshToken", "")
        feed_token = await asyncio.get_event_loop().run_in_executor(None, obj.getfeedToken)
        async with _broker_lock:
            _broker_session.update({
                "connected": True,
                "client_id": ANGEL_CLIENT_ID,
                "auth_token": auth_token,
                "feed_token": feed_token,
                "refresh_token": refresh_token,
                "last_connected_at": _now_iso(),
                "obj": obj,
            })
        logger.info("Broker connected for client %s", ANGEL_CLIENT_ID)
        return {"ok": True, "message": f"Connected as {ANGEL_CLIENT_ID}"}
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Broker connect failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/broker/disconnect")
async def broker_disconnect():
    """Terminate the Angel One session."""
    async with _broker_lock:
        obj = _broker_session.get("obj")
        if obj is not None:
            try:
                await asyncio.get_event_loop().run_in_executor(None, obj.terminateSession, ANGEL_CLIENT_ID)
            except Exception:
                pass  # log but don't fail
        _broker_session.update({
            "connected": False,
            "client_id": None,
            "auth_token": None,
            "feed_token": None,
            "refresh_token": None,
            "obj": None,
        })
    logger.info("Broker disconnected.")
    return {"ok": True}


@app.get("/broker/funds")
async def broker_funds():
    obj = await _get_smart_obj()
    try:
        data = await asyncio.get_event_loop().run_in_executor(None, obj.rmsLimit)
        if not data or data.get("status") is False:
            raise HTTPException(status_code=502, detail=data.get("message", "Failed to fetch funds"))
        d = data.get("data", {})
        net = float(d.get("net", 0) or 0)
        available = float(d.get("availablecash", 0) or 0)
        used = float(d.get("utiliseddebits", d.get("utilisedmargin", 0)) or 0)
        return {"net": net, "available_cash": available, "used_margin": used, "raw": d}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/broker/positions")
async def broker_positions():
    obj = await _get_smart_obj()
    try:
        data = await asyncio.get_event_loop().run_in_executor(None, obj.position)
        if not data or data.get("status") is False:
            raise HTTPException(status_code=502, detail=data.get("message", "Failed to fetch positions"))
        raw = data.get("data") or []
        positions = []
        for p in raw:
            qty = int(p.get("netqty", 0) or 0)
            if qty == 0:
                continue
            avg = float(p.get("netavgprice", p.get("avgnetprice", 0)) or 0)
            ltp = float(p.get("ltp", 0) or 0)
            pnl = float(p.get("unrealised", p.get("pnl", 0)) or 0)
            pnl_pct = round((pnl / (avg * abs(qty)) * 100), 2) if avg and qty else 0
            positions.append({
                "symbol": p.get("tradingsymbol", ""),
                "qty": qty,
                "avgprice": avg,
                "ltp": ltp,
                "pnl": pnl,
                "pnlpercent": pnl_pct,
                "exchange": p.get("exchange", "NSE"),
                "producttype": p.get("producttype", ""),
            })
        return {"positions": positions}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/broker/holdings")
async def broker_holdings():
    obj = await _get_smart_obj()
    try:
        data = await asyncio.get_event_loop().run_in_executor(None, obj.holding)
        if not data or data.get("status") is False:
            raise HTTPException(status_code=502, detail=data.get("message", "Failed to fetch holdings"))
        raw = data.get("data") or []
        holdings = []
        for h in raw:
            qty = int(h.get("quantity", 0) or 0)
            avg = float(h.get("averageprice", 0) or 0)
            ltp = float(h.get("ltp", 0) or 0)
            pnl = float(h.get("profitandloss", 0) or 0)
            val = qty * ltp
            holdings.append({
                "tradingsymbol": h.get("tradingsymbol", ""),
                "quantity": qty,
                "averageprice": avg,
                "ltp": ltp,
                "holdingvalue": val,
                "profitandloss": pnl,
                "exchange": h.get("exchange", "NSE"),
                "isin": h.get("isin", ""),
            })
        return {"holdings": holdings}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/broker/orders")
async def broker_orders_list():
    obj = await _get_smart_obj()
    try:
        data = await asyncio.get_event_loop().run_in_executor(None, obj.orderBook)
        if not data or data.get("status") is False:
            # empty order book is not an error
            if data and "No Record" in data.get("message", ""):
                return {"orders": []}
            raise HTTPException(status_code=502, detail=data.get("message", "Failed to fetch orders") if data else "No response")
        raw = data.get("data") or []
        orders = []
        for o in raw:
            orders.append({
                "order_id": o.get("orderid", ""),
                "symbol": o.get("tradingsymbol", ""),
                "side": o.get("transactiontype", ""),
                "qty": int(o.get("quantity", 0) or 0),
                "price": float(o.get("price", 0) or 0),
                "order_type": o.get("ordertype", ""),
                "status": o.get("status", ""),
                "placed_at": o.get("updatetime", o.get("exchtime", "")),
                "exchange": o.get("exchange", "NSE"),
            })
        return {"orders": orders}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


class BrokerOrderRequest(BaseModel):
    symbol: str
    side: str                     # BUY | SELL
    qty: int
    order_type: str = "MARKET"    # MARKET | LIMIT | SL
    price: float = 0.0
    exchange: str = "NSE"
    product_type: str = "INTRADAY"  # INTRADAY | DELIVERY | CARRYFORWARD


@app.post("/broker/order")
async def broker_place_order(req: BrokerOrderRequest):
    obj = await _get_smart_obj()
    try:
        params = {
            "variety": "NORMAL",
            "tradingsymbol": req.symbol.upper(),
            "symboltoken": "",          # Angel One resolves by symbol name
            "transactiontype": req.side.upper(),
            "exchange": req.exchange.upper(),
            "ordertype": req.order_type.upper(),
            "producttype": req.product_type.upper(),
            "duration": "DAY",
            "price": str(req.price) if req.order_type.upper() != "MARKET" else "0",
            "triggerprice": "0",
            "squareoff": "0",
            "stoploss": "0",
            "quantity": str(req.qty),
        }
        result = await asyncio.get_event_loop().run_in_executor(None, lambda: obj.placeOrder(params))
        if not result or result.get("status") is False:
            msg = result.get("message", "Order rejected") if result else "No response"
            raise HTTPException(status_code=400, detail=msg)
        order_id = result.get("data", {}).get("orderid", "") if isinstance(result.get("data"), dict) else result.get("data", "")
        # Persist to local DB
        with _db() as conn:
            conn.execute(
                """INSERT INTO broker_orders
                   (order_id, symbol, exchange, side, qty, price, order_type, status, placed_at, response)
                   VALUES (?,?,?,?,?,?,?,?,?,?)""",
                (str(order_id), req.symbol.upper(), req.exchange.upper(), req.side.upper(),
                 req.qty, req.price, req.order_type.upper(), "PENDING", _now_iso(), json.dumps(result)),
            )
        logger.info("Broker order placed: %s %s %s x%d → order_id=%s", req.side, req.symbol, req.order_type, req.qty, order_id)
        return {"ok": True, "order_id": order_id, "message": result.get("message", "Order placed")}
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Broker place order failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/broker/sync-paper/{trade_id}")
async def broker_sync_paper(trade_id: int):
    """Replicate a paper trade as a real Angel One order."""
    with _db() as conn:
        row = conn.execute(
            "SELECT symbol, side, qty, entry_price, status FROM paper_trades WHERE id=?",
            (trade_id,),
        ).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Paper trade not found")
    if row["status"] != "open":
        raise HTTPException(status_code=400, detail="Only open paper trades can be synced")
    req = BrokerOrderRequest(
        symbol=row["symbol"],
        side=row["side"].upper(),
        qty=row["qty"],
        order_type="MARKET",
        price=0.0,
    )
    result = await broker_place_order(req)
    # Link the broker order to the paper trade
    with _db() as conn:
        conn.execute(
            "UPDATE broker_orders SET paper_trade_id=? WHERE order_id=?",
            (trade_id, result.get("order_id", "")),
        )
    return {**result, "paper_trade_id": trade_id}


# ---------------------------------------------------------------------------
# Static frontend (production)
# ---------------------------------------------------------------------------
#
# In development you run the Vite dev server on :3000 and FastAPI on :8000; the
# frontend hits the backend cross-origin. In production the Docker image bakes
# the built React bundle into `frontend/dist/` and we serve it here on the same
# port — so users hit a single URL and `API_BASE` can be empty (same origin).
#
# MUST be mounted LAST, after every API route + the /unlock, /lock-status,
# /market-status, /market-holidays etc. endpoints have been registered.
# Otherwise FastAPI's route resolution would give the SPA priority over API
# paths and every API call would return index.html.


FRONTEND_DIST_DIRS: list[Path] = [
    # When the Docker image is built, frontend/dist/ sits one level above backend/
    # (see Dockerfile). When running uvicorn locally from backend/ the copy lives
    # at ../frontend/dist/.
    Path(__file__).resolve().parent.parent / "frontend" / "dist",
    # Also check an in-place copy if someone has run `npm run build` into
    # backend/static/ for a single-folder deploy.
    Path(__file__).resolve().parent / "static",
]


def _mount_frontend_if_present() -> None:
    for dist in FRONTEND_DIST_DIRS:
        if (dist / "index.html").exists():
            from fastapi.responses import FileResponse
            from fastapi.staticfiles import StaticFiles

            index_file = str(dist / "index.html")

            # SPA catch-all: for any unknown path that's NOT an API route AND
            # isn't a known asset, return index.html so client-side routing
            # (e.g. React Router) works after a browser refresh.
            @app.get("/{full_path:path}", include_in_schema=False)
            async def _spa_fallback(full_path: str):
                if _is_api_path("/" + full_path):
                    # let the normal API router handle it (will 404 via FastAPI)
                    raise HTTPException(status_code=404, detail="Not Found")
                return FileResponse(index_file)

            # Mount StaticFiles AFTER the fallback decorator so specific files
            # (CSS/JS/img under /assets/*) still resolve to their real content
            # while the SPA fallback handles route-style paths.
            app.mount("/", StaticFiles(directory=str(dist), html=True), name="frontend")
            logger.info("Mounted built frontend from %s", dist)
            return
    logger.info(
        "No built frontend found at %s — running API-only "
        "(run `npm run build` in frontend/ and restart to serve the dashboard from the same port).",
        " or ".join(str(p) for p in FRONTEND_DIST_DIRS),
    )


_mount_frontend_if_present()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
