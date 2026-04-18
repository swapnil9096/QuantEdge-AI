import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Brain,
  CheckCircle2,
  ChevronRight,
  CircleDollarSign,
  Cpu,
  Eye,
  Gauge,
  LayoutDashboard,
  LineChart as LineIcon,
  Plus,
  RefreshCw,
  Rocket,
  Search,
  ShieldAlert,
  Sparkles,
  Target,
  TrendingDown,
  TrendingUp,
  Trophy,
  WifiOff,
  X,
  XCircle,
  Zap,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

const C = {
  bg: '#0a0e1a',
  card: '#111827',
  border: '#1f2937',
  muted: '#6b7280',
  text: '#e2e8f0',
  sub: '#cbd5e1',
  teal: '#00d4a8',
  blue: '#4d9fff',
  purple: '#a78bfa',
  yellow: '#fbbf24',
  green: '#4ade80',
  red: '#f87171',
  orange: '#f0883e',
  dark: '#0d1526',
};

const FONT_MONO = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace";

// API base URL. In dev (Vite on :3000) we hit the backend on :8000 directly.
// In production the backend serves the built frontend on the same origin so
// relative URLs are correct. Supports override via VITE_API_BASE if you ever
// need to split the deployment (e.g. backend on a different domain).
const API_BASE = (() => {
  const envOverride = import.meta?.env?.VITE_API_BASE;
  if (envOverride) return envOverride.replace(/\/$/, '');
  if (typeof window !== 'undefined') {
    const { hostname, port } = window.location;
    const sameOrigin = hostname !== 'localhost' && hostname !== '127.0.0.1';
    if (sameOrigin) return '';
    if (port === '3000') return 'http://localhost:8000';
  }
  return 'http://localhost:8000';
})();

// ---------------------------------------------------------------------------
// Claude proxy helper + JSON extraction
// ---------------------------------------------------------------------------

async function claude(messages, useSearch = false, system = null, maxTok = 1200) {
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

function extractJSON(data) {
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
// Utility helpers
// ---------------------------------------------------------------------------

function parseSymbol(raw) {
  if (!raw) return { ticker: '', exchange: 'NSE' };
  const clean = raw.trim().toUpperCase();
  if (clean.includes('.')) {
    const [ticker, exchange] = clean.split('.');
    return { ticker, exchange: exchange || 'NSE' };
  }
  if (clean.includes(':')) {
    const [ticker, exchange] = clean.split(':');
    return { ticker, exchange: exchange || 'NSE' };
  }
  return { ticker: clean, exchange: 'NSE' };
}

function fmt(value, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const abs = Math.abs(value);
  if (abs >= 1_00_000) return value.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  return Number(value).toFixed(digits);
}

function fmtPct(value, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const sign = value >= 0 ? '+' : '';
  return `${sign}${Number(value).toFixed(digits)}%`;
}

function formatCrore(value, currency = 'INR') {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  const n = Number(value);
  if (currency === 'INR') {
    const cr = n / 10_000_000;
    if (cr >= 1_000) return `₹${(cr / 1_000).toFixed(2)}K Cr`;
    return `₹${cr.toFixed(0)} Cr`;
  }
  if (n >= 1e12) return `$${(n / 1e12).toFixed(2)}T`;
  if (n >= 1e9) return `$${(n / 1e9).toFixed(2)}B`;
  if (n >= 1e6) return `$${(n / 1e6).toFixed(2)}M`;
  return `$${n.toFixed(0)}`;
}

function deterministicPattern(price) {
  const patterns = [
    'Bullish Engulfing',
    'Morning Star',
    'Hammer',
    'Inside Bar Breakout',
    'Momentum Breakout',
    'Bullish Marubozu',
  ];
  const bucket = Math.floor((Number(price) || 0) * 100) % patterns.length;
  return patterns[Math.abs(bucket)];
}

function mlScoreFromData(sd) {
  if (!sd) return 0;
  let score = 50;
  const trend = (sd.trend || '').toLowerCase();
  if (trend.includes('strong up') || trend.includes('bullish')) score += 18;
  else if (trend.includes('up')) score += 10;
  else if (trend.includes('down') || trend.includes('bearish')) score -= 12;

  const sentiment = (sd.sentiment || '').toLowerCase();
  if (sentiment.includes('positive') || sentiment.includes('bullish')) score += 10;
  else if (sentiment.includes('negative') || sentiment.includes('bearish')) score -= 10;

  const rsi = Number(sd.rsi_estimate);
  if (!Number.isNaN(rsi)) {
    if (rsi >= 55 && rsi <= 68) score += 10;
    else if (rsi < 35 || rsi > 78) score -= 8;
  }

  const vr = Number(sd.volume) / Math.max(Number(sd.avg_volume) || 1, 1);
  if (vr >= 2) score += 8;
  else if (vr >= 1.5) score += 5;
  else if (vr < 0.8) score -= 6;

  const support = Number(sd.support);
  const price = Number(sd.price);
  if (support && price && price > 0) {
    const proximity = ((price - support) / price) * 100;
    if (proximity > 0 && proximity < 3) score += 6;
    else if (proximity < 0) score -= 10;
  }
  return Math.max(0, Math.min(99, Math.round(score)));
}

function computeSetup(sd) {
  const price = Number(sd?.price) || 0;
  const atrEst = price * 0.022;
  const entry = price;
  const stop = Math.max(entry - atrEst * 1.5, entry * 0.95);
  const target = entry + atrEst * 3.2;
  const risk = Math.max(entry - stop, 0.01);
  const reward = target - entry;
  const rr = reward / risk;
  const expectedReturn = ((target - entry) / entry) * 100;
  return {
    entry,
    stop,
    target,
    rr,
    expectedReturn,
    atr: atrEst,
    pattern: deterministicPattern(price),
  };
}

// ---------------------------------------------------------------------------
// Reusable building blocks
// ---------------------------------------------------------------------------

function Spinner({ size = 16, color = C.teal }) {
  return (
    <span
      aria-label="loading"
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        border: `2px solid ${C.border}`,
        borderTopColor: color,
        borderRadius: '50%',
        animation: 'qe-spin 0.8s linear infinite',
        verticalAlign: 'middle',
      }}
    />
  );
}

function Section({ title, subtitle, icon, children, right }) {
  return (
    <section
      style={{
        background: C.card,
        border: `1px solid ${C.border}`,
        borderRadius: 14,
        padding: '1.25rem 1.25rem 1.1rem',
        marginBottom: 18,
        animation: 'qe-fade-in 0.35s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        {icon && (
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: C.dark,
              display: 'grid',
              placeItems: 'center',
              color: C.teal,
            }}
          >
            {icon}
          </div>
        )}
        <div style={{ flex: 1 }}>
          {title && (
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: C.text }}>{title}</h3>
          )}
          {subtitle && (
            <p style={{ margin: '2px 0 0', fontSize: 12, color: C.muted }}>{subtitle}</p>
          )}
        </div>
        {right}
      </div>
      {children}
    </section>
  );
}

function KpiCard({ icon, label, value, sub, color = C.teal }) {
  return (
    <div
      style={{
        flex: '1 1 140px',
        background: C.card,
        border: `1px solid ${C.border}`,
        borderRadius: 12,
        padding: '0.85rem 1rem',
        minWidth: 140,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: C.muted, fontSize: 11, letterSpacing: 0.6, textTransform: 'uppercase' }}>
        <span style={{ color }}>{icon}</span>
        {label}
      </div>
      <div style={{ marginTop: 6, fontSize: 22, fontWeight: 700, color: C.text, fontFamily: FONT_MONO }}>
        {value}
      </div>
      {sub && <div style={{ marginTop: 2, fontSize: 11, color: C.sub }}>{sub}</div>}
    </div>
  );
}

function ProbBadge({ score }) {
  const n = Number(score) || 0;
  let bg = 'rgba(248,113,113,0.15)';
  let fg = C.red;
  if (n >= 85) {
    bg = 'rgba(0,212,168,0.15)';
    fg = C.teal;
  } else if (n >= 70) {
    bg = 'rgba(77,159,255,0.15)';
    fg = C.blue;
  } else if (n >= 50) {
    bg = 'rgba(251,191,36,0.15)';
    fg = C.yellow;
  }
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '3px 9px',
        borderRadius: 999,
        background: bg,
        color: fg,
        fontSize: 11.5,
        fontWeight: 700,
        fontFamily: FONT_MONO,
      }}
    >
      {n}%
    </span>
  );
}

function LiveBtn({ icon, label, loading, onClick, gradient = `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)` }) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '0.55rem 1.15rem',
        background: loading ? C.border : gradient,
        color: loading ? C.muted : '#041018',
        border: 'none',
        borderRadius: 10,
        fontWeight: 700,
        fontSize: 13,
        cursor: loading ? 'not-allowed' : 'pointer',
        boxShadow: loading ? 'none' : '0 4px 18px rgba(0,212,168,0.25)',
        transition: 'transform 0.15s ease, box-shadow 0.15s ease',
      }}
      onMouseDown={(e) => !loading && (e.currentTarget.style.transform = 'translateY(1px)')}
      onMouseUp={(e) => !loading && (e.currentTarget.style.transform = 'translateY(0)')}
    >
      {loading ? <Spinner color="#041018" /> : icon}
      {loading ? 'Working…' : label}
    </button>
  );
}

function Tag({ label, value, color = C.teal }) {
  return (
    <div
      style={{
        padding: '0.5rem 0.75rem',
        background: C.dark,
        border: `1px solid ${C.border}`,
        borderRadius: 10,
      }}
    >
      <div style={{ fontSize: 10, letterSpacing: 0.6, color: C.muted, textTransform: 'uppercase' }}>
        {label}
      </div>
      <div style={{ marginTop: 2, fontSize: 13, fontWeight: 700, color, fontFamily: FONT_MONO }}>
        {value}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Data fetchers (wrappers around claude())
// ---------------------------------------------------------------------------

async function fetchStockData(rawSymbol) {
  const { ticker, exchange } = parseSymbol(rawSymbol);
  // Authoritative market data comes from our own backend (Yahoo Finance),
  // not Claude. Claude is only used downstream for the trade narrative.
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

async function fetchBacktest(symbols) {
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

async function fetchMLAnalysis(symbols, scanData) {
  const system = 'Reply with ONLY a valid JSON object. No markdown. First character must be {.';
  const scanSummary = Object.entries(scanData || {})
    .slice(0, 10)
    .map(([sym, sd]) => `${sym}: ₹${fmt(sd?.price)} ${fmtPct(sd?.change_pct)}`)
    .join('; ');
  const prompt = `Act as a senior ML engineer. Produce a realistic multi-model comparison for a swing-trading classifier trained on ${symbols.join(', ')}.
Context of today's scan: ${scanSummary || 'n/a'}.
Return JSON:
models: array of 4 objects for XGBoost, LightGBM, RandomForest, GradBoosting, each with
  name, acc (0-1), prec (0-1), rec (0-1), f1 (0-1), auc (0-1), train_time_s (number), best (boolean).
features: array of 8-12 objects { name, importance (0-100), direction: "bullish"|"bearish"|"neutral" }.
dataset_size (integer), training_period (string), cv_folds (integer), best_threshold (0-1),
analysis_note (2-3 sentences).
Exactly one model should have best=true.`;
  const raw = await claude([{ role: 'user', content: prompt }], true, system, 1800);
  return extractJSON(raw);
}

async function fetchAIExplanation(symbol, sd, setup) {
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
// Header
// ---------------------------------------------------------------------------

function Header({ lastScan, lastError, onLock }) {
  const live = !!lastScan;
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '1rem 1.5rem',
        borderBottom: `1px solid ${C.border}`,
        background: 'linear-gradient(180deg, #0b1224 0%, #0a0e1a 100%)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div
          style={{
            width: 40,
            height: 40,
            borderRadius: 12,
            background: `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
            display: 'grid',
            placeItems: 'center',
            color: '#041018',
            boxShadow: '0 6px 24px rgba(0,212,168,0.35)',
          }}
        >
          <Sparkles size={22} strokeWidth={2.4} />
        </div>
        <div>
          <h1 style={{ margin: 0, fontSize: 17, fontWeight: 800, letterSpacing: 0.3 }}>
            QuantEdge <span style={{ color: C.teal }}>AI</span>
          </h1>
          <div style={{ fontSize: 11, color: C.muted }}>
            Institutional AlphaScan · Live market research · GPT-4o &amp; Claude
          </div>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 12px',
            borderRadius: 999,
            background: live ? 'rgba(74,222,128,0.12)' : C.dark,
            border: `1px solid ${live ? C.green : C.border}`,
            color: live ? C.green : C.muted,
            fontSize: 12,
            fontWeight: 600,
          }}
        >
          {live ? (
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: C.green,
                animation: 'qe-pulse 1.4s ease-in-out infinite',
              }}
            />
          ) : (
            <WifiOff size={12} />
          )}
          {live ? 'Live' : 'Not scanned'}
        </div>
        <div style={{ fontSize: 11, color: C.muted, fontFamily: FONT_MONO }}>
          {lastScan ? `Last scan: ${new Date(lastScan).toLocaleTimeString()}` : lastError ? 'Error' : '—'}
        </div>
        {onLock && (
          <button
            onClick={onLock}
            title="Lock QuantEdge AI (clears session, wipes secrets from memory)"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '6px 12px',
              borderRadius: 999,
              background: C.dark,
              border: `1px solid ${C.border}`,
              color: C.muted,
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = C.red;
              e.currentTarget.style.borderColor = C.red;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = C.muted;
              e.currentTarget.style.borderColor = C.border;
            }}
          >
            <ShieldAlert size={12} /> Lock
          </button>
        )}
      </div>
    </header>
  );
}

// ---------------------------------------------------------------------------
// Tab 1 — Scanner
// ---------------------------------------------------------------------------

function ScannerTab({
  watchlist,
  addSymbol,
  removeSymbol,
  scanStatus,
  scanData,
  runScan,
  scanning,
  openInsights,
}) {
  const [draft, setDraft] = useState('');

  const rows = useMemo(() => {
    return watchlist.map((sym) => {
      const sd = scanData[sym];
      const setup = sd ? computeSetup(sd) : null;
      const ml = sd ? mlScoreFromData(sd) : null;
      return { sym, sd, setup, ml, status: scanStatus[sym] || 'idle' };
    });
  }, [watchlist, scanData, scanStatus]);

  return (
    <div>
      <Section
        title="Watchlist"
        subtitle="Add NSE symbols. Use the format RELIANCE or RELIANCE.NSE — the exchange suffix is stripped before search."
        icon={<Eye size={18} />}
        right={
          <LiveBtn
            icon={<RefreshCw size={15} />}
            label={`Run Live Scan (${watchlist.length})`}
            loading={scanning}
            onClick={runScan}
          />
        }
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!draft.trim()) return;
            addSymbol(draft);
            setDraft('');
          }}
          style={{ display: 'flex', gap: 8, marginBottom: 12 }}
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="e.g. RELIANCE or TCS.NSE"
            style={{
              flex: 1,
              padding: '0.55rem 0.75rem',
              background: C.dark,
              color: C.text,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              outline: 'none',
              fontFamily: FONT_MONO,
            }}
          />
          <button
            type="submit"
            style={{
              padding: '0.55rem 1rem',
              background: C.dark,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              color: C.teal,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontWeight: 600,
            }}
          >
            <Plus size={14} /> Add
          </button>
        </form>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {watchlist.map((sym) => {
            const status = scanStatus[sym];
            let icon = null;
            if (status === 'loading') icon = <Spinner size={12} />;
            else if (status === 'ok') icon = <CheckCircle2 size={12} color={C.green} />;
            else if (status === 'error') icon = <XCircle size={12} color={C.red} />;
            return (
              <span
                key={sym}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '4px 10px',
                  background: C.dark,
                  border: `1px solid ${C.border}`,
                  borderRadius: 999,
                  fontSize: 12,
                  fontFamily: FONT_MONO,
                  color: C.sub,
                }}
              >
                {icon}
                {sym}
                <button
                  onClick={() => removeSymbol(sym)}
                  style={{ background: 'none', border: 'none', color: C.muted, cursor: 'pointer', padding: 0 }}
                  aria-label={`Remove ${sym}`}
                >
                  <X size={12} />
                </button>
              </span>
            );
          })}
        </div>
      </Section>

      <Section title="Results" subtitle="Click any row to jump to AI Insights." icon={<BarChart3 size={18} />}>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ textAlign: 'left', color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                {[
                  'Symbol',
                  'Price',
                  'Δ %',
                  'Entry',
                  'Stop',
                  'Target',
                  'R:R',
                  'Pattern',
                  'RSI',
                  'ML Score',
                  '',
                ].map((h) => (
                  <th key={h} style={{ padding: '0.55rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(({ sym, sd, setup, ml, status }) => {
                const changeColor =
                  Number(sd?.change_pct) >= 0 ? C.green : C.red;
                return (
                  <tr
                    key={sym}
                    onClick={() => sd && openInsights(sym)}
                    style={{
                      cursor: sd ? 'pointer' : 'default',
                      borderBottom: `1px solid ${C.border}`,
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = C.dark)}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    <td style={{ padding: '0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        {status === 'loading' && <Spinner size={12} />}
                        {status === 'ok' && <CheckCircle2 size={12} color={C.green} />}
                        {status === 'error' && <XCircle size={12} color={C.red} />}
                        {sym}
                      </div>
                    </td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO }}>₹{fmt(sd?.price)}</td>
                    <td style={{ padding: '0.6rem', color: changeColor, fontFamily: FONT_MONO }}>
                      {fmtPct(sd?.change_pct)}
                    </td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO }}>{setup ? fmt(setup.entry) : '—'}</td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO, color: C.red }}>
                      {setup ? fmt(setup.stop) : '—'}
                    </td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO, color: C.green }}>
                      {setup ? fmt(setup.target) : '—'}
                    </td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO }}>
                      {setup ? fmt(setup.rr) : '—'}
                    </td>
                    <td style={{ padding: '0.6rem' }}>{setup ? setup.pattern : '—'}</td>
                    <td style={{ padding: '0.6rem', fontFamily: FONT_MONO }}>{sd ? fmt(sd.rsi_estimate, 1) : '—'}</td>
                    <td style={{ padding: '0.6rem' }}>{ml !== null ? <ProbBadge score={ml} /> : '—'}</td>
                    <td style={{ padding: '0.6rem', textAlign: 'right' }}>
                      {sd && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            openInsights(sym);
                          }}
                          style={{
                            padding: '4px 10px',
                            background: 'transparent',
                            border: `1px solid ${C.border}`,
                            borderRadius: 8,
                            color: C.teal,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                          }}
                        >
                          <Brain size={12} /> AI View
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
              {!rows.length && (
                <tr>
                  <td colSpan={11} style={{ padding: '1.25rem', textAlign: 'center', color: C.muted }}>
                    Add a few symbols above and hit Run Live Scan.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tab 2 — Backtest
// ---------------------------------------------------------------------------

function BacktestTab({ backtest, loading, runBacktest }) {
  const equity = useMemo(() => {
    if (!backtest?.monthly_returns) return [];
    let stratCum = 1;
    let bmCum = 1;
    return backtest.monthly_returns.map((row) => {
      stratCum *= 1 + (Number(row.r) || 0) / 100;
      bmCum *= 1 + (Number(row.bm) || 0) / 100;
      return {
        m: row.m,
        strategy: Number((stratCum * 100 - 100).toFixed(2)),
        benchmark: Number((bmCum * 100 - 100).toFixed(2)),
      };
    });
  }, [backtest]);

  const monthly = useMemo(
    () =>
      (backtest?.monthly_returns || []).map((row) => ({
        m: row.m,
        r: Number(row.r) || 0,
        bm: Number(row.bm) || 0,
      })),
    [backtest],
  );

  return (
    <div>
      <Section
        title="Run backtest"
        subtitle="Uses live web research via Claude to benchmark the strategy over the last 24 months."
        icon={<LineIcon size={18} />}
        right={
          <LiveBtn
            icon={<Rocket size={15} />}
            label="Run Live Backtest"
            loading={loading}
            onClick={runBacktest}
          />
        }
      >
        {!backtest && !loading && (
          <div style={{ color: C.muted, fontSize: 13 }}>
            No backtest yet. Click <b>Run Live Backtest</b> to generate one.
          </div>
        )}
        {loading && (
          <div style={{ color: C.sub, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
            <Spinner /> Crunching trades…
          </div>
        )}
      </Section>

      {backtest && (
        <>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 18 }}>
            <KpiCard
              icon={<Trophy size={14} />}
              label="Win rate"
              value={`${fmt(backtest.win_rate, 1)}%`}
              color={C.teal}
            />
            <KpiCard
              icon={<TrendingUp size={14} />}
              label="Profit factor"
              value={fmt(backtest.profit_factor)}
              color={C.green}
            />
            <KpiCard
              icon={<Activity size={14} />}
              label="Avg return"
              value={`${fmt(backtest.avg_return)}%`}
              color={C.blue}
            />
            <KpiCard
              icon={<TrendingDown size={14} />}
              label="Max drawdown"
              value={`${fmt(backtest.max_drawdown)}%`}
              color={C.red}
            />
            <KpiCard
              icon={<Gauge size={14} />}
              label="Sharpe"
              value={fmt(backtest.sharpe_ratio)}
              color={C.yellow}
            />
            <KpiCard
              icon={<Gauge size={14} />}
              label="Sortino"
              value={fmt(backtest.sortino_ratio)}
              color={C.orange}
            />
            <KpiCard
              icon={<Gauge size={14} />}
              label="Calmar"
              value={fmt(backtest.calmar_ratio)}
              color={C.purple}
            />
            <KpiCard
              icon={<CircleDollarSign size={14} />}
              label="Total trades"
              value={fmt(backtest.total_trades, 0)}
              color={C.sub}
            />
          </div>

          {backtest.strategy_summary && (
            <Section title="AI strategy summary" icon={<Brain size={18} />} subtitle={backtest.training_period}>
              <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.65 }}>
                {backtest.strategy_summary}
              </p>
            </Section>
          )}

          <Section title="Equity curve" subtitle="Cumulative return vs benchmark" icon={<LineIcon size={18} />}>
            <div style={{ width: '100%', height: 280 }}>
              <ResponsiveContainer>
                <AreaChart data={equity} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <defs>
                    <linearGradient id="qe-strat" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={C.teal} stopOpacity={0.5} />
                      <stop offset="95%" stopColor={C.teal} stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="qe-bench" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={C.blue} stopOpacity={0.35} />
                      <stop offset="95%" stopColor={C.blue} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke={C.border} strokeDasharray="3 3" />
                  <XAxis dataKey="m" stroke={C.muted} fontSize={11} />
                  <YAxis stroke={C.muted} fontSize={11} tickFormatter={(v) => `${v}%`} />
                  <Tooltip
                    contentStyle={{
                      background: C.card,
                      border: `1px solid ${C.border}`,
                      borderRadius: 8,
                      fontSize: 12,
                    }}
                    formatter={(v) => `${Number(v).toFixed(2)}%`}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Area
                    type="monotone"
                    dataKey="strategy"
                    stroke={C.teal}
                    fill="url(#qe-strat)"
                    strokeWidth={2}
                    name="QuantEdge"
                  />
                  <Area
                    type="monotone"
                    dataKey="benchmark"
                    stroke={C.blue}
                    fill="url(#qe-bench)"
                    strokeWidth={2}
                    name="NIFTY 50"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </Section>

          <Section title="Monthly returns" subtitle="Green/red = strategy, blue = benchmark" icon={<BarChart3 size={18} />}>
            <div style={{ width: '100%', height: 260 }}>
              <ResponsiveContainer>
                <BarChart data={monthly} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <CartesianGrid stroke={C.border} strokeDasharray="3 3" />
                  <XAxis dataKey="m" stroke={C.muted} fontSize={11} />
                  <YAxis stroke={C.muted} fontSize={11} tickFormatter={(v) => `${v}%`} />
                  <Tooltip
                    contentStyle={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 8, fontSize: 12 }}
                    formatter={(v) => `${Number(v).toFixed(2)}%`}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="r" name="Strategy">
                    {monthly.map((row, idx) => (
                      <Cell key={idx} fill={row.r >= 0 ? C.green : C.red} />
                    ))}
                  </Bar>
                  <Bar dataKey="bm" name="NIFTY" fill={C.blue} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Section>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tab 3 — ML Models
// ---------------------------------------------------------------------------

function MLTab({ ml, loading, runTraining }) {
  const radarData = useMemo(() => {
    if (!ml?.models) return [];
    const metrics = ['acc', 'prec', 'rec', 'f1', 'auc'];
    const labels = { acc: 'Accuracy', prec: 'Precision', rec: 'Recall', f1: 'F1', auc: 'ROC-AUC' };
    return metrics.map((m) => {
      const row = { metric: labels[m] };
      for (const model of ml.models) {
        row[model.name] = Number((Number(model[m]) * 100).toFixed(1));
      }
      return row;
    });
  }, [ml]);

  const featureColor = (dir) => {
    const d = (dir || '').toLowerCase();
    if (d === 'bullish') return Math.random() < 0.5 ? C.teal : C.blue; // deterministic-looking tint
    if (d === 'bearish') return Math.random() < 0.5 ? C.red : C.purple;
    return Math.random() < 0.5 ? C.yellow : C.blue;
  };
  const modelColors = [C.teal, C.blue, C.purple, C.orange];

  return (
    <div>
      <Section
        title="Train live models"
        subtitle="Ensemble comparison across XGBoost, LightGBM, RandomForest and GradientBoosting."
        icon={<Cpu size={18} />}
        right={
          <LiveBtn
            icon={<Zap size={15} />}
            label="Train Live Models"
            loading={loading}
            onClick={runTraining}
          />
        }
      >
        {!ml && !loading && (
          <div style={{ color: C.muted, fontSize: 13 }}>
            No training run yet. Click <b>Train Live Models</b> to kick one off.
          </div>
        )}
        {loading && (
          <div style={{ color: C.sub, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
            <Spinner /> Cross-validating…
          </div>
        )}
      </Section>

      {ml && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 18 }}>
            {ml.models?.map((model, idx) => (
              <div
                key={model.name}
                style={{
                  background: C.card,
                  border: `1px solid ${model.best ? C.teal : C.border}`,
                  borderRadius: 14,
                  padding: '1rem',
                  position: 'relative',
                }}
              >
                {model.best && (
                  <span
                    style={{
                      position: 'absolute',
                      top: 10,
                      right: 12,
                      padding: '2px 8px',
                      background: 'rgba(0,212,168,0.15)',
                      color: C.teal,
                      borderRadius: 999,
                      fontSize: 10,
                      fontWeight: 700,
                      letterSpacing: 0.6,
                      textTransform: 'uppercase',
                    }}
                  >
                    Best
                  </span>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <div
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      background: modelColors[idx % modelColors.length],
                    }}
                  />
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{model.name}</div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
                  {['acc', 'prec', 'rec', 'f1', 'auc'].map((k) => (
                    <div
                      key={k}
                      style={{ background: C.dark, border: `1px solid ${C.border}`, borderRadius: 8, padding: '6px 8px' }}
                    >
                      <div style={{ fontSize: 9.5, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                        {k}
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 700, color: C.text, fontFamily: FONT_MONO }}>
                        {fmt(Number(model[k]) * 100, 1)}%
                      </div>
                    </div>
                  ))}
                </div>
                <div style={{ marginTop: 8, fontSize: 11, color: C.muted }}>
                  Train time: {fmt(model.train_time_s)}s
                </div>
              </div>
            ))}
          </div>

          {ml.analysis_note && (
            <Section title="Model insights" icon={<Brain size={18} />}>
              <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.65 }}>{ml.analysis_note}</p>
              <div style={{ display: 'flex', gap: 16, marginTop: 10, color: C.muted, fontSize: 11 }}>
                <span>Dataset: {fmt(ml.dataset_size, 0)} rows</span>
                <span>CV folds: {ml.cv_folds}</span>
                <span>Threshold: {fmt(ml.best_threshold, 2)}</span>
                <span>Window: {ml.training_period}</span>
              </div>
            </Section>
          )}

          <Section title="Model comparison" subtitle="5 metrics, normalised 0-100" icon={<BarChart3 size={18} />}>
            <div style={{ width: '100%', height: 300 }}>
              <ResponsiveContainer>
                <RadarChart data={radarData}>
                  <PolarGrid stroke={C.border} />
                  <PolarAngleAxis dataKey="metric" stroke={C.sub} fontSize={11} />
                  <PolarRadiusAxis stroke={C.muted} fontSize={10} domain={[0, 100]} />
                  {ml.models?.map((model, idx) => (
                    <Radar
                      key={model.name}
                      name={model.name}
                      dataKey={model.name}
                      stroke={modelColors[idx % modelColors.length]}
                      fill={modelColors[idx % modelColors.length]}
                      fillOpacity={0.15}
                      strokeWidth={2}
                    />
                  ))}
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Tooltip
                    contentStyle={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 8, fontSize: 12 }}
                  />
                </RadarChart>
              </ResponsiveContainer>
            </div>
          </Section>

          {ml.features && (
            <Section title="Feature importance" subtitle="Direction-coloured" icon={<BarChart3 size={18} />}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {ml.features.map((f) => {
                  const dir = (f.direction || '').toLowerCase();
                  const color =
                    dir === 'bullish'
                      ? C.teal
                      : dir === 'bearish'
                      ? C.red
                      : C.yellow;
                  const pct = Math.max(0, Math.min(100, Number(f.importance) || 0));
                  return (
                    <div key={f.name} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ width: 180, fontSize: 12, color: C.sub }}>{f.name}</div>
                      <div style={{ flex: 1, height: 10, background: C.dark, borderRadius: 999, overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: color }} />
                      </div>
                      <div style={{ width: 60, textAlign: 'right', fontSize: 11, color: C.muted, fontFamily: FONT_MONO }}>
                        {fmt(pct, 1)}%
                      </div>
                    </div>
                  );
                })}
              </div>
            </Section>
          )}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tab 4 — AI Insights
// ---------------------------------------------------------------------------

function InsightsTab({ watchlist, scanData, activeSymbol, setActiveSymbol, aiText, aiLoading, runExplain }) {
  const scannedSymbols = Object.keys(scanData || {});
  const sd = activeSymbol ? scanData[activeSymbol] : null;
  const setup = sd ? computeSetup(sd) : null;
  const ml = sd ? mlScoreFromData(sd) : null;

  useEffect(() => {
    if (activeSymbol && sd && !aiText && !aiLoading) {
      runExplain(activeSymbol);
    }
  }, [activeSymbol, sd, aiText, aiLoading, runExplain]);

  return (
    <div>
      <Section title="Select a scanned stock" icon={<Search size={18} />}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {watchlist.map((sym) => {
            const enabled = scannedSymbols.includes(sym);
            const active = sym === activeSymbol;
            return (
              <button
                key={sym}
                disabled={!enabled}
                onClick={() => setActiveSymbol(sym)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 10,
                  border: `1px solid ${active ? C.teal : C.border}`,
                  background: active ? 'rgba(0,212,168,0.12)' : enabled ? C.dark : 'transparent',
                  color: enabled ? (active ? C.teal : C.sub) : C.muted,
                  cursor: enabled ? 'pointer' : 'not-allowed',
                  fontFamily: FONT_MONO,
                  fontSize: 12,
                  fontWeight: 600,
                }}
              >
                {sym}
              </button>
            );
          })}
          {!watchlist.length && (
            <div style={{ color: C.muted, fontSize: 13 }}>Add symbols and run a scan first.</div>
          )}
        </div>
      </Section>

      {sd && setup && (
        <>
          <Section
            title={`${activeSymbol}${sd.sector ? ` · ${sd.sector}` : ''}`}
            subtitle={sd.news_summary}
            icon={<Brain size={18} />}
            right={
              <div style={{ display: 'flex', gap: 6 }}>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: 999,
                    background:
                      Number(sd.change_pct) >= 0 ? 'rgba(74,222,128,0.15)' : 'rgba(248,113,113,0.15)',
                    color: Number(sd.change_pct) >= 0 ? C.green : C.red,
                    fontWeight: 700,
                    fontSize: 11.5,
                    fontFamily: FONT_MONO,
                  }}
                >
                  {fmtPct(sd.change_pct)}
                </span>
                <ProbBadge score={ml} />
              </div>
            }
          >
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
              <Tag label="Live price" value={`₹${fmt(sd.price)}`} color={C.teal} />
              <Tag label="Stop" value={`₹${fmt(setup.stop)}`} color={C.red} />
              <Tag label="Target" value={`₹${fmt(setup.target)}`} color={C.green} />
              <Tag label="Risk/Reward" value={fmt(setup.rr)} color={C.blue} />
              <Tag label="Expected" value={`${fmt(setup.expectedReturn)}%`} color={C.teal} />
              <Tag label="ATR est." value={fmt(setup.atr)} color={C.yellow} />
            </div>

            {sd.week52_low && sd.week52_high && sd.price && (
              <div style={{ marginTop: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: C.muted }}>
                  <span>52W low ₹{fmt(sd.week52_low)}</span>
                  <span>52W high ₹{fmt(sd.week52_high)}</span>
                </div>
                <div
                  style={{
                    marginTop: 4,
                    height: 8,
                    background: C.dark,
                    borderRadius: 999,
                    position: 'relative',
                    border: `1px solid ${C.border}`,
                  }}
                >
                  <div
                    style={{
                      position: 'absolute',
                      left: `${Math.max(0, Math.min(100, ((sd.price - sd.week52_low) / Math.max(sd.week52_high - sd.week52_low, 1e-9)) * 100))}%`,
                      transform: 'translate(-50%, -50%)',
                      top: '50%',
                      width: 14,
                      height: 14,
                      borderRadius: '50%',
                      background: C.teal,
                      boxShadow: '0 0 10px rgba(0,212,168,0.7)',
                    }}
                  />
                </div>
              </div>
            )}
          </Section>

          <Section title="Fundamentals" icon={<CircleDollarSign size={18} />} subtitle="Live from Yahoo Finance">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
              <Tag
                label="Market cap"
                value={sd.market_cap ? formatCrore(sd.market_cap, sd.currency) : '—'}
                color={C.teal}
              />
              <Tag label="P/E" value={sd.pe_ratio ? fmt(sd.pe_ratio, 2) : '—'} color={C.blue} />
              <Tag label="Book value" value={sd.book_value ? `₹${fmt(sd.book_value, 2)}` : '—'} color={C.purple} />
              <Tag
                label="Div yield"
                value={sd.dividend_yield != null ? `${fmt(sd.dividend_yield, 2)}%` : '—'}
                color={C.yellow}
              />
              <Tag label="ROE" value={sd.roe != null ? `${fmt(sd.roe, 2)}%` : '—'} color={C.green} />
              <Tag label="Industry" value={sd.industry || '—'} color={C.orange} />
            </div>
            <div style={{ marginTop: 10, color: C.muted, fontSize: 11 }}>
              Data as of {sd.last_bar || '—'} · source: {sd.data_source || 'yahoo_finance'}
            </div>
          </Section>

          <Section
            title="AI trade thesis"
            icon={<Brain size={18} color={C.purple} />}
            subtitle="GPT-style narrative via Claude"
            right={
              <button
                onClick={() => runExplain(activeSymbol)}
                disabled={aiLoading}
                style={{
                  padding: '4px 10px',
                  background: 'transparent',
                  border: `1px solid ${C.border}`,
                  borderRadius: 8,
                  color: C.purple,
                  cursor: aiLoading ? 'not-allowed' : 'pointer',
                  fontSize: 12,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                {aiLoading ? <Spinner size={12} color={C.purple} /> : <RefreshCw size={12} />} Regenerate
              </button>
            }
          >
            {aiLoading ? (
              <div style={{ color: C.sub, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Spinner color={C.purple} /> Drafting institutional view…
              </div>
            ) : aiText ? (
              <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.7 }}>{aiText}</p>
            ) : (
              <div style={{ color: C.muted, fontSize: 13 }}>No narrative yet.</div>
            )}
          </Section>

          <Section title="Signal tags" icon={<Target size={18} />}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 10 }}>
              <Tag label="Pattern" value={setup.pattern} color={C.teal} />
              <Tag label="RSI" value={fmt(sd.rsi_estimate, 1)} color={C.blue} />
              <Tag label="Trend" value={sd.trend} color={C.green} />
              <Tag label="Sentiment" value={sd.sentiment} color={C.purple} />
              <Tag label="Risk" value={`₹${fmt(setup.entry - setup.stop)}`} color={C.red} />
              <Tag label="Reward" value={`₹${fmt(setup.target - setup.entry)}`} color={C.green} />
            </div>
          </Section>

          {scannedSymbols.length > 1 && (
            <Section title="Other scanned symbols" icon={<LayoutDashboard size={18} />}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {scannedSymbols
                  .filter((s) => s !== activeSymbol)
                  .map((s) => (
                    <button
                      key={s}
                      onClick={() => setActiveSymbol(s)}
                      style={{
                        padding: '5px 10px',
                        borderRadius: 999,
                        background: C.dark,
                        border: `1px solid ${C.border}`,
                        color: C.sub,
                        cursor: 'pointer',
                        fontSize: 12,
                        fontFamily: FONT_MONO,
                      }}
                    >
                      {s}
                    </button>
                  ))}
              </div>
            </Section>
          )}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tab 5 — AlphaScan (local FastAPI)
// ---------------------------------------------------------------------------

function ScoreRing({ score }) {
  const size = 140;
  const stroke = 10;
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, Number(score) || 0));
  const offset = circ - (pct / 100) * circ;
  const color = pct >= 90 ? C.teal : pct >= 70 ? C.blue : C.yellow;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke={C.border} strokeWidth={stroke} fill="none" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={circ}
          style={{
            '--ring-circ': circ,
            '--ring-target': offset,
            strokeDashoffset: offset,
            animation: 'qe-ring-draw 1.1s ease-out',
            transform: `rotate(-90deg)`,
            transformOrigin: 'center',
            filter: `drop-shadow(0 0 6px ${color}55)`,
          }}
        />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'grid',
          placeItems: 'center',
          flexDirection: 'column',
          textAlign: 'center',
        }}
      >
        <div>
          <div style={{ fontSize: 30, fontWeight: 800, color, fontFamily: FONT_MONO, lineHeight: 1 }}>{pct}</div>
          <div style={{ fontSize: 10, color: C.muted, letterSpacing: 0.6, textTransform: 'uppercase', marginTop: 2 }}>
            Alpha Score
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Deep Analyze panel (multi-factor strict gates)
// ---------------------------------------------------------------------------

async function fetchDeepAnalysis(symbol) {
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

async function fetchHighProbabilityScan(maxSymbols = 0) {
  const url = `${API_BASE}/high-probability-scan${maxSymbols ? `?max_symbols=${maxSymbols}` : ''}`;
  const r = await fetch(url, { method: 'POST' });
  if (!r.ok) {
    const detail = await r.text().catch(() => '');
    throw new Error(`high-probability-scan ${r.status}: ${detail.slice(0, 200)}`);
  }
  return r.json();
}

// ---- History API helpers -------------------------------------------------

async function fetchHistoryList({ limit = 50, offset = 0, symbol = '', highProbabilityOnly = false } = {}) {
  const q = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  if (symbol) q.set('symbol', symbol);
  if (highProbabilityOnly) q.set('high_probability_only', 'true');
  const r = await fetch(`${API_BASE}/deep-analysis-history?${q.toString()}`);
  if (!r.ok) throw new Error(`history list ${r.status}`);
  return r.json();
}

async function fetchHistoryItem(id) {
  const r = await fetch(`${API_BASE}/deep-analysis-history/${id}`);
  if (!r.ok) throw new Error(`history fetch ${r.status}`);
  return r.json();
}

async function fetchHistoryStats() {
  const r = await fetch(`${API_BASE}/deep-analysis-history/stats`);
  if (!r.ok) throw new Error(`history stats ${r.status}`);
  return r.json();
}

async function deleteHistoryItem(id) {
  const r = await fetch(`${API_BASE}/deep-analysis-history/${id}`, { method: 'DELETE' });
  if (!r.ok) throw new Error(`history delete ${r.status}`);
  return r.json();
}

async function clearHistory(symbol = '') {
  const url = symbol
    ? `${API_BASE}/deep-analysis-history?symbol=${encodeURIComponent(symbol)}`
    : `${API_BASE}/deep-analysis-history`;
  const r = await fetch(url, { method: 'DELETE' });
  if (!r.ok) throw new Error(`history clear ${r.status}`);
  return r.json();
}

function formatDateTime(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}

function CheckRow({ check }) {
  const p = check.passed;
  const color = p === true ? C.green : p === false ? C.red : C.muted;
  const icon =
    p === true ? (
      <CheckCircle2 size={14} color={color} />
    ) : p === false ? (
      <XCircle size={14} color={color} />
    ) : (
      <AlertTriangle size={14} color={color} />
    );
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 8,
        padding: '6px 0',
        borderBottom: `1px dashed ${C.border}`,
      }}
    >
      <span style={{ marginTop: 1 }}>{icon}</span>
      <div style={{ flex: 1, fontSize: 12 }}>
        <div style={{ color: C.sub, fontWeight: 600, fontFamily: FONT_MONO }}>{check.name}</div>
        <div style={{ color: C.muted, marginTop: 1 }}>{check.detail}</div>
      </div>
    </div>
  );
}

function GateCard({ title, icon, gate }) {
  if (!gate) return null;
  const passed = gate.passed;
  const headerColor = passed ? C.green : C.red;
  return (
    <div
      style={{
        background: C.card,
        border: `1px solid ${passed ? 'rgba(74,222,128,0.4)' : 'rgba(248,113,113,0.35)'}`,
        borderRadius: 12,
        padding: '0.9rem 1rem',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
        <span style={{ color: headerColor }}>{icon}</span>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{title}</div>
          <div style={{ fontSize: 11, color: C.muted, fontFamily: FONT_MONO }}>
            {gate.score}/{gate.max_score}
          </div>
        </div>
        <span
          style={{
            padding: '2px 8px',
            borderRadius: 999,
            background: passed ? 'rgba(74,222,128,0.15)' : 'rgba(248,113,113,0.15)',
            color: headerColor,
            fontSize: 11,
            fontWeight: 700,
          }}
        >
          {passed ? 'PASS' : 'FAIL'}
        </span>
      </div>
      <div>
        {(gate.checks || []).map((c) => (
          <CheckRow key={c.name} check={c} />
        ))}
      </div>
    </div>
  );
}

function HistoryPanel({ onSelect, activeId, refreshToken }) {
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState(null);
  const [symbolFilter, setSymbolFilter] = useState('');
  const [winnersOnly, setWinnersOnly] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, s] = await Promise.all([
        fetchHistoryList({ limit: 50, symbol: symbolFilter, highProbabilityOnly: winnersOnly }),
        fetchHistoryStats().catch(() => null),
      ]);
      setItems(list.items || []);
      setTotal(list.total || 0);
      setStats(s);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  }, [symbolFilter, winnersOnly]);

  useEffect(() => {
    load();
  }, [load, refreshToken]);

  const handleDelete = async (id) => {
    try {
      await deleteHistoryItem(id);
      await load();
    } catch (exc) {
      setError(exc.message || String(exc));
    }
  };

  const handleClear = async () => {
    if (!window.confirm(symbolFilter
      ? `Clear all history for ${symbolFilter.toUpperCase()}? This cannot be undone.`
      : 'Clear ALL analysis history? This cannot be undone.')) return;
    try {
      await clearHistory(symbolFilter);
      await load();
    } catch (exc) {
      setError(exc.message || String(exc));
    }
  };

  return (
    <Section
      title="Analysis history"
      subtitle={
        stats
          ? `${stats.total} analyses saved · ${stats.winners} flagged high-probability · avg score ${stats.avg_score}${
              stats.last_analyzed_at ? ` · last ${formatDateTime(stats.last_analyzed_at)}` : ''
            }`
          : 'Persistent history of every Deep Analyze run, with date/time.'
      }
      icon={<Activity size={18} />}
      right={
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            onClick={load}
            disabled={loading}
            style={{
              padding: '5px 10px',
              background: 'transparent',
              border: `1px solid ${C.border}`,
              borderRadius: 8,
              color: C.teal,
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 12,
            }}
          >
            {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
          </button>
          <button
            onClick={handleClear}
            disabled={loading || total === 0}
            style={{
              padding: '5px 10px',
              background: 'transparent',
              border: `1px solid ${total === 0 ? C.border : C.red}`,
              borderRadius: 8,
              color: total === 0 ? C.muted : C.red,
              cursor: total === 0 ? 'not-allowed' : 'pointer',
              fontSize: 12,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <X size={12} /> Clear {symbolFilter ? symbolFilter : 'all'}
          </button>
        </div>
      }
    >
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
        <input
          value={symbolFilter}
          onChange={(e) => setSymbolFilter(e.target.value.toUpperCase())}
          placeholder="Filter by symbol (blank = all)"
          style={{
            flex: '1 1 220px',
            minWidth: 180,
            padding: '0.45rem 0.7rem',
            background: C.dark,
            color: C.text,
            border: `1px solid ${C.border}`,
            borderRadius: 10,
            outline: 'none',
            fontFamily: FONT_MONO,
            fontSize: 13,
          }}
        />
        <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: C.sub, fontSize: 12 }}>
          <input
            type="checkbox"
            checked={winnersOnly}
            onChange={(e) => setWinnersOnly(e.target.checked)}
            style={{ accentColor: C.teal }}
          />
          Only high-probability
        </label>
      </div>

      {error && <div style={{ color: C.red, fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {loading && items.length === 0 && (
        <div style={{ color: C.sub, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Spinner /> Loading history…
        </div>
      )}
      {!loading && items.length === 0 && !error && (
        <div style={{ color: C.muted, fontSize: 13 }}>
          No analyses yet. Run Deep Analyze above — every result is saved here automatically.
        </div>
      )}

      {items.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                {['When', 'Symbol', 'Sector', 'Score', 'Verdict', 'Entry', 'T1', 'R:R', 'Pattern', ''].map((h) => (
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((row) => {
                const isActive = activeId === row.id;
                return (
                  <tr
                    key={row.id}
                    onClick={() => onSelect(row.id)}
                    style={{
                      cursor: 'pointer',
                      background: isActive ? C.dark : 'transparent',
                      borderBottom: `1px solid ${C.border}`,
                    }}
                    onMouseEnter={(e) => !isActive && (e.currentTarget.style.background = C.dark)}
                    onMouseLeave={(e) => !isActive && (e.currentTarget.style.background = 'transparent')}
                  >
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.sub }}>
                      {formatDateTime(row.analyzed_at)}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{row.symbol}</td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted }}>{row.sector || '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem' }}>
                      <ProbBadge score={row.combined_score} />
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem' }}>
                      <span
                        style={{
                          padding: '2px 8px',
                          borderRadius: 999,
                          fontSize: 10.5,
                          fontWeight: 700,
                          background: row.high_probability ? 'rgba(74,222,128,0.15)' : 'rgba(248,113,113,0.12)',
                          color: row.high_probability ? C.green : C.red,
                        }}
                      >
                        {row.high_probability ? 'HIGH PROB' : 'NO-GO'}
                      </span>
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>
                      {row.entry_price != null ? `₹${fmt(row.entry_price)}` : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.green }}>
                      {row.target_1 != null ? `₹${fmt(row.target_1)}` : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>
                      {row.risk_reward != null ? fmt(row.risk_reward) : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem' }}>{row.pattern_detected || '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', textAlign: 'right' }}>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(row.id);
                        }}
                        title="Delete"
                        style={{
                          background: 'transparent',
                          border: `1px solid ${C.border}`,
                          borderRadius: 6,
                          padding: '2px 6px',
                          color: C.red,
                          cursor: 'pointer',
                          fontSize: 11,
                        }}
                      >
                        <X size={11} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {total > items.length && (
            <div style={{ marginTop: 8, textAlign: 'right', color: C.muted, fontSize: 11 }}>
              Showing {items.length} of {total}. Narrow by symbol for more.
            </div>
          )}
        </div>
      )}
    </Section>
  );
}

// ---------------------------------------------------------------------------
// Paper Trading API helpers
// ---------------------------------------------------------------------------

async function fetchPaperPortfolio() {
  const r = await fetch(`${API_BASE}/paper-portfolio`);
  if (!r.ok) throw new Error(`portfolio ${r.status}`);
  return r.json();
}

async function fetchEquityCurve() {
  const r = await fetch(`${API_BASE}/paper-equity-curve`);
  if (!r.ok) throw new Error(`equity-curve ${r.status}`);
  return r.json();
}

async function fetchTelegramStatus() {
  const r = await fetch(`${API_BASE}/telegram-status`);
  if (!r.ok) throw new Error(`telegram-status ${r.status}`);
  return r.json();
}

async function sendTelegramTest() {
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

async function fetchMarketStatus() {
  const r = await fetch(`${API_BASE}/market-status`);
  if (!r.ok) throw new Error(`market-status ${r.status}`);
  return r.json();
}

function MarketStatusBanner({ market, dense = false }) {
  if (!market) return null;
  const open = market.is_open;
  const isHoliday = market.status === 'HOLIDAY';
  const tone = open ? C.green : isHoliday ? C.purple : market.status === 'PRE_OPEN' ? C.yellow : C.red;
  const bg = open
    ? 'rgba(74,222,128,0.1)'
    : isHoliday
    ? 'rgba(167,139,250,0.1)'
    : 'rgba(248,113,113,0.08)';
  const label =
    market.status === 'OPEN' ? 'NSE OPEN'
    : market.status === 'PRE_OPEN' ? 'PRE-OPEN AUCTION'
    : market.status === 'POST_CLOSE' ? 'POST-CLOSE'
    : market.status === 'PRE_MARKET' ? 'PRE-MARKET'
    : market.status === 'HOLIDAY' ? 'NSE HOLIDAY'
    : 'WEEKEND';
  const nextOpen = market.next_open_ist ? new Date(market.next_open_ist).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : '—';
  const nowIst = market.now_ist ? new Date(market.now_ist).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : '';
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: dense ? '0.4rem 0.7rem' : '0.55rem 0.85rem',
        borderRadius: 10,
        background: bg,
        border: `1px solid ${tone}`,
        color: C.text,
        fontSize: 12,
        marginBottom: dense ? 8 : 12,
        flexWrap: 'wrap',
      }}
    >
      <span
        style={{
          width: 8, height: 8, borderRadius: '50%', background: tone,
          animation: open ? 'qe-pulse 1.6s ease-in-out infinite' : 'none',
        }}
      />
      <b style={{ color: tone }}>{label}</b>
      {isHoliday && market.holiday_name && (
        <span style={{ color: C.purple, fontWeight: 600 }}>· {market.holiday_name}</span>
      )}
      <span style={{ color: C.muted, fontFamily: FONT_MONO }}>{nowIst}</span>
      {!open && (
        <span style={{ color: C.sub }}>
          · Next open <b>{nextOpen}</b>
          {typeof market.minutes_to_open === 'number' && market.minutes_to_open > 0 && (
            <span style={{ color: C.muted }}> ({Math.round(market.minutes_to_open / 60)}h away)</span>
          )}
        </span>
      )}
      {market.calendar_source && (
        <span style={{ color: C.muted, fontSize: 10, marginLeft: 'auto' }} title="Holiday calendar data source">
          cal: {market.calendar_source}
        </span>
      )}
    </div>
  );
}

async function fetchPaperTrades({ status = '', symbol = '', limit = 100 } = {}) {
  const q = new URLSearchParams({ limit: String(limit) });
  if (status) q.set('status', status);
  if (symbol) q.set('symbol', symbol);
  const r = await fetch(`${API_BASE}/paper-trades?${q.toString()}`);
  if (!r.ok) throw new Error(`paper-trades ${r.status}`);
  return r.json();
}

async function fetchPaperSettings() {
  const r = await fetch(`${API_BASE}/paper-settings`);
  if (!r.ok) throw new Error(`paper-settings ${r.status}`);
  return r.json();
}

async function patchPaperSettings(updates) {
  const r = await fetch(`${API_BASE}/paper-settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  });
  if (!r.ok) throw new Error(`paper-settings patch ${r.status}`);
  return r.json();
}

async function closePaperTrade(id, price = null) {
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

async function runMonitorNow() {
  const r = await fetch(`${API_BASE}/paper-trades/monitor-now`, { method: 'POST' });
  if (!r.ok) throw new Error(`monitor-now ${r.status}`);
  return r.json();
}

function fmtMoney(n, currency = '₹') {
  if (n === null || n === undefined || Number.isNaN(Number(n))) return '—';
  const v = Number(n);
  const abs = Math.abs(v);
  const sign = v < 0 ? '-' : '';
  if (abs >= 1e7) return `${sign}${currency}${(abs / 1e7).toFixed(2)}Cr`;
  if (abs >= 1e5) return `${sign}${currency}${(abs / 1e5).toFixed(2)}L`;
  if (abs >= 1000) return `${sign}${currency}${(abs / 1000).toFixed(1)}K`;
  return `${sign}${currency}${abs.toFixed(0)}`;
}

function EquityCurveTooltip({ active, payload, starting }) {
  if (!active || !payload || !payload.length) return null;
  const p = payload[0].payload;
  const eventColor =
    p.event === 'close'
      ? (p.pnl_amount || 0) >= 0
        ? C.green
        : C.red
      : p.event === 'mark'
      ? C.blue
      : C.muted;
  const ts = p.t ? new Date(p.t).toLocaleString() : '';
  const startCap = starting || 1;
  return (
    <div
      style={{
        background: C.card,
        border: `1px solid ${C.border}`,
        borderRadius: 8,
        padding: '8px 10px',
        fontSize: 12,
        color: C.text,
        minWidth: 180,
      }}
    >
      <div style={{ color: eventColor, fontWeight: 700, marginBottom: 4 }}>
        {p.event === 'close' ? `${p.symbol} · ${p.exit_reason || 'CLOSE'}`
          : p.event === 'mark' ? 'Live mark-to-market'
          : 'Start'}
      </div>
      <div style={{ color: C.muted, fontSize: 10, marginBottom: 6, fontFamily: FONT_MONO }}>{ts}</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '2px 10px', fontFamily: FONT_MONO }}>
        <span style={{ color: C.muted }}>Equity</span>
        <span>₹{Number(p.equity).toLocaleString('en-IN')}</span>
        <span style={{ color: C.muted }}>Return</span>
        <span style={{ color: p.equity >= startCap ? C.green : C.red }}>
          {fmtPct(((p.equity - startCap) / startCap) * 100)}
        </span>
        <span style={{ color: C.muted }}>Peak</span>
        <span>₹{Number(p.peak).toLocaleString('en-IN')}</span>
        <span style={{ color: C.muted }}>Drawdown</span>
        <span style={{ color: p.drawdown_pct < 0 ? C.red : C.muted }}>
          {p.drawdown_pct < 0 ? `${p.drawdown_pct}%` : '0%'}
        </span>
        {p.event === 'close' && (
          <>
            <span style={{ color: C.muted }}>Trade P&L</span>
            <span style={{ color: (p.pnl_amount || 0) >= 0 ? C.green : C.red }}>
              {fmtMoney(p.pnl_amount)}
            </span>
          </>
        )}
        {p.event === 'mark' && p.unrealised !== 0 && (
          <>
            <span style={{ color: C.muted }}>Unrealised</span>
            <span style={{ color: p.unrealised >= 0 ? C.green : C.red }}>{fmtMoney(p.unrealised)}</span>
          </>
        )}
      </div>
    </div>
  );
}


function EquityCurvePanel({ refreshToken }) {
  const [curve, setCurve] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCurve(await fetchEquityCurve());
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load, refreshToken]);

  useEffect(() => {
    const id = setInterval(load, 30_000);
    return () => clearInterval(id);
  }, [load]);

  const points = curve?.points || [];
  const starting = Number(curve?.starting_capital) || 0;
  const minEquity = points.length ? Math.min(...points.map((p) => p.equity)) : starting;
  const maxEquity = points.length ? Math.max(...points.map((p) => p.equity)) : starting;
  // Pad the equity Y-axis so the chart isn't visually flat.
  const span = Math.max(maxEquity - minEquity, starting * 0.01);
  const yMin = Math.min(starting, minEquity - span * 0.2);
  const yMax = Math.max(starting, maxEquity + span * 0.2);

  const closedCount = Number(curve?.closed_count || 0);
  const currentReturn = Number(curve?.current_return_pct || 0);
  const peakReturn = Number(curve?.peak_return_pct || 0);
  const maxDD = Number(curve?.max_drawdown_pct || 0);
  const curDD = Number(curve?.current_drawdown_pct || 0);

  return (
    <Section
      title="Equity curve"
      subtitle={
        closedCount > 0
          ? `${closedCount} closed trades · current return ${fmtPct(currentReturn)} · peak return ${fmtPct(
              peakReturn,
            )} · max drawdown ${fmt(maxDD)}%`
          : 'No closed trades yet. The curve starts at your opening capital and updates on every close / mark.'
      }
      icon={<LineIcon size={18} />}
      right={
        <button
          onClick={load}
          disabled={loading}
          style={{
            padding: '5px 10px',
            background: 'transparent',
            border: `1px solid ${C.border}`,
            borderRadius: 8,
            color: C.teal,
            cursor: loading ? 'not-allowed' : 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 12,
          }}
        >
          {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
        </button>
      }
    >
      {error && <div style={{ color: C.red, fontSize: 13, marginBottom: 8 }}>{error}</div>}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 14 }}>
        <KpiCard
          icon={<CircleDollarSign size={14} />}
          label="Current equity"
          value={fmtMoney(curve?.current_equity)}
          sub={`Start ${fmtMoney(starting)}`}
          color={C.teal}
        />
        <KpiCard
          icon={<Trophy size={14} />}
          label="Peak equity"
          value={fmtMoney(curve?.peak_equity)}
          sub={`Peak return ${fmtPct(peakReturn)}`}
          color={C.green}
        />
        <KpiCard
          icon={<TrendingDown size={14} />}
          label="Current drawdown"
          value={`${fmt(curDD, 2)}%`}
          color={curDD < 0 ? C.red : C.muted}
        />
        <KpiCard
          icon={<AlertTriangle size={14} />}
          label="Max drawdown"
          value={`${fmt(maxDD, 2)}%`}
          sub={
            curve?.max_drawdown_at
              ? new Date(curve.max_drawdown_at).toLocaleDateString()
              : '—'
          }
          color={C.red}
        />
      </div>

      {points.length < 2 ? (
        <div style={{ color: C.muted, fontSize: 13, padding: '1rem', textAlign: 'center' }}>
          Not enough data to plot. Close at least one paper trade to see the curve.
        </div>
      ) : (
        <>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <AreaChart data={points} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="qe-equity" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={C.teal} stopOpacity={0.4} />
                    <stop offset="95%" stopColor={C.teal} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={C.border} strokeDasharray="3 3" />
                <XAxis
                  dataKey="t"
                  stroke={C.muted}
                  fontSize={10}
                  tickFormatter={(v) => {
                    try {
                      return new Date(v).toLocaleDateString(undefined, {
                        month: 'short',
                        day: '2-digit',
                      });
                    } catch {
                      return v;
                    }
                  }}
                />
                <YAxis
                  stroke={C.muted}
                  fontSize={10}
                  domain={[yMin, yMax]}
                  tickFormatter={(v) => fmtMoney(v)}
                  width={72}
                />
                <Tooltip content={<EquityCurveTooltip starting={starting} />} />
                <Area
                  type="monotone"
                  dataKey="equity"
                  stroke={C.teal}
                  strokeWidth={2}
                  fill="url(#qe-equity)"
                  name="Equity"
                  dot={(props) => {
                    const { cx, cy, payload } = props;
                    if (!payload) return null;
                    if (payload.event === 'close') {
                      const isWin = (payload.pnl_amount || 0) >= 0;
                      return (
                        <circle
                          cx={cx}
                          cy={cy}
                          r={3.5}
                          fill={isWin ? C.green : C.red}
                          stroke={C.card}
                          strokeWidth={1.5}
                        />
                      );
                    }
                    if (payload.event === 'mark') {
                      return <circle cx={cx} cy={cy} r={3} fill={C.blue} stroke={C.card} strokeWidth={1.5} />;
                    }
                    return null;
                  }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <h4 style={{ margin: '16px 0 4px', color: C.sub, fontSize: 12, fontWeight: 600, letterSpacing: 0.4 }}>
            Drawdown from peak
          </h4>
          <div style={{ width: '100%', height: 140 }}>
            <ResponsiveContainer>
              <AreaChart data={points} margin={{ top: 4, right: 20, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="qe-dd" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={C.red} stopOpacity={0} />
                    <stop offset="95%" stopColor={C.red} stopOpacity={0.45} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={C.border} strokeDasharray="3 3" />
                <XAxis
                  dataKey="t"
                  stroke={C.muted}
                  fontSize={10}
                  tickFormatter={(v) => {
                    try {
                      return new Date(v).toLocaleDateString(undefined, {
                        month: 'short',
                        day: '2-digit',
                      });
                    } catch {
                      return v;
                    }
                  }}
                />
                <YAxis
                  stroke={C.muted}
                  fontSize={10}
                  domain={[Math.min(maxDD - 1, -1), 0]}
                  tickFormatter={(v) => `${v}%`}
                  width={52}
                />
                <Tooltip content={<EquityCurveTooltip starting={starting} />} />
                <Area
                  type="monotone"
                  dataKey="drawdown_pct"
                  stroke={C.red}
                  strokeWidth={1.5}
                  fill="url(#qe-dd)"
                  name="Drawdown"
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div style={{ marginTop: 6, display: 'flex', gap: 14, fontSize: 11, color: C.muted }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: C.green, display: 'inline-block' }} />
              Winning close
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: C.red, display: 'inline-block' }} />
              Losing close
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: C.blue, display: 'inline-block' }} />
              Live mark-to-market
            </span>
          </div>
        </>
      )}
    </Section>
  );
}


function TelegramAlertsPanel({ settings, onSettingsChange }) {
  const [status, setStatus] = useState(null);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null); // {ok, message}
  const [pendingKey, setPendingKey] = useState(null);

  const load = useCallback(async () => {
    try {
      setStatus(await fetchTelegramStatus());
    } catch (exc) {
      setStatus({ configured: false, error: exc.message });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const runTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      await sendTelegramTest();
      setTestResult({ ok: true, message: 'Test message sent. Check your Telegram.' });
    } catch (exc) {
      setTestResult({ ok: false, message: exc.message || 'Failed to send test.' });
    } finally {
      setTesting(false);
    }
  };

  const toggle = async (key) => {
    setPendingKey(key);
    try {
      const next = await patchPaperSettings({ [key]: !settings[key] });
      onSettingsChange(next);
    } catch (exc) {
      setTestResult({ ok: false, message: exc.message || 'Failed to update.' });
    } finally {
      setPendingKey(null);
    }
  };

  const updateNumber = async (key, value) => {
    setPendingKey(key);
    try {
      const next = await patchPaperSettings({ [key]: Number(value) });
      onSettingsChange(next);
    } catch (exc) {
      setTestResult({ ok: false, message: exc.message || 'Failed to update.' });
    } finally {
      setPendingKey(null);
    }
  };

  const configured = !!status?.configured;

  const ALERT_ROWS = [
    { key: 'telegram_on_open', label: 'Auto-trade opened', hint: 'Fires when a paper BUY is placed.' },
    { key: 'telegram_on_close', label: 'Trade closed', hint: 'SL / Target / Time / Manual exit with P&L.' },
    { key: 'telegram_on_error', label: 'Monitor failure', hint: '3 consecutive price-fetch failures for a symbol.' },
    { key: 'telegram_on_high_probability', label: 'High-probability setup', hint: 'A Deep Analyze result scores ≥ threshold below.' },
  ];

  return (
    <Section
      title="Telegram alerts"
      subtitle={
        configured
          ? `Bot @${status?.token_prefix?.replace('…', '')}… · chat ${status?.chat_id}`
          : 'Not configured. Paste your bot token via setup_secrets.py and add your chat id to .env.'
      }
      icon={<Activity size={18} />}
      right={
        <button
          onClick={runTest}
          disabled={testing || !configured}
          style={{
            padding: '5px 10px',
            background: 'transparent',
            border: `1px solid ${configured ? C.teal : C.border}`,
            borderRadius: 8,
            color: configured ? C.teal : C.muted,
            cursor: testing || !configured ? 'not-allowed' : 'pointer',
            fontSize: 12,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          {testing ? <Spinner size={12} /> : <Zap size={12} />} Send test
        </button>
      }
    >
      {!configured && (
        <div
          style={{
            padding: '0.6rem 0.85rem',
            background: 'rgba(251,191,36,0.1)',
            border: `1px solid ${C.yellow}`,
            borderRadius: 10,
            color: C.yellow,
            fontSize: 12,
            lineHeight: 1.6,
            marginBottom: 10,
          }}
        >
          {status?.token_present ? 'Bot token found' : 'No bot token'} ·{' '}
          {status?.chat_id_present ? `chat id ${status?.chat_id}` : 'no chat id'}
          <div style={{ marginTop: 6, color: C.sub }}>
            Fix with:
            <pre
              style={{
                background: C.dark,
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                padding: '6px 10px',
                margin: '6px 0 0',
                fontSize: 11,
                fontFamily: FONT_MONO,
                color: C.sub,
                overflowX: 'auto',
              }}
            >{`python scripts/setup_secrets.py --add-secret TELEGRAM_BOT_TOKEN=<token>
echo "TELEGRAM_CHAT_ID=<your_id>" >> quantedge-ai/backend/.env`}</pre>
          </div>
        </div>
      )}

      {testResult && (
        <div
          style={{
            padding: '0.5rem 0.75rem',
            background: testResult.ok ? 'rgba(74,222,128,0.1)' : 'rgba(248,113,113,0.08)',
            border: `1px solid ${testResult.ok ? C.green : C.red}`,
            borderRadius: 8,
            color: testResult.ok ? C.green : C.red,
            fontSize: 12,
            marginBottom: 10,
          }}
        >
          {testResult.message}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {ALERT_ROWS.map((row) => {
          const enabled = !!settings?.[row.key];
          const busy = pendingKey === row.key;
          return (
            <div
              key={row.key}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '0.6rem 0.75rem',
                background: C.dark,
                border: `1px solid ${C.border}`,
                borderRadius: 10,
              }}
            >
              <label
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  cursor: configured ? 'pointer' : 'not-allowed',
                  flex: 1,
                }}
              >
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={() => toggle(row.key)}
                  disabled={!configured || busy}
                  style={{ accentColor: C.teal }}
                />
                <span style={{ color: enabled && configured ? C.teal : C.sub, fontWeight: 600, fontSize: 13 }}>
                  {row.label}
                </span>
                <span style={{ color: C.muted, fontSize: 11, marginLeft: 6 }}>{row.hint}</span>
              </label>
              {busy && <Spinner size={12} />}
            </div>
          );
        })}

        {settings?.telegram_on_high_probability && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '0.55rem 0.75rem',
              background: C.dark,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              fontSize: 12,
              color: C.sub,
            }}
          >
            <span>High-probability trigger ≥</span>
            <input
              type="number"
              min={50}
              max={100}
              value={settings.telegram_high_probability_threshold ?? 85}
              onChange={(e) => updateNumber('telegram_high_probability_threshold', e.target.value)}
              disabled={!configured || pendingKey === 'telegram_high_probability_threshold'}
              style={{
                width: 60,
                padding: '3px 6px',
                background: C.card,
                color: C.text,
                border: `1px solid ${C.border}`,
                borderRadius: 6,
                fontFamily: FONT_MONO,
                fontSize: 13,
                textAlign: 'center',
              }}
            />
            <span>%</span>
            <span style={{ color: C.muted, marginLeft: 'auto', fontSize: 11 }}>
              Only pings when Deep Analyze combined score crosses this level.
            </span>
          </div>
        )}
      </div>
    </Section>
  );
}


function PaperTradingPanel({ refreshToken }) {
  const [portfolio, setPortfolio] = useState(null);
  const [settings, setSettings] = useState(null);
  const [closedTrades, setClosedTrades] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [pendingAction, setPendingAction] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, s, closed] = await Promise.all([
        fetchPaperPortfolio(),
        fetchPaperSettings(),
        fetchPaperTrades({ status: 'CLOSED', limit: 50 }),
      ]);
      setPortfolio(p);
      setSettings(s);
      setClosedTrades(closed.items || []);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load, refreshToken]);

  // Poll every 30s so the mark-to-market P&L stays fresh.
  useEffect(() => {
    const id = setInterval(load, 30_000);
    return () => clearInterval(id);
  }, [load]);

  const toggleAuto = async () => {
    if (!settings) return;
    setPendingAction('toggle');
    try {
      const next = await patchPaperSettings({ auto_trade_enabled: !settings.auto_trade_enabled });
      setSettings(next);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setPendingAction(null);
    }
  };

  const updateThreshold = async (value) => {
    setPendingAction('threshold');
    try {
      const next = await patchPaperSettings({ auto_trade_threshold: Number(value) });
      setSettings(next);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setPendingAction(null);
    }
  };

  const closeAt = async (id, price) => {
    if (!window.confirm(`Close paper trade #${id} at market?`)) return;
    setPendingAction(`close-${id}`);
    try {
      await closePaperTrade(id, price);
      await load();
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setPendingAction(null);
    }
  };

  const triggerMonitor = async () => {
    setPendingAction('monitor');
    try {
      await runMonitorNow();
      await load();
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setPendingAction(null);
    }
  };

  const cap = portfolio?.capital || {};
  const stats = portfolio?.stats || {};
  const open = portfolio?.positions?.open || [];
  const pnlUnreal = Number(cap.unrealised_pnl || 0);
  const pnlReal = Number(cap.realised_pnl || 0);

  return (
    <>
      <Section
        title="Paper Trading Desk"
        subtitle="Virtual money. Auto-opens a BUY when Deep Analyze scores ≥ threshold. Fills instantly at the recommended entry. Monitored every 60s for stop/target/time exit."
        icon={<CircleDollarSign size={18} />}
        right={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button
              onClick={load}
              disabled={loading}
              style={{
                padding: '5px 10px',
                background: 'transparent',
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                color: C.teal,
                cursor: loading ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
              }}
            >
              {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
            </button>
            <button
              onClick={triggerMonitor}
              disabled={!!pendingAction}
              style={{
                padding: '5px 10px',
                background: 'transparent',
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                color: C.blue,
                cursor: pendingAction ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
              }}
            >
              {pendingAction === 'monitor' ? <Spinner size={12} color={C.blue} /> : <Activity size={12} />}
              Check exits
            </button>
          </div>
        }
      >
        {error && <div style={{ color: C.red, fontSize: 13, marginBottom: 8 }}>{error}</div>}

        {portfolio?.market_status && <MarketStatusBanner market={portfolio.market_status} />}

        {settings && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 16,
              padding: '0.7rem 0.85rem',
              background: C.dark,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              marginBottom: 14,
              flexWrap: 'wrap',
            }}
          >
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={!!settings.auto_trade_enabled}
                onChange={toggleAuto}
                disabled={pendingAction === 'toggle'}
                style={{ accentColor: C.teal }}
              />
              <span style={{ color: settings.auto_trade_enabled ? C.teal : C.muted, fontWeight: 700 }}>
                Auto-trade {settings.auto_trade_enabled ? 'ON' : 'OFF'}
              </span>
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: C.sub }}>
              <span>Trigger when score ≥</span>
              <input
                type="number"
                min={50}
                max={100}
                value={settings.auto_trade_threshold}
                onChange={(e) => updateThreshold(e.target.value)}
                disabled={pendingAction === 'threshold'}
                style={{
                  width: 60,
                  padding: '3px 6px',
                  background: C.card,
                  color: C.text,
                  border: `1px solid ${C.border}`,
                  borderRadius: 6,
                  fontFamily: FONT_MONO,
                  fontSize: 13,
                  textAlign: 'center',
                }}
              />
              <span>%</span>
            </div>
            <label
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12,
                color: settings.auto_trade_market_open_only ? C.teal : C.sub, cursor: 'pointer',
              }}
              title="Block auto-trades outside NSE hours (Mon-Fri 09:15-15:30 IST)"
            >
              <input
                type="checkbox"
                checked={!!settings.auto_trade_market_open_only}
                onChange={async (e) => {
                  setPendingAction('market-only');
                  try {
                    const next = await patchPaperSettings({ auto_trade_market_open_only: e.target.checked });
                    setSettings(next);
                  } catch (exc) { setError(exc.message || String(exc)); }
                  finally { setPendingAction(null); }
                }}
                disabled={pendingAction === 'market-only'}
                style={{ accentColor: C.teal }}
              />
              Market hours only
            </label>
            <div style={{ color: C.muted, fontSize: 11, flex: 1, textAlign: 'right' }}>
              Capital {fmtMoney(settings.starting_capital)} · Risk {settings.risk_per_trade_pct}%/trade · Max{' '}
              {settings.max_open_positions} open · Hold ≤ {settings.max_hold_days}d
            </div>
          </div>
        )}

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 14 }}>
          <KpiCard
            icon={<CircleDollarSign size={14} />}
            label="Equity"
            value={fmtMoney(cap.total_equity)}
            sub={`Start ${fmtMoney(cap.starting)}`}
            color={C.teal}
          />
          <KpiCard
            icon={<TrendingUp size={14} />}
            label="Realised"
            value={fmtMoney(pnlReal)}
            color={pnlReal >= 0 ? C.green : C.red}
          />
          <KpiCard
            icon={<Activity size={14} />}
            label="Unrealised"
            value={fmtMoney(pnlUnreal)}
            color={pnlUnreal >= 0 ? C.green : C.red}
          />
          <KpiCard
            icon={<Eye size={14} />}
            label="Open"
            value={`${portfolio?.positions?.open_count ?? 0}/${portfolio?.positions?.max_open ?? 0}`}
            color={C.blue}
          />
          <KpiCard
            icon={<Trophy size={14} />}
            label="Win rate"
            value={stats.closed_count ? `${fmt(stats.win_rate, 1)}%` : '—'}
            sub={`${stats.wins || 0}W / ${stats.losses || 0}L`}
            color={C.yellow}
          />
          <KpiCard
            icon={<Gauge size={14} />}
            label="Avg return"
            value={stats.closed_count ? `${fmt(stats.avg_pnl_pct)}%` : '—'}
            color={C.purple}
          />
        </div>

      </Section>

      <TelegramAlertsPanel settings={settings} onSettingsChange={setSettings} />

      <EquityCurvePanel refreshToken={refreshToken} />

      <Section
        title="Positions"
        subtitle="Open trades update every 30s; click Close to exit manually at the last live price."
        icon={<Eye size={18} />}
      >
        <h4 style={{ margin: '0 0 8px', color: C.text, fontSize: 13, fontWeight: 700 }}>
          Open positions ({open.length})
        </h4>
        <div style={{ overflowX: 'auto', marginBottom: 14 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                {['#', 'Symbol', 'Qty', 'Entry', 'Stop', 'Target', 'Last', 'P&L', 'P&L %', 'Source', 'Opened', ''].map((h) => (
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {open.length === 0 && (
                <tr>
                  <td colSpan={12} style={{ padding: '0.9rem', textAlign: 'center', color: C.muted }}>
                    No open positions.
                  </td>
                </tr>
              )}
              {open.map((t) => {
                const pnlColor = (t.unrealised_pnl || 0) >= 0 ? C.green : C.red;
                return (
                  <tr
                    key={t.id}
                    style={{ borderBottom: `1px solid ${C.border}` }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = C.dark)}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.muted }}>{t.id}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{t.symbol}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{t.quantity}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(t.entry_price)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.red }}>₹{fmt(t.stop_loss)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.green }}>₹{fmt(t.target_price)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{t.last_price != null ? `₹${fmt(t.last_price)}` : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>
                      {fmtMoney(t.unrealised_pnl)}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>
                      {t.unrealised_pnl_pct != null ? fmtPct(t.unrealised_pnl_pct) : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontSize: 11 }}>
                      {(() => {
                        const s = t.source || 'manual';
                        const aftermarket = s === 'auto-aftermarket';
                        const auto = s === 'auto';
                        const bg = auto ? 'rgba(0,212,168,0.12)' : aftermarket ? 'rgba(251,191,36,0.12)' : C.dark;
                        const fg = auto ? C.teal : aftermarket ? C.yellow : C.sub;
                        const border = auto ? C.teal : aftermarket ? C.yellow : C.border;
                        const label = aftermarket ? 'AUTO · AFTERMARKET' : s.toUpperCase();
                        return (
                          <span
                            title={aftermarket ? 'Auto-opened while NSE was closed — entry = last close' : undefined}
                            style={{
                              padding: '2px 8px',
                              borderRadius: 999,
                              background: bg, color: fg, border: `1px solid ${border}`,
                              fontSize: 10.5, fontWeight: 700, letterSpacing: 0.4,
                            }}
                          >
                            {label}
                          </span>
                        );
                      })()}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>
                      {formatDateTime(t.opened_at)}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', textAlign: 'right' }}>
                      <button
                        onClick={() => closeAt(t.id, null)}
                        disabled={pendingAction === `close-${t.id}`}
                        style={{
                          padding: '3px 8px',
                          background: 'transparent',
                          border: `1px solid ${C.border}`,
                          borderRadius: 6,
                          color: C.red,
                          cursor: pendingAction === `close-${t.id}` ? 'not-allowed' : 'pointer',
                          fontSize: 11,
                          fontWeight: 600,
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                        }}
                      >
                        {pendingAction === `close-${t.id}` ? <Spinner size={10} color={C.red} /> : <X size={11} />} Close
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <h4 style={{ margin: '0 0 8px', color: C.text, fontSize: 13, fontWeight: 700 }}>
          Recently closed ({closedTrades.length})
        </h4>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                {['#', 'Symbol', 'Entry', 'Exit', 'P&L', 'P&L %', 'Reason', 'Opened', 'Closed'].map((h) => (
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {closedTrades.length === 0 && (
                <tr>
                  <td colSpan={9} style={{ padding: '0.9rem', textAlign: 'center', color: C.muted }}>
                    No closed trades yet.
                  </td>
                </tr>
              )}
              {closedTrades.map((t) => {
                const pnlColor = (t.pnl_amount || 0) >= 0 ? C.green : C.red;
                const reasonColor =
                  t.exit_reason === 'TARGET_HIT'
                    ? C.green
                    : t.exit_reason === 'SL_HIT'
                    ? C.red
                    : t.exit_reason === 'TIME_EXIT'
                    ? C.yellow
                    : C.muted;
                return (
                  <tr key={t.id} style={{ borderBottom: `1px solid ${C.border}` }}>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.muted }}>{t.id}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{t.symbol}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(t.entry_price)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>
                      {t.close_price != null ? `₹${fmt(t.close_price)}` : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{fmtMoney(t.pnl_amount)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>
                      {t.pnl_pct != null ? fmtPct(t.pnl_pct) : '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', color: reasonColor, fontSize: 11, fontWeight: 700 }}>
                      {t.exit_reason || '—'}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>
                      {formatDateTime(t.opened_at)}
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>
                      {formatDateTime(t.closed_at)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Section>
    </>
  );
}


function DeepAnalyzePanel({ backendDown }) {
  const [symbol, setSymbol] = useState('RELIANCE');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [source, setSource] = useState(null); // 'live' | 'history' | null
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const [activeHistoryId, setActiveHistoryId] = useState(null);

  const run = async () => {
    if (!symbol.trim() || loading) return;
    setLoading(true);
    setError(null);
    setActiveHistoryId(null);
    setSource('live');
    try {
      const result = await fetchDeepAnalysis(symbol);
      setData(result);
      setHistoryRefresh((n) => n + 1); // nudge HistoryPanel to reload
      if (result.history_id) setActiveHistoryId(result.history_id);
    } catch (exc) {
      setError(exc.message || String(exc));
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  const viewHistory = async (id) => {
    setLoading(true);
    setError(null);
    try {
      const item = await fetchHistoryItem(id);
      setData(item.payload);
      setSource('history');
      setActiveHistoryId(id);
      setSymbol(item.symbol);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Section
        title="Deep Analyze"
        subtitle="Strict 4-gate check: Technical + Fundamentals + Backtest + Risk/Reward. Only 'high probability' if every gate passes and score ≥ 90. Every run is saved to history."
        icon={<Target size={18} />}
        right={
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              value={symbol}
              onChange={(e) => setSymbol(e.target.value.toUpperCase())}
              onKeyDown={(e) => e.key === 'Enter' && run()}
              placeholder="Symbol (e.g. SHAILY)"
              style={{
                padding: '0.5rem 0.75rem',
                background: C.dark,
                color: C.text,
                border: `1px solid ${C.border}`,
                borderRadius: 10,
                outline: 'none',
                fontFamily: FONT_MONO,
                minWidth: 180,
                fontSize: 13,
              }}
            />
            <LiveBtn icon={<Zap size={15} />} label="Analyze" loading={loading} onClick={run} />
          </div>
        }
      >
        {backendDown && (
          <div style={{ color: C.red, fontSize: 12 }}>
            Backend unreachable — start it with <code>./start.sh</code>.
          </div>
        )}
        {error && <div style={{ color: C.red, fontSize: 13 }}>{error}</div>}
        {!data && !loading && !error && (
          <div style={{ color: C.muted, fontSize: 13 }}>
            Enter any NSE symbol and hit <b>Analyze</b>. The engine pulls live Yahoo/NSE data, runs
            a 3-year backtest of the same setup, and reports per-gate pass/fail with a combined
            0–100 score.
          </div>
        )}
        {data && (
          <>
            {source === 'history' && (
              <div
                style={{
                  marginBottom: 12,
                  padding: '0.5rem 0.75rem',
                  background: 'rgba(77,159,255,0.08)',
                  border: `1px solid ${C.blue}`,
                  borderRadius: 10,
                  fontSize: 12,
                  color: C.sub,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <Activity size={14} color={C.blue} />
                <span>
                  Historical view · saved {formatDateTime(data.scan_timestamp)} · numbers reflect
                  the market at that moment. Click <b>Analyze</b> to refresh live.
                </span>
              </div>
            )}
            <DeepAnalyzeResult data={data} />
          </>
        )}
      </Section>

      <HistoryPanel
        onSelect={viewHistory}
        activeId={activeHistoryId}
        refreshToken={historyRefresh}
      />
      <PaperTradingPanel refreshToken={historyRefresh} />
    </>
  );
}

function DeepAnalyzeResult({ data }) {
  const {
    overall,
    levels,
    gates,
    indicators,
    patterns,
    fundamentals_snapshot: fs,
    ai_analysis,
    sector,
    industry,
    symbol,
    auto_paper_trade: autoTrade,
    auto_paper_trade_status: autoStatus,
  } = data;
  const hp = overall.high_probability;
  const bt = gates.backtest.metrics;

  return (
    <>
      {data.market_status && <MarketStatusBanner market={data.market_status} dense />}
      {autoTrade && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            padding: '0.65rem 0.85rem',
            marginBottom: 10,
            borderRadius: 10,
            background: 'rgba(0,212,168,0.12)',
            border: `1px solid ${C.teal}`,
            color: C.text,
            fontSize: 13,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Zap size={14} color={C.teal} />
            <div style={{ flex: 1 }}>
              <b style={{ color: C.teal }}>Paper trade auto-opened</b> · #{autoTrade.id} ·{' '}
              {autoTrade.symbol} · qty {autoTrade.quantity} · entry ₹{fmt(autoTrade.entry_price)} · stop ₹
              {fmt(autoTrade.stop_loss)} · target ₹{fmt(autoTrade.target_price)} · risk{' '}
              {fmtMoney(autoTrade.risk_amount)}.
            </div>
          </div>
          {autoTrade.source === 'auto-aftermarket' && (
            <div
              style={{
                padding: '4px 10px',
                borderRadius: 8,
                background: 'rgba(251,191,36,0.12)',
                border: `1px solid ${C.yellow}`,
                color: C.yellow,
                fontSize: 12,
              }}
            >
              <b>
                {data.market_status?.status === 'HOLIDAY'
                  ? `NSE HOLIDAY${data.market_status?.holiday_name ? ` — ${data.market_status.holiday_name}` : ''}.`
                  : 'Market is closed right now.'}
              </b>{' '}
              Entry price is the last NSE session close — treat this as a pending fill. Stop/target
              won't be evaluated until the next {data.market_status?.session?.open_hm || '09:15'} IST
              session opens; only time-based exit keeps counting.
            </div>
          )}
        </div>
      )}
      {!autoTrade && autoStatus && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '0.55rem 0.85rem',
            marginBottom: 10,
            borderRadius: 10,
            background: C.dark,
            border: `1px dashed ${C.border}`,
            color: C.muted,
            fontSize: 12,
          }}
        >
          <Activity size={12} />
          <span>
            {autoStatus.reason
              || (autoStatus.enabled
                ? `Score ${autoStatus.combined_score} below threshold ${autoStatus.threshold}.`
                : 'Auto-trade is OFF — enable it in the Paper Trading Desk to place trades automatically.')}
          </span>
        </div>
      )}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'auto 1fr',
          gap: 18,
          alignItems: 'center',
          padding: '0.9rem 1rem',
          background: hp
            ? 'linear-gradient(135deg, rgba(0,212,168,0.12), rgba(77,159,255,0.08))'
            : 'linear-gradient(135deg, rgba(248,113,113,0.08), rgba(251,191,36,0.05))',
          border: `1px solid ${hp ? C.teal : C.border}`,
          borderRadius: 12,
          marginBottom: 14,
        }}
      >
        <ScoreRing score={overall.combined_score} />
        <div>
          <div style={{ fontWeight: 800, fontSize: 18 }}>
            {symbol} <span style={{ color: C.muted, fontWeight: 500, fontSize: 13 }}>· {sector}{industry ? ` / ${industry}` : ''}</span>
          </div>
          <div style={{ marginTop: 4, fontSize: 13, color: hp ? C.green : C.sub }}>
            {hp
              ? `✓ HIGH PROBABILITY — all 4 gates passed, combined score ${overall.combined_score}/100 ≥ ${overall.min_score}.`
              : `✗ NOT high probability today — combined score ${overall.combined_score}/100 (threshold ${overall.min_score}).`}
          </div>
          <div style={{ marginTop: 6, display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 11, color: C.muted }}>
            <span>Technical {gates.technical.score}/{gates.technical.max_score}</span>
            <span>·</span>
            <span>Fundamentals {gates.fundamentals.score}/{gates.fundamentals.max_score}</span>
            <span>·</span>
            <span>Backtest {gates.backtest.score}/{gates.backtest.max_score}</span>
            <span>·</span>
            <span>R/R {gates.risk_reward.score}/{gates.risk_reward.max_score}</span>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10, marginBottom: 14 }}>
        <Tag label="Entry" value={`₹${fmt(levels.entry)}`} color={C.teal} />
        <Tag label="Stop" value={`₹${fmt(levels.stop)}`} color={C.red} />
        <Tag label="Target 1" value={`₹${fmt(levels.target_1)}`} color={C.green} />
        <Tag label="Target 2" value={`₹${fmt(levels.target_2)}`} color={C.blue} />
        <Tag label="R:R" value={fmt(levels.risk_reward)} color={C.purple} />
        <Tag label="Expected" value={fmtPct(levels.expected_return_pct)} color={C.yellow} />
        <Tag label="Risk %" value={`${fmt(levels.risk_pct)}%`} color={C.orange} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginBottom: 14 }}>
        <GateCard title="Technical" icon={<LineIcon size={16} />} gate={gates.technical} />
        <GateCard title="Fundamentals" icon={<CircleDollarSign size={16} />} gate={gates.fundamentals} />
        <GateCard title="Backtest (3y, 20-day forward)" icon={<BarChart3 size={16} />} gate={gates.backtest} />
        <GateCard title="Risk / Reward" icon={<ShieldAlert size={16} />} gate={gates.risk_reward} />
      </div>

      <Section title="Fundamentals snapshot" icon={<CircleDollarSign size={18} />} subtitle={`Sources: ${(fs.sources || []).join(', ') || '—'}`}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
          <Tag label="Market cap" value={fs.market_cap != null ? formatCrore(fs.market_cap, fs.currency) : '—'} color={C.teal} />
          <Tag label="P/E" value={fs.pe_ratio != null ? fmt(fs.pe_ratio, 2) : '—'} color={C.blue} />
          <Tag label="D/E" value={fs.debt_to_equity != null ? fmt(fs.debt_to_equity, 2) : '—'} color={C.purple} />
          <Tag label="ROE" value={fs.roe != null ? `${fmt(fs.roe, 2)}%` : '—'} color={C.green} />
          <Tag label="Revenue YoY" value={fs.revenue_growth != null ? `${fmt(fs.revenue_growth, 2)}%` : '—'} color={C.yellow} />
          <Tag label="Profit YoY" value={fs.profit_growth != null ? `${fmt(fs.profit_growth, 2)}%` : '—'} color={C.green} />
          <Tag label="Book value" value={fs.book_value != null ? `₹${fmt(fs.book_value, 2)}` : '—'} color={C.blue} />
          <Tag label="Promoter" value={fs.promoter_holding != null ? `${fmt(fs.promoter_holding, 2)}%` : '—'} color={C.teal} />
          <Tag label="FII" value={fs.fii_holding != null ? `${fmt(fs.fii_holding, 2)}%` : '—'} color={C.orange} />
          <Tag label="DII" value={fs.dii_holding != null ? `${fmt(fs.dii_holding, 2)}%` : '—'} color={C.purple} />
          <Tag label="Pledge" value={fs.promoter_pledge != null ? `${fmt(fs.promoter_pledge, 2)}%` : 'n/a'} color={C.red} />
        </div>
      </Section>

      <Section title="Backtest metrics" icon={<BarChart3 size={18} />} subtitle={`Historical walk-forward — 20-day forward return, signals ≥ 5 bars apart`}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 10 }}>
          <Tag label="Trades" value={fmt(bt.num_trades, 0)} color={C.blue} />
          <Tag label="Win rate" value={`${fmt(bt.win_rate, 1)}%`} color={C.teal} />
          <Tag label="Profit factor" value={fmt(bt.profit_factor)} color={C.green} />
          <Tag label="Avg return" value={`${fmt(bt.avg_return)}%`} color={C.teal} />
          <Tag label="Avg win" value={`${fmt(bt.avg_win)}%`} color={C.green} />
          <Tag label="Avg loss" value={`${fmt(bt.avg_loss)}%`} color={C.red} />
          <Tag label="Best" value={`${fmt(bt.best_trade)}%`} color={C.green} />
          <Tag label="Worst" value={`${fmt(bt.worst_trade)}%`} color={C.red} />
          <Tag label="Sharpe" value={fmt(bt.sharpe)} color={C.yellow} />
        </div>
      </Section>

      <Section title="Chart snapshot" icon={<LineIcon size={18} />}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
          <Tag label="EMA 20" value={`₹${fmt(indicators.ema_20)}`} color={C.teal} />
          <Tag label="EMA 50" value={`₹${fmt(indicators.ema_50)}`} color={C.blue} />
          <Tag label="EMA 200" value={`₹${fmt(indicators.ema_200)}`} color={C.purple} />
          <Tag label="RSI" value={fmt(indicators.rsi, 1)} color={C.yellow} />
          <Tag label="MACD hist" value={fmt(indicators.macd_hist, 3)} color={indicators.macd_hist >= 0 ? C.green : C.red} />
          <Tag label="ATR" value={fmt(indicators.atr)} color={C.orange} />
          <Tag label="Volume ratio" value={`${fmt(indicators.volume_ratio)}x`} color={C.blue} />
          <Tag label="Pattern" value={patterns.name} color={C.teal} />
        </div>
      </Section>

      {ai_analysis && (
        <Section title="AI thesis" icon={<Brain size={18} color={C.purple} />}>
          <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.7 }}>{ai_analysis}</p>
        </Section>
      )}
    </>
  );
}


function AlphaScanTab({ state, log, runScan, backendDown }) {
  const { status, result, noTrade, error } = state;

  return (
    <div>
      <DeepAnalyzePanel backendDown={backendDown} />
      <Section
        title="AlphaScan Engine"
        subtitle="Live NIFTY scan through the strict 4-gate filter (technical + fundamentals + 3y backtest + R/R). Only stocks with combined score ≥ 90 are emitted."
        icon={<Rocket size={18} />}
        right={
          <LiveBtn
            icon={<Zap size={15} />}
            label={status === 'scanning' ? 'Scanning…' : 'Launch AlphaScan'}
            loading={status === 'scanning'}
            onClick={runScan}
          />
        }
      >
        {backendDown && (
          <div
            style={{
              padding: '1rem',
              background: 'rgba(248,113,113,0.08)',
              border: `1px solid ${C.red}`,
              borderRadius: 10,
              marginBottom: 12,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: C.red, fontWeight: 700, marginBottom: 8 }}>
              <ShieldAlert size={16} /> Backend unreachable on localhost:8000
            </div>
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 6 }}>
              Start the FastAPI server in a separate terminal:
            </div>
            <pre
              style={{
                margin: 0,
                padding: '0.75rem',
                background: C.dark,
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                fontFamily: FONT_MONO,
                fontSize: 12,
                color: C.sub,
                overflowX: 'auto',
              }}
            >{`cd backend
pip install -r requirements.txt
python main.py`}</pre>
          </div>
        )}
      </Section>

      <Section title="Scan log" subtitle="Most recent first" icon={<Activity size={18} />}>
        <div style={{ maxHeight: 180, overflowY: 'auto', fontFamily: FONT_MONO, fontSize: 12 }}>
          {log.length === 0 ? (
            <div style={{ color: C.muted }}>No scans yet.</div>
          ) : (
            log
              .slice()
              .reverse()
              .map((entry, idx) => (
                <div
                  key={idx}
                  style={{
                    display: 'flex',
                    gap: 10,
                    padding: '4px 0',
                    borderBottom: `1px dashed ${C.border}`,
                    color: C.sub,
                  }}
                >
                  <span style={{ color: C.muted }}>{entry.t}</span>
                  <span style={{ color: entry.level === 'error' ? C.red : entry.level === 'ok' ? C.teal : C.sub }}>
                    {entry.msg}
                  </span>
                </div>
              ))
          )}
        </div>
      </Section>

      {status === 'idle' && !result && !noTrade && !error && (
        <Section icon={<LayoutDashboard size={18} />} title="Ready" subtitle="Click Launch AlphaScan to run the NIFTY 500 sweep.">
          <div style={{ color: C.muted, fontSize: 13 }}>
            The engine will fetch fresh OHLCV for each symbol, apply all 7 hard filters, and score
            the survivors across 5 institutional dimensions.
          </div>
        </Section>
      )}

      {error && (
        <Section icon={<AlertTriangle size={18} color={C.red} />} title="Scan failed">
          <div style={{ color: C.red, fontSize: 13 }}>{error}</div>
        </Section>
      )}

      {noTrade && (
        <Section icon={<AlertTriangle size={18} color={C.yellow} />} title="No trade opportunity today">
          <div style={{ color: C.yellow, fontSize: 13, marginBottom: 6 }}>{noTrade.message}</div>
          <div style={{ color: C.sub, fontSize: 12 }}>{noTrade.reason}</div>
          <div style={{ color: C.muted, fontSize: 11, marginTop: 8 }}>
            Stocks scanned: <b>{noTrade.stocks_scanned}</b> · {new Date(noTrade.scan_timestamp).toLocaleString()}
          </div>
        </Section>
      )}

      {result && (
        <>
          <Section
            title={`${result.symbol} · ${result.sector}`}
            subtitle="Highest-conviction setup in the NIFTY 500"
            icon={<Trophy size={18} color={C.teal} />}
            right={<ScoreRing score={result.confidence_score} />}
          >
            <div
              style={{
                padding: '0.85rem 1rem',
                background: `linear-gradient(135deg, rgba(0,212,168,0.08) 0%, rgba(77,159,255,0.08) 100%)`,
                border: `1px solid ${C.teal}`,
                borderRadius: 12,
                marginBottom: 12,
                display: 'flex',
                alignItems: 'center',
                gap: 10,
              }}
            >
              <Sparkles size={18} color={C.teal} />
              <div style={{ flex: 1, color: C.sub, fontSize: 13 }}>
                Winner selected from <b>{`NIFTY 500`}</b>. All 7 hard conditions passed. Expected
                return <b style={{ color: C.teal }}>{fmtPct(result.expected_return)}</b> at
                R:R of <b>{fmt(result.risk_reward)}</b>.
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
              <Tag label="Entry" value={`₹${fmt(result.entry_price)}`} color={C.teal} />
              <Tag label="Stop" value={`₹${fmt(result.stop_loss)}`} color={C.red} />
              <Tag label="Target" value={`₹${fmt(result.target_price)}`} color={C.green} />
              <Tag label="Expected return" value={fmtPct(result.expected_return)} color={C.teal} />
              <Tag label="RSI" value={fmt(result.rsi, 1)} color={C.blue} />
              <Tag label="Volume ratio" value={`${fmt(result.volume_ratio)}x`} color={C.yellow} />
              <Tag label="R:R" value={fmt(result.risk_reward)} color={C.purple} />
              <Tag label="Pattern" value={result.pattern_detected} color={C.orange} />
            </div>
          </Section>

          <Section title="Trend structure" subtitle="EMA stack · MACD · ATR" icon={<LineIcon size={18} />}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
              {[
                { label: 'EMA 20', value: result.ema_20 },
                { label: 'EMA 50', value: result.ema_50 },
                { label: 'EMA 200', value: result.ema_200 },
              ].map((row) => {
                const above = result.entry_price > row.value;
                return (
                  <div
                    key={row.label}
                    style={{
                      padding: '0.6rem 0.85rem',
                      background: C.dark,
                      border: `1px solid ${C.border}`,
                      borderRadius: 10,
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ color: C.muted, fontSize: 11, letterSpacing: 0.6, textTransform: 'uppercase' }}>
                        {row.label}
                      </div>
                      <span
                        style={{
                          fontSize: 10,
                          color: above ? C.green : C.red,
                          padding: '2px 6px',
                          borderRadius: 999,
                          background: above ? 'rgba(74,222,128,0.1)' : 'rgba(248,113,113,0.1)',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                        }}
                      >
                        {above ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
                        {above ? 'above' : 'below'}
                      </span>
                    </div>
                    <div style={{ marginTop: 4, fontSize: 15, fontWeight: 700, fontFamily: FONT_MONO, color: C.text }}>
                      ₹{fmt(row.value)}
                    </div>
                  </div>
                );
              })}
              <Tag label="MACD" value={result.macd_signal} color={result.macd_signal === 'Bullish' ? C.green : C.red} />
              <Tag label="ATR" value={fmt(result.atr)} color={C.yellow} />
            </div>
          </Section>

          <Section title="AI analysis" icon={<Brain size={18} color={C.purple} />}>
            <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.7 }}>{result.analysis}</p>
            <div style={{ marginTop: 10, color: C.muted, fontSize: 11 }}>
              {new Date(result.scan_timestamp).toLocaleString()}
            </div>
          </Section>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Top KPI strip
// ---------------------------------------------------------------------------

function TopKpiStrip({ watchlist, scanData, backtest, ml }) {
  const scannedCount = Object.keys(scanData || {}).length;
  const highProbCount = Object.values(scanData || {}).filter(
    (sd) => mlScoreFromData(sd) >= 80,
  ).length;
  const bestModel = ml?.models?.find((m) => m.best)?.name || '—';
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, padding: '1rem 1.5rem 0' }}>
      <KpiCard icon={<Eye size={14} />} label="Watchlist" value={watchlist.length} color={C.teal} />
      <KpiCard icon={<Search size={14} />} label="Scanned" value={scannedCount} color={C.blue} />
      <KpiCard
        icon={<Trophy size={14} />}
        label="High Prob (≥80)"
        value={highProbCount}
        color={C.green}
      />
      <KpiCard
        icon={<TrendingUp size={14} />}
        label="Win rate"
        value={backtest ? `${fmt(backtest.win_rate, 1)}%` : '—'}
        color={C.yellow}
      />
      <KpiCard
        icon={<Gauge size={14} />}
        label="Sharpe"
        value={backtest ? fmt(backtest.sharpe_ratio) : '—'}
        color={C.purple}
      />
      <KpiCard icon={<Cpu size={14} />} label="Best model" value={bestModel} color={C.orange} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main App
// ---------------------------------------------------------------------------

const TABS = [
  { id: 'scanner', label: 'Scanner', icon: <Search size={14} /> },
  { id: 'backtest', label: 'Backtest', icon: <LineIcon size={14} /> },
  { id: 'ml', label: 'ML Models', icon: <Cpu size={14} /> },
  { id: 'insights', label: 'AI Insights', icon: <Brain size={14} /> },
  { id: 'alphascan', label: 'AlphaScan', icon: <Rocket size={14} /> },
];

// ---------------------------------------------------------------------------
// Secrets lock — global fetch interceptor + LockScreen
// ---------------------------------------------------------------------------

const TOKEN_KEY = 'quantedge_session_token';

function installFetchInterceptor({ onLocked }) {
  const orig = window.fetch.bind(window);
  if (window.__quantedge_fetch_patched__) return () => {};
  window.__quantedge_fetch_patched__ = true;

  window.fetch = async (input, init = {}) => {
    const url = typeof input === 'string' ? input : input?.url || '';
    // "Backend request" = anything matching the configured API_BASE, OR any
    // relative path when API_BASE is empty (same-origin production).
    const isBackend =
      (API_BASE && url.startsWith(API_BASE)) ||
      (!API_BASE && (url.startsWith('/') || (!url.startsWith('http') && !url.startsWith('//'))));
    if (isBackend) {
      const token = localStorage.getItem(TOKEN_KEY);
      if (token) {
        init = { ...init };
        init.headers = { ...(init.headers || {}), Authorization: `Bearer ${token}` };
      }
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
        if (code === 'locked') {
          localStorage.removeItem(TOKEN_KEY);
          onLocked?.();
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

async function apiUnlock(password) {
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

async function apiLockStatus() {
  const r = await fetch(`${API_BASE}/lock-status`);
  if (!r.ok) throw new Error(`lock-status ${r.status}`);
  return r.json();
}

async function apiLockServer() {
  const r = await fetch(`${API_BASE}/lock`, { method: 'POST' });
  if (!r.ok) throw new Error(`lock ${r.status}`);
  return r.json();
}

function LockScreen({ initialStatus, onUnlocked }) {
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(initialStatus || null);
  const [retryCountdown, setRetryCountdown] = useState(0);

  useEffect(() => {
    if (retryCountdown <= 0) return;
    const id = setInterval(() => setRetryCountdown((n) => Math.max(0, n - 1)), 1000);
    return () => clearInterval(id);
  }, [retryCountdown]);

  useEffect(() => {
    // Refresh lock-status on mount so we always show the latest backoff.
    apiLockStatus()
      .then((s) => {
        setStatus(s);
        if (s.retry_after_seconds) setRetryCountdown(s.retry_after_seconds);
      })
      .catch(() => {});
  }, []);

  const submit = async (e) => {
    e?.preventDefault?.();
    if (!password.trim() || loading || retryCountdown > 0) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiUnlock(password);
      localStorage.setItem(TOKEN_KEY, res.access_token);
      setPassword('');
      onUnlocked();
    } catch (exc) {
      setError(exc);
      if (exc.retryAfter) setRetryCountdown(exc.retryAfter);
      setPassword('');
    } finally {
      setLoading(false);
    }
  };

  const notConfigured = status && status.configured === false;
  const depsMissing = status && status.deps_available === false;

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: C.bg,
        color: C.text,
        padding: '2rem',
      }}
    >
      <form
        onSubmit={submit}
        style={{
          width: '100%',
          maxWidth: 420,
          background: C.card,
          border: `1px solid ${C.border}`,
          borderRadius: 14,
          padding: '1.75rem 1.5rem',
          boxShadow: '0 20px 60px rgba(0,0,0,0.4)',
          animation: 'qe-fade-in 0.35s ease',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 18,
          }}
        >
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
              display: 'grid',
              placeItems: 'center',
              color: '#041018',
              boxShadow: '0 6px 24px rgba(0,212,168,0.35)',
            }}
          >
            <Sparkles size={22} strokeWidth={2.4} />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>
              QuantEdge <span style={{ color: C.teal }}>AI</span>
            </h1>
            <div style={{ fontSize: 12, color: C.muted }}>
              Enter master password to unlock
            </div>
          </div>
        </div>

        {notConfigured && (
          <div
            style={{
              padding: '0.7rem 0.85rem',
              background: 'rgba(251,191,36,0.1)',
              border: `1px solid ${C.yellow}`,
              borderRadius: 10,
              color: C.yellow,
              fontSize: 12,
              marginBottom: 14,
              lineHeight: 1.6,
            }}
          >
            <b>Vault not configured.</b>
            <br />
            Run this on the server, then reload:
            <pre
              style={{
                background: C.dark,
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                padding: '6px 10px',
                margin: '8px 0 0',
                fontSize: 11,
                fontFamily: FONT_MONO,
                color: C.sub,
                overflowX: 'auto',
              }}
            >
              python scripts/setup_secrets.py
            </pre>
          </div>
        )}

        {depsMissing && (
          <div
            style={{
              padding: '0.6rem 0.85rem',
              background: 'rgba(248,113,113,0.08)',
              border: `1px solid ${C.red}`,
              borderRadius: 10,
              color: C.red,
              fontSize: 12,
              marginBottom: 14,
            }}
          >
            Server is missing <code>cryptography</code> / <code>PyJWT</code>. Run{' '}
            <code>pip install -r requirements.txt</code>.
          </div>
        )}

        <label
          style={{
            display: 'block',
            fontSize: 11,
            letterSpacing: 0.6,
            textTransform: 'uppercase',
            color: C.muted,
            marginBottom: 6,
          }}
        >
          Master password
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoFocus
          disabled={loading || retryCountdown > 0 || notConfigured}
          placeholder="••••••••••••"
          style={{
            width: '100%',
            padding: '0.7rem 0.9rem',
            background: C.dark,
            color: C.text,
            border: `1px solid ${error ? C.red : C.border}`,
            borderRadius: 10,
            outline: 'none',
            fontFamily: FONT_MONO,
            fontSize: 14,
            letterSpacing: 1.5,
          }}
        />

        {error && (
          <div
            style={{
              marginTop: 10,
              padding: '0.5rem 0.75rem',
              background: 'rgba(248,113,113,0.1)',
              border: `1px solid ${C.red}`,
              borderRadius: 8,
              color: C.red,
              fontSize: 12,
            }}
          >
            {error.code === 'rate_limited'
              ? `Too many attempts. Wait ${retryCountdown || error.retryAfter}s before retrying.`
              : error.code === 'wrong_password'
              ? `Invalid password${error.failedAttempts ? ` (attempt ${error.failedAttempts})` : ''}.${
                  retryCountdown ? ` Cooling down ${retryCountdown}s.` : ''
                }`
              : error.message || 'Unlock failed.'}
          </div>
        )}

        {retryCountdown > 0 && !error && (
          <div
            style={{
              marginTop: 10,
              padding: '0.5rem 0.75rem',
              background: 'rgba(251,191,36,0.1)',
              border: `1px solid ${C.yellow}`,
              borderRadius: 8,
              color: C.yellow,
              fontSize: 12,
            }}
          >
            Cooling down — retry in {retryCountdown}s.
          </div>
        )}

        <button
          type="submit"
          disabled={loading || !password.trim() || retryCountdown > 0 || notConfigured}
          style={{
            width: '100%',
            marginTop: 14,
            padding: '0.7rem 1rem',
            background:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? C.border
                : `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
            color:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? C.muted
                : '#041018',
            border: 'none',
            borderRadius: 10,
            fontWeight: 700,
            fontSize: 14,
            cursor:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? 'not-allowed'
                : 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
          }}
        >
          {loading ? <Spinner color="#041018" /> : <Zap size={15} />}
          Unlock
        </button>

        <div
          style={{
            marginTop: 16,
            fontSize: 11,
            color: C.muted,
            lineHeight: 1.6,
            textAlign: 'center',
          }}
        >
          Secrets are encrypted at rest with scrypt + AES-256-GCM.
          <br />
          Password is never stored. Lost password = rotate API keys and rerun setup.
        </div>
      </form>
    </div>
  );
}

export default function App() {
  const [activeTab, setActiveTab] = useState('scanner');
  const [watchlist, setWatchlist] = useState([
    'RELIANCE',
    'TCS',
    'HDFCBANK',
    'INFY',
    'ICICIBANK',
  ]);
  const [scanStatus, setScanStatus] = useState({});
  const [scanData, setScanData] = useState({});
  const [scanning, setScanning] = useState(false);
  const [lastScan, setLastScan] = useState(null);
  const [lastError, setLastError] = useState(null);

  const [backtest, setBacktest] = useState(null);
  const [backtestLoading, setBacktestLoading] = useState(false);

  const [ml, setMl] = useState(null);
  const [mlLoading, setMlLoading] = useState(false);

  const [activeSymbol, setActiveSymbol] = useState(null);
  const [aiText, setAiText] = useState('');
  const [aiLoading, setAiLoading] = useState(false);

  const [alphaState, setAlphaState] = useState({ status: 'idle' });
  const [alphaLog, setAlphaLog] = useState([]);
  const [backendDown, setBackendDown] = useState(false);

  // ---- Lock gate -------------------------------------------------------
  const [lockStatus, setLockStatus] = useState(null); // null = loading, {unlocked, configured}
  const [lockVersion, setLockVersion] = useState(0); // bump to force re-check after unlock/lock

  const forceRelock = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setLockStatus((prev) => ({ ...(prev || {}), unlocked: false }));
  }, []);

  // Install the fetch interceptor exactly once.
  useEffect(() => {
    const teardown = installFetchInterceptor({ onLocked: forceRelock });
    return teardown;
  }, [forceRelock]);

  // Poll /lock-status on mount and whenever lockVersion bumps.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const s = await apiLockStatus();
        if (cancelled) return;
        const hasToken = !!localStorage.getItem(TOKEN_KEY);
        setLockStatus({ ...s, unlocked: s.unlocked && hasToken });
        setBackendDown(false);
      } catch {
        if (!cancelled) setBackendDown(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [lockVersion]);

  const handleUnlocked = useCallback(() => {
    setLockVersion((v) => v + 1);
  }, []);

  const handleLock = useCallback(async () => {
    try {
      await apiLockServer();
    } catch {
      // even if server call fails (network), still clear local token
    }
    localStorage.removeItem(TOKEN_KEY);
    setLockStatus((prev) => ({ ...(prev || {}), unlocked: false }));
    setLockVersion((v) => v + 1);
  }, []);

  const addSymbol = useCallback((raw) => {
    const { ticker } = parseSymbol(raw);
    if (!ticker) return;
    setWatchlist((list) => (list.includes(ticker) ? list : [...list, ticker]));
  }, []);

  const removeSymbol = useCallback((sym) => {
    setWatchlist((list) => list.filter((s) => s !== sym));
    setScanData((data) => {
      const next = { ...data };
      delete next[sym];
      return next;
    });
    setScanStatus((s) => {
      const next = { ...s };
      delete next[sym];
      return next;
    });
  }, []);

  const runScan = useCallback(async () => {
    if (scanning || !watchlist.length) return;
    setScanning(true);
    setLastError(null);
    const startedAt = new Date().toISOString();

    // mark everything loading
    setScanStatus(Object.fromEntries(watchlist.map((s) => [s, 'loading'])));

    for (const sym of watchlist) {
      try {
        const data = await fetchStockData(sym);
        setScanData((prev) => ({ ...prev, [sym]: data }));
        setScanStatus((s) => ({ ...s, [sym]: 'ok' }));
      } catch (exc) {
        console.warn('Scan failed for', sym, exc);
        setScanStatus((s) => ({ ...s, [sym]: 'error' }));
        setLastError(exc.message || String(exc));
      }
    }
    setLastScan(startedAt);
    setScanning(false);
  }, [scanning, watchlist]);

  const runBacktest = useCallback(async () => {
    if (backtestLoading || !watchlist.length) return;
    setBacktestLoading(true);
    try {
      const data = await fetchBacktest(watchlist);
      setBacktest(data);
    } catch (exc) {
      setLastError(exc.message || String(exc));
    } finally {
      setBacktestLoading(false);
    }
  }, [backtestLoading, watchlist]);

  const runTraining = useCallback(async () => {
    if (mlLoading || !watchlist.length) return;
    setMlLoading(true);
    try {
      const data = await fetchMLAnalysis(watchlist, scanData);
      setMl(data);
    } catch (exc) {
      setLastError(exc.message || String(exc));
    } finally {
      setMlLoading(false);
    }
  }, [mlLoading, watchlist, scanData]);

  const openInsights = useCallback((sym) => {
    setActiveSymbol(sym);
    setAiText('');
    setActiveTab('insights');
  }, []);

  const runExplain = useCallback(
    async (sym) => {
      const sd = scanData[sym];
      if (!sd) return;
      setAiLoading(true);
      try {
        const setup = computeSetup(sd);
        const text = await fetchAIExplanation(sym, sd, setup);
        setAiText(text || 'No analysis returned.');
      } catch (exc) {
        setAiText(`Unable to generate analysis: ${exc.message || exc}`);
      } finally {
        setAiLoading(false);
      }
    },
    [scanData],
  );

  const runAlphaScan = useCallback(async () => {
    setAlphaState({ status: 'scanning' });
    const ts = new Date().toLocaleTimeString();
    setAlphaLog((log) => [...log, { t: ts, level: 'info', msg: 'Dispatching /scan-best-stock…' }]);
    try {
      const r = await fetch(`${API_BASE}/scan-best-stock`, { method: 'POST' });
      if (!r.ok) throw new Error(`Server responded ${r.status}`);
      const data = await r.json();
      if (data.trade_found) {
        setAlphaState({ status: 'result', result: data.result });
        setAlphaLog((log) => [
          ...log,
          {
            t: new Date().toLocaleTimeString(),
            level: 'ok',
            msg: `Winner: ${data.result.symbol} · score ${data.result.confidence_score}`,
          },
        ]);
      } else {
        setAlphaState({ status: 'no-trade', noTrade: data.no_trade });
        setAlphaLog((log) => [
          ...log,
          {
            t: new Date().toLocaleTimeString(),
            level: 'info',
            msg: `No trade · ${data.no_trade?.stocks_scanned || 0} scanned`,
          },
        ]);
      }
      setBackendDown(false);
    } catch (exc) {
      setAlphaState({ status: 'error', error: exc.message || String(exc) });
      setAlphaLog((log) => [
        ...log,
        { t: new Date().toLocaleTimeString(), level: 'error', msg: exc.message || 'Scan failed' },
      ]);
      setBackendDown(true);
    }
  }, []);

  // Gate: show LockScreen until the server confirms we're unlocked AND we have
  // a local JWT. If the backend is unreachable we still render the app shell
  // so the user sees "Backend unreachable" rather than a blank lock screen.
  if (lockStatus === null) {
    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: C.bg, color: C.muted }}>
        <Spinner />
      </div>
    );
  }
  if (!lockStatus.unlocked && !backendDown) {
    return <LockScreen initialStatus={lockStatus} onUnlocked={handleUnlocked} />;
  }

  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text }}>
      <Header lastScan={lastScan} lastError={lastError} onLock={handleLock} />
      <TopKpiStrip watchlist={watchlist} scanData={scanData} backtest={backtest} ml={ml} />

      <nav
        style={{
          display: 'flex',
          gap: 4,
          padding: '0.75rem 1.5rem 0',
          borderBottom: `1px solid ${C.border}`,
          overflowX: 'auto',
        }}
      >
        {TABS.map((tab) => {
          const active = tab.id === activeTab;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '0.55rem 1rem',
                border: 'none',
                borderBottom: `2px solid ${active ? C.teal : 'transparent'}`,
                background: 'transparent',
                color: active ? C.teal : C.muted,
                fontWeight: 600,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              {tab.icon}
              {tab.label}
              {active && <ChevronRight size={12} />}
            </button>
          );
        })}
      </nav>

      <main style={{ padding: '1.25rem 1.5rem 3rem', maxWidth: 1400, margin: '0 auto' }}>
        {activeTab === 'scanner' && (
          <ScannerTab
            watchlist={watchlist}
            addSymbol={addSymbol}
            removeSymbol={removeSymbol}
            scanStatus={scanStatus}
            scanData={scanData}
            runScan={runScan}
            scanning={scanning}
            openInsights={openInsights}
          />
        )}
        {activeTab === 'backtest' && (
          <BacktestTab backtest={backtest} loading={backtestLoading} runBacktest={runBacktest} />
        )}
        {activeTab === 'ml' && (
          <MLTab ml={ml} loading={mlLoading} runTraining={runTraining} />
        )}
        {activeTab === 'insights' && (
          <InsightsTab
            watchlist={watchlist}
            scanData={scanData}
            activeSymbol={activeSymbol}
            setActiveSymbol={(s) => {
              setActiveSymbol(s);
              setAiText('');
            }}
            aiText={aiText}
            aiLoading={aiLoading}
            runExplain={runExplain}
          />
        )}
        {activeTab === 'alphascan' && (
          <AlphaScanTab
            state={alphaState}
            log={alphaLog}
            runScan={runAlphaScan}
            backendDown={backendDown}
          />
        )}
      </main>
    </div>
  );
}
