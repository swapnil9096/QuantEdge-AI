import React, { useState, useCallback, useEffect } from 'react';
import {
  Activity, AlertTriangle, CircleDollarSign, Eye, Gauge,
  LineChart as LineIcon, RefreshCw, TrendingDown, TrendingUp, Trophy, X, Zap,
} from 'lucide-react';
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, KpiCard, MarketStatusBanner } from './shared.jsx';
import { fmt, fmtPct, fmtMoney, formatDateTime } from '../utils/format.js';
import {
  fetchPaperPortfolio, fetchEquityCurve, fetchPaperSettings, patchPaperSettings,
  fetchPaperTrades, closePaperTrade, runMonitorNow, fetchTelegramStatus, sendTelegramTest,
} from '../utils/api.js';

// ---------------------------------------------------------------------------
// EquityCurveTooltip
// ---------------------------------------------------------------------------

function EquityCurveTooltip({ active, payload, starting }) {
  if (!active || !payload || !payload.length) return null;
  const p = payload[0].payload;
  const eventColor =
    p.event === 'close'
      ? (p.pnl_amount || 0) >= 0 ? C.green : C.red
      : p.event === 'mark' ? C.blue : C.muted;
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
        {p.event === 'close'
          ? `${p.symbol} · ${p.exit_reason || 'CLOSE'}`
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

// ---------------------------------------------------------------------------
// EquityCurvePanel
// ---------------------------------------------------------------------------

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

  useEffect(() => { load(); }, [load, refreshToken]);
  useEffect(() => {
    const id = setInterval(load, 30_000);
    return () => clearInterval(id);
  }, [load]);

  const points = curve?.points || [];
  const starting = Number(curve?.starting_capital) || 0;
  const minEquity = points.length ? Math.min(...points.map((p) => p.equity)) : starting;
  const maxEquity = points.length ? Math.max(...points.map((p) => p.equity)) : starting;
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
          ? `${closedCount} closed trades · current return ${fmtPct(currentReturn)} · peak return ${fmtPct(peakReturn)} · max drawdown ${fmt(maxDD)}%`
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
        <KpiCard icon={<CircleDollarSign size={14} />} label="Current equity" value={fmtMoney(curve?.current_equity)} sub={`Start ${fmtMoney(starting)}`} color={C.teal} />
        <KpiCard icon={<Trophy size={14} />} label="Peak equity" value={fmtMoney(curve?.peak_equity)} sub={`Peak return ${fmtPct(peakReturn)}`} color={C.green} />
        <KpiCard icon={<TrendingDown size={14} />} label="Current drawdown" value={`${fmt(curDD, 2)}%`} color={curDD < 0 ? C.red : C.muted} />
        <KpiCard
          icon={<AlertTriangle size={14} />}
          label="Max drawdown"
          value={`${fmt(maxDD, 2)}%`}
          sub={curve?.max_drawdown_at ? new Date(curve.max_drawdown_at).toLocaleDateString() : '—'}
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
                    try { return new Date(v).toLocaleDateString(undefined, { month: 'short', day: '2-digit' }); }
                    catch { return v; }
                  }}
                />
                <YAxis stroke={C.muted} fontSize={10} domain={[yMin, yMax]} tickFormatter={(v) => fmtMoney(v)} width={72} />
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
                      return <circle cx={cx} cy={cy} r={3.5} fill={isWin ? C.green : C.red} stroke={C.card} strokeWidth={1.5} />;
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
                    try { return new Date(v).toLocaleDateString(undefined, { month: 'short', day: '2-digit' }); }
                    catch { return v; }
                  }}
                />
                <YAxis stroke={C.muted} fontSize={10} domain={[Math.min(maxDD - 1, -1), 0]} tickFormatter={(v) => `${v}%`} width={52} />
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

// ---------------------------------------------------------------------------
// TelegramAlertsPanel
// ---------------------------------------------------------------------------

function TelegramAlertsPanel({ settings, onSettingsChange }) {
  const [status, setStatus] = useState(null);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [pendingKey, setPendingKey] = useState(null);
  // Local draft for threshold input — decouples visual typing from API calls
  const [thresholdDraft, setThresholdDraft] = useState(null);

  const load = useCallback(async () => {
    try {
      setStatus(await fetchTelegramStatus());
    } catch (exc) {
      setStatus({ configured: false, error: exc.message });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

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
    const n = parseInt(value, 10);
    if (!Number.isFinite(n) || n < 0 || n > 100) return;
    setPendingKey(key);
    try {
      const next = await patchPaperSettings({ [key]: n });
      onSettingsChange(next);
    } catch (exc) {
      setTestResult({ ok: false, message: exc.message || 'Failed to update.' });
    } finally {
      setPendingKey(null);
      setThresholdDraft(null);
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
            >{`python scripts/setup_secrets.py --add-secret TELEGRAM_BOT_TOKEN=<token>\necho "TELEGRAM_CHAT_ID=<your_id>" >> quantedge-ai/backend/.env`}</pre>
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
              value={thresholdDraft ?? settings.telegram_high_probability_threshold ?? 85}
              onChange={(e) => setThresholdDraft(e.target.value)}
              onBlur={(e) => updateNumber('telegram_high_probability_threshold', e.target.value)}
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

// ---------------------------------------------------------------------------
// PaperTradingPanel (main export)
// ---------------------------------------------------------------------------

export function PaperTradingPanel({ refreshToken }) {
  const [portfolio, setPortfolio] = useState(null);
  const [settings, setSettings] = useState(null);
  const [closedTrades, setClosedTrades] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [pendingAction, setPendingAction] = useState(null);
  // Local draft for threshold input — decouples visual typing from API calls
  const [thresholdDraft, setThresholdDraft] = useState(null);

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

  useEffect(() => { load(); }, [load, refreshToken]);
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
    const n = parseInt(value, 10);
    if (!Number.isFinite(n) || n < 0 || n > 100) return;
    setPendingAction('threshold');
    try {
      const next = await patchPaperSettings({ auto_trade_threshold: n });
      setSettings(next);
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setPendingAction(null);
      setThresholdDraft(null);
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
                value={thresholdDraft ?? settings.auto_trade_threshold}
                onChange={(e) => setThresholdDraft(e.target.value)}
                onBlur={(e) => updateThreshold(e.target.value)}
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
          <KpiCard icon={<CircleDollarSign size={14} />} label="Equity" value={fmtMoney(cap.total_equity)} sub={`Start ${fmtMoney(cap.starting)}`} color={C.teal} />
          <KpiCard icon={<TrendingUp size={14} />} label="Realised" value={fmtMoney(pnlReal)} color={pnlReal >= 0 ? C.green : C.red} />
          <KpiCard icon={<Activity size={14} />} label="Unrealised" value={fmtMoney(pnlUnreal)} color={pnlUnreal >= 0 ? C.green : C.red} />
          <KpiCard icon={<Eye size={14} />} label="Open" value={`${portfolio?.positions?.open_count ?? 0}/${portfolio?.positions?.max_open ?? 0}`} color={C.blue} />
          <KpiCard icon={<Trophy size={14} />} label="Win rate" value={stats.closed_count ? `${fmt(stats.win_rate, 1)}%` : '—'} sub={`${stats.wins || 0}W / ${stats.losses || 0}L`} color={C.yellow} />
          <KpiCard icon={<Gauge size={14} />} label="Avg return" value={stats.closed_count ? `${fmt(stats.avg_pnl_pct)}%` : '—'} color={C.purple} />
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
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {open.length === 0 && (
                <tr>
                  <td colSpan={12} style={{ padding: '0.9rem', textAlign: 'center', color: C.muted }}>No open positions.</td>
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
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{fmtMoney(t.unrealised_pnl)}</td>
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
                            style={{ padding: '2px 8px', borderRadius: 999, background: bg, color: fg, border: `1px solid ${border}`, fontSize: 10.5, fontWeight: 700, letterSpacing: 0.4 }}
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
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {closedTrades.length === 0 && (
                <tr>
                  <td colSpan={9} style={{ padding: '0.9rem', textAlign: 'center', color: C.muted }}>No closed trades yet.</td>
                </tr>
              )}
              {closedTrades.map((t) => {
                const pnlColor = (t.pnl_amount || 0) >= 0 ? C.green : C.red;
                const reasonColor =
                  t.exit_reason === 'TARGET_HIT' ? C.green
                  : t.exit_reason === 'SL_HIT' ? C.red
                  : t.exit_reason === 'TIME_EXIT' ? C.yellow
                  : C.muted;
                return (
                  <tr key={t.id} style={{ borderBottom: `1px solid ${C.border}` }}>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.muted }}>{t.id}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{t.symbol}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(t.entry_price)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{t.close_price != null ? `₹${fmt(t.close_price)}` : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{fmtMoney(t.pnl_amount)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{t.pnl_pct != null ? fmtPct(t.pnl_pct) : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', color: reasonColor, fontSize: 11, fontWeight: 700 }}>{t.exit_reason || '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>{formatDateTime(t.opened_at)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>{formatDateTime(t.closed_at)}</td>
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
