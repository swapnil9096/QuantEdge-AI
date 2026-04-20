import { API_BASE, TOKEN_KEY } from '../constants.js';
import { fmt, fmtPct } from './format.js';
import { parseSymbol } from './indicators.js';

// ---------------------------------------------------------------------------
// Fetch interceptor — auto-adds Authorization header + handles 401 locked
// ---------------------------------------------------------------------------

export function installFetchInterceptor({ onLocked }) {
  const orig = window.fetch.bind(window);
  if (window.__quantedge_fetch_patched__) return () => {};
  window.__quantedge_fetch_patched__ = true;

  window.fetch = async (input, init = {}) => {
    const url = typeof input === 'string' ? input : input?.url || '';
    const isBackend =
      (API_BASE && url.startsWith(API_BASE)) ||
      (!API_BASE && (url.startsWith('/') || (!url.startsWith('http') && !url.startsWith('//'))));

    // Snapshot the token BEFORE the request is sent.
    // This is the key to avoiding the stale-401 race condition:
    // if the user logs in while a pre-login request is in-flight, the 401
    // response arrives AFTER the new token is stored.  By comparing the
    // token we sent with the one currently in storage, we can tell whether
    // a new login happened and skip the logout in that case.
    const tokenAtRequestTime = isBackend ? localStorage.getItem(TOKEN_KEY) : null;

    if (isBackend && tokenAtRequestTime) {
      init = { ...init };
      init.headers = { ...(init.headers || {}), Authorization: `Bearer ${tokenAtRequestTime}` };
    }

    let res;
    try {
      res = await orig(input, init);
    } catch (err) {
      throw err;
    }

    if (isBackend && res.status === 401) {
      try {
        const clone = res.clone();
        const body = await clone.json();
        const code =
          (body && body.code) ||
          (body && body.detail && body.detail.code);
        if (code === 'locked' || code === 'unauthorized') {
          // Only log out if the token hasn't changed since this request was
          // made.  If the user logged in while this request was in-flight,
          // tokenAtRequestTime differs from the current token — that means
          // this 401 is stale and we must NOT clear the fresh session.
          const currentToken = localStorage.getItem(TOKEN_KEY);
          if (tokenAtRequestTime && currentToken === tokenAtRequestTime) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem('quantedge_user');
            onLocked?.();
          }
        }
      } catch {
        // ignore non-JSON 401s
      }
    }
    return res;
  };
  return () => {
    window.fetch = orig;
    window.__quantedge_fetch_patched__ = false;
  };
}

// ---------------------------------------------------------------------------
// Auth API helpers
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// User auth (multi-user)
// ---------------------------------------------------------------------------

async function _authPost(path, body) {
  const r = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  let data = null;
  try { data = await r.json(); } catch {}
  if (!r.ok) {
    const msg = data?.detail || data?.message || `${path} failed (${r.status})`;
    const err = new Error(typeof msg === 'object' ? JSON.stringify(msg) : msg);
    err.status = r.status;
    throw err;
  }
  return data;
}

export const apiAuthLogin    = ({ username, password }) => _authPost('/auth/login',    { username, password });
export const apiAuthRegister = ({ username, password }) => _authPost('/auth/register', { username, password });

export async function apiAuthMe() {
  const r = await fetch(`${API_BASE}/auth/me`);
  if (!r.ok) throw new Error(`auth/me ${r.status}`);
  return r.json();
}

export async function apiAuthChangePassword({ current_password, new_password }) {
  const r = await fetch(`${API_BASE}/auth/change-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ current_password, new_password }),
  });
  let data = null;
  try { data = await r.json(); } catch {}
  if (!r.ok) throw new Error(data?.detail || 'Change password failed.');
  return data;
}

// ---------------------------------------------------------------------------
// Legacy vault unlock (kept for admin use / backward compat)
// ---------------------------------------------------------------------------

export async function apiUnlock(password) {
  const r = await fetch(`${API_BASE}/unlock`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  let body = null;
  try { body = await r.json(); } catch {}
  if (!r.ok) {
    const err = new Error(body?.detail?.message || body?.message || `unlock failed (${r.status})`);
    err.status = r.status;
    err.code = body?.detail?.code || body?.code;
    err.retryAfter = body?.detail?.retry_after_seconds || body?.retry_after_seconds || 0;
    err.failedAttempts = body?.detail?.failed_attempts;
    throw err;
  }
  return body;
}

export async function apiLockStatus() {
  const r = await fetch(`${API_BASE}/lock-status`);
  if (!r.ok) throw new Error(`lock-status ${r.status}`);
  return r.json();
}

export async function apiLockServer() {
  const r = await fetch(`${API_BASE}/lock`, { method: 'POST' });
  if (!r.ok) throw new Error(`lock ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// Claude proxy helper + JSON extraction
// ---------------------------------------------------------------------------

export async function claude(messages, useSearch = false, system = null, maxTok = 1200) {
  const body = {
    messages,
    max_tokens: maxTok,
    use_search: useSearch,
    temperature: 0.2,
  };
  if (system) body.system = system;
  const r = await fetch(`${API_BASE}/proxy/claude`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`Claude proxy error ${r.status}: ${detail.slice(0, 200)}`);
  }
  return await r.json();
}

export function extractJSON(data) {
  let text = '';
  if (data && Array.isArray(data.content)) {
    for (const block of data.content) {
      if (block && block.type === 'text' && typeof block.text === 'string') {
        text += block.text;
      }
    }
  } else if (typeof data === 'string') {
    text = data;
  } else if (data?.completion) {
    text = data.completion;
  }
  text = text.trim();
  text = text.replace(/^```(?:json)?/i, '').replace(/```$/i, '').trim();
  const firstBrace = text.indexOf('{');
  const lastBrace = text.lastIndexOf('}');
  if (firstBrace !== -1 && lastBrace > firstBrace) {
    text = text.slice(firstBrace, lastBrace + 1);
  }
  return JSON.parse(text);
}

// ---------------------------------------------------------------------------
// Market data
// ---------------------------------------------------------------------------

export async function fetchStockData(rawSymbol) {
  const { ticker, exchange } = parseSymbol(rawSymbol);
  const url = `${API_BASE}/stock-data/${encodeURIComponent(ticker)}?exchange=${encodeURIComponent(
    exchange || 'NSE',
  )}`;
  const r = await fetch(url);
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`stock-data ${r.status}: ${detail.slice(0, 200)}`);
  }
  const data = await r.json();
  const price = Number(data?.price);
  if (!Number.isFinite(price) || price <= 0) {
    throw new Error(`Invalid price for ${ticker}`);
  }
  return { ...data, price, symbol: ticker, exchange: data.exchange || exchange };
}

export async function fetchMarketStatus() {
  const r = await fetch(`${API_BASE}/market-status`);
  if (!r.ok) throw new Error(`market-status ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// Backtest (Claude-powered)
// ---------------------------------------------------------------------------

export async function fetchBacktest(symbols) {
  const system = 'Reply with ONLY a valid JSON object. No markdown. First character must be {.';
  const prompt = `Use web search to assemble a realistic backtest summary for a momentum + smart-money breakout strategy applied to these Indian equities over the last 2 years: ${symbols.join(', ')}.
Return JSON with keys:
win_rate (0-100), profit_factor (number), avg_return (number percent),
max_drawdown (negative number percent), sharpe_ratio (number), sortino_ratio (number),
calmar_ratio (number), total_trades (integer), avg_win (percent), avg_loss (percent),
best_trade (percent), worst_trade (percent), best_symbol (string), worst_symbol (string),
strategy_summary (2-3 sentence institutional write-up),
training_period (e.g. "Jan 2023 - Apr 2025"),
monthly_returns: array of 24 objects { "m": "YYYY-MM", "r": strategy_return_pct, "bm": nifty_return_pct }.
Numbers must be numbers, not strings.`;
  const raw = await claude([{ role: 'user', content: prompt }], true, system, 1800);
  return extractJSON(raw);
}

// ---------------------------------------------------------------------------
// ML training (real backend endpoint, Phase 2)
// ---------------------------------------------------------------------------

export async function fetchMLTraining(symbols = null) {
  const body = {};
  if (symbols && symbols.length) body.symbols = symbols;
  const r = await fetch(`${API_BASE}/train-ml`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`train-ml ${r.status}: ${detail.slice(0, 200)}`);
  }
  return r.json();
}

export async function fetchMLLatest() {
  const r = await fetch(`${API_BASE}/train-ml/latest`);
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`train-ml/latest ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// AI explanation (Claude-powered)
// ---------------------------------------------------------------------------

export async function fetchAIExplanation(symbol, sd, setup) {
  const prompt = `Symbol: ${symbol}. Price: ${fmt(sd?.price)} (${fmtPct(sd?.change_pct)}).
RSI: ${fmt(sd?.rsi_estimate, 1)}. Trend: ${sd?.trend}. Sentiment: ${sd?.sentiment}.
Volume vs avg: ${(Number(sd?.volume) / Math.max(Number(sd?.avg_volume) || 1, 1)).toFixed(2)}x.
Entry ${fmt(setup?.entry)}, Stop ${fmt(setup?.stop)}, Target ${fmt(setup?.target)}, R:R ${fmt(setup?.rr)}.
News: ${sd?.news_summary || 'n/a'}.

Write a 3-4 sentence institutional-grade trade thesis. Reference the pattern, momentum structure,
volume, and whether the risk/reward is defensible. No disclaimers, no bullet points.`;
  const raw = await claude(
    [{ role: 'user', content: prompt }],
    false,
    'You are a senior quantitative trader. Be concise, direct, and insight-dense.',
    500,
  );
  let text = '';
  if (Array.isArray(raw?.content)) {
    for (const block of raw.content) {
      if (block?.type === 'text' && typeof block.text === 'string') text += block.text;
    }
  }
  return text.trim();
}

// ---------------------------------------------------------------------------
// Deep Analyze + History
// ---------------------------------------------------------------------------

export async function fetchDeepAnalysis(symbol) {
  const { ticker, exchange } = parseSymbol(symbol);
  const r = await fetch(
    `${API_BASE}/deep-analyze/${encodeURIComponent(ticker)}?exchange=${encodeURIComponent(exchange || 'NSE')}`,
  );
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`deep-analyze ${r.status}: ${detail.slice(0, 200)}`);
  }
  return r.json();
}

export async function fetchHighProbabilityScan(maxSymbols = 0) {
  const url = `${API_BASE}/high-probability-scan${maxSymbols ? `?max_symbols=${maxSymbols}` : ''}`;
  const r = await fetch(url, { method: 'POST' });
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`high-probability-scan ${r.status}: ${detail.slice(0, 200)}`);
  }
  return r.json();
}

export async function fetchHistoryList({ limit = 50, offset = 0, symbol = '', highProbabilityOnly = false } = {}) {
  const q = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  if (symbol) q.set('symbol', symbol);
  if (highProbabilityOnly) q.set('high_probability_only', 'true');
  const r = await fetch(`${API_BASE}/deep-analysis-history?${q.toString()}`);
  if (!r.ok) throw new Error(`history list ${r.status}`);
  return r.json();
}

export async function fetchHistoryItem(id) {
  const r = await fetch(`${API_BASE}/deep-analysis-history/${id}`);
  if (!r.ok) throw new Error(`history fetch ${r.status}`);
  return r.json();
}

export async function fetchHistoryStats() {
  const r = await fetch(`${API_BASE}/deep-analysis-history/stats`);
  if (!r.ok) throw new Error(`history stats ${r.status}`);
  return r.json();
}

export async function deleteHistoryItem(id) {
  const r = await fetch(`${API_BASE}/deep-analysis-history/${id}`, { method: 'DELETE' });
  if (!r.ok) throw new Error(`history delete ${r.status}`);
  return r.json();
}

export async function clearHistory(symbol = '') {
  const url = symbol
    ? `${API_BASE}/deep-analysis-history?symbol=${encodeURIComponent(symbol)}`
    : `${API_BASE}/deep-analysis-history`;
  const r = await fetch(url, { method: 'DELETE' });
  if (!r.ok) throw new Error(`history clear ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// Paper Trading
// ---------------------------------------------------------------------------

export async function fetchPaperPortfolio() {
  const r = await fetch(`${API_BASE}/paper-portfolio`);
  if (!r.ok) throw new Error(`portfolio ${r.status}`);
  return r.json();
}

export async function fetchEquityCurve() {
  const r = await fetch(`${API_BASE}/paper-equity-curve`);
  if (!r.ok) throw new Error(`equity-curve ${r.status}`);
  return r.json();
}

export async function fetchPaperTrades({ status = '', symbol = '', limit = 100 } = {}) {
  const q = new URLSearchParams({ limit: String(limit) });
  if (status) q.set('status', status);
  if (symbol) q.set('symbol', symbol);
  const r = await fetch(`${API_BASE}/paper-trades?${q.toString()}`);
  if (!r.ok) throw new Error(`paper-trades ${r.status}`);
  return r.json();
}

export async function fetchPaperSettings() {
  const r = await fetch(`${API_BASE}/paper-settings`);
  if (!r.ok) throw new Error(`paper-settings ${r.status}`);
  return r.json();
}

export async function patchPaperSettings(updates) {
  const r = await fetch(`${API_BASE}/paper-settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  });
  if (!r.ok) throw new Error(`paper-settings patch ${r.status}`);
  return r.json();
}

export async function closePaperTrade(id, price = null) {
  const r = await fetch(`${API_BASE}/paper-trades/${id}/close`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ price, reason: 'MANUAL_CLOSE' }),
  });
  if (!r.ok) {
    const t = await r.text().catch(() => '');
    throw new Error(`close ${r.status}: ${t.slice(0, 200)}`);
  }
  return r.json();
}

export async function runMonitorNow() {
  const r = await fetch(`${API_BASE}/paper-trades/monitor-now`, { method: 'POST' });
  if (!r.ok) throw new Error(`monitor-now ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// Dashboard & Sentiment
// ---------------------------------------------------------------------------

export async function fetchDashboardSummary() {
  const r = await fetch(`${API_BASE}/dashboard/summary`);
  if (!r.ok) throw new Error(`dashboard/summary ${r.status}`);
  return r.json();
}

export async function fetchNewsSentiment(symbol) {
  const r = await fetch(`${API_BASE}/news/sentiment?symbol=${encodeURIComponent(symbol)}`);
  if (!r.ok) throw new Error(`news/sentiment ${r.status}`);
  return r.json();
}

// ---------------------------------------------------------------------------
// Telegram
// ---------------------------------------------------------------------------

export async function fetchTelegramStatus() {
  const r = await fetch(`${API_BASE}/telegram-status`);
  if (!r.ok) throw new Error(`telegram-status ${r.status}`);
  return r.json();
}

export async function sendTelegramTest() {
  const r = await fetch(`${API_BASE}/telegram-test`, { method: 'POST' });
  const body = await r.json().catch(() => ({}));
  if (!r.ok) {
    const err = new Error(
      (body?.detail?.detail && typeof body.detail.detail === 'object'
        ? JSON.stringify(body.detail.detail)
        : body?.detail?.detail || body?.detail || `telegram-test ${r.status}`),
    );
    err.status = r.status;
    throw err;
  }
  return body;
}

// ---------------------------------------------------------------------------
// Broker (Phase 4 — Angel One SmartAPI)
// ---------------------------------------------------------------------------

export async function fetchBrokerStatus() {
  const r = await fetch(`${API_BASE}/broker/status`);
  if (!r.ok) throw new Error(`broker/status ${r.status}`);
  return r.json();
}

export async function fetchBrokerCredentials() {
  const r = await fetch(`${API_BASE}/broker/credentials`);
  if (!r.ok) throw new Error(`broker/credentials ${r.status}`);
  return r.json();
}

export async function saveBrokerCredentials(creds) {
  const r = await fetch(`${API_BASE}/broker/credentials`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(creds),
  });
  let data = null;
  try { data = await r.json(); } catch {}
  if (!r.ok) throw new Error(data?.detail || `broker/credentials ${r.status}`);
  return data;
}

export async function deleteBrokerCredentials() {
  const r = await fetch(`${API_BASE}/broker/credentials`, { method: 'DELETE' });
  if (!r.ok) throw new Error(`broker/credentials DELETE ${r.status}`);
  return r.json();
}

export async function connectBroker(credentials) {
  const r = await fetch(`${API_BASE}/broker/connect`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials || {}),
  });
  if (!r.ok) {
    const t = await r.text().catch(() => '');
    throw new Error(`broker/connect ${r.status}: ${t.slice(0, 200)}`);
  }
  return r.json();
}

export async function disconnectBroker() {
  const r = await fetch(`${API_BASE}/broker/disconnect`, { method: 'POST' });
  if (!r.ok) throw new Error(`broker/disconnect ${r.status}`);
  return r.json();
}

export async function fetchBrokerPositions() {
  const r = await fetch(`${API_BASE}/broker/positions`);
  if (!r.ok) throw new Error(`broker/positions ${r.status}`);
  return r.json();
}

export async function fetchBrokerHoldings() {
  const r = await fetch(`${API_BASE}/broker/holdings`);
  if (!r.ok) throw new Error(`broker/holdings ${r.status}`);
  return r.json();
}

export async function fetchBrokerFunds() {
  const r = await fetch(`${API_BASE}/broker/funds`);
  if (!r.ok) throw new Error(`broker/funds ${r.status}`);
  return r.json();
}

export async function fetchBrokerOrders() {
  const r = await fetch(`${API_BASE}/broker/orders`);
  if (!r.ok) throw new Error(`broker/orders ${r.status}`);
  return r.json();
}

export async function placeBrokerOrder(payload) {
  const r = await fetch(`${API_BASE}/broker/order`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!r.ok) {
    const t = await r.text().catch(() => '');
    throw new Error(`broker/order ${r.status}: ${t.slice(0, 200)}`);
  }
  return r.json();
}

export async function syncPaperTradeToBroker(tradeId) {
  const r = await fetch(`${API_BASE}/broker/sync-paper/${tradeId}`, { method: 'POST' });
  if (!r.ok) {
    const t = await r.text().catch(() => '');
    throw new Error(`broker/sync-paper ${r.status}: ${t.slice(0, 200)}`);
  }
  return r.json();
}
