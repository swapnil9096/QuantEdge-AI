import React, { useState, useCallback, useEffect } from 'react';
import {
  Activity, BarChart3, Brain, CircleDollarSign, LayoutDashboard,
  LineChart as LineIcon, RefreshCw, Rocket, ShieldAlert, Sparkles,
  Target, TrendingDown, TrendingUp, Trophy, X, Zap,
} from 'lucide-react';
import { C, FONT_MONO, API_BASE } from '../constants.js';
import { Spinner, Section, LiveBtn, ScoreRing, Tag, ProbBadge, MarketStatusBanner, GateCard } from './shared.jsx';
import { fmt, fmtPct, formatCrore, formatDateTime } from '../utils/format.js';
import {
  fetchDeepAnalysis, fetchHistoryList, fetchHistoryItem, fetchHistoryStats,
  deleteHistoryItem, clearHistory,
} from '../utils/api.js';
import { PaperTradingPanel } from './PaperTradingPanel.jsx';

// ---------------------------------------------------------------------------
// HistoryPanel
// ---------------------------------------------------------------------------

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

  useEffect(() => { load(); }, [load, refreshToken]);

  const handleDelete = async (id) => {
    try {
      await deleteHistoryItem(id);
      await load();
    } catch (exc) {
      setError(exc.message || String(exc));
    }
  };

  const handleClear = async () => {
    if (!window.confirm(
      symbolFilter
        ? `Clear all history for ${symbolFilter.toUpperCase()}? This cannot be undone.`
        : 'Clear ALL analysis history? This cannot be undone.',
    )) return;
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
          ? `${stats.total} analyses saved · ${stats.winners} flagged high-probability · avg score ${stats.avg_score}${stats.last_analyzed_at ? ` · last ${formatDateTime(stats.last_analyzed_at)}` : ''}`
          : 'Persistent history of every Deep Analyze run, with date/time.'
      }
      icon={<Activity size={18} />}
      right={
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            onClick={load}
            disabled={loading}
            style={{
              padding: '5px 10px', background: 'transparent', border: `1px solid ${C.border}`,
              borderRadius: 8, color: C.teal, cursor: loading ? 'not-allowed' : 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12,
            }}
          >
            {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
          </button>
          <button
            onClick={handleClear}
            disabled={loading || total === 0}
            style={{
              padding: '5px 10px', background: 'transparent',
              border: `1px solid ${total === 0 ? C.border : C.red}`,
              borderRadius: 8, color: total === 0 ? C.muted : C.red,
              cursor: total === 0 ? 'not-allowed' : 'pointer',
              fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6,
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
            flex: '1 1 220px', minWidth: 180, padding: '0.45rem 0.7rem',
            background: C.dark, color: C.text, border: `1px solid ${C.border}`,
            borderRadius: 10, outline: 'none', fontFamily: FONT_MONO, fontSize: 13,
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
                  <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
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
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.sub }}>{formatDateTime(row.analyzed_at)}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{row.symbol}</td>
                    <td style={{ padding: '0.55rem 0.6rem', color: C.muted }}>{row.sector || '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem' }}><ProbBadge score={row.combined_score} /></td>
                    <td style={{ padding: '0.55rem 0.6rem' }}>
                      <span
                        style={{
                          padding: '2px 8px', borderRadius: 999, fontSize: 10.5, fontWeight: 700,
                          background: row.high_probability ? 'rgba(74,222,128,0.15)' : 'rgba(248,113,113,0.12)',
                          color: row.high_probability ? C.green : C.red,
                        }}
                      >
                        {row.high_probability ? 'HIGH PROB' : 'NO-GO'}
                      </span>
                    </td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{row.entry_price != null ? `₹${fmt(row.entry_price)}` : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.green }}>{row.target_1 != null ? `₹${fmt(row.target_1)}` : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{row.risk_reward != null ? fmt(row.risk_reward) : '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem' }}>{row.pattern_detected || '—'}</td>
                    <td style={{ padding: '0.55rem 0.6rem', textAlign: 'right' }}>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleDelete(row.id); }}
                        title="Delete"
                        style={{
                          background: 'transparent', border: `1px solid ${C.border}`,
                          borderRadius: 6, padding: '2px 6px', color: C.red, cursor: 'pointer', fontSize: 11,
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
// DeepAnalyzeResult
// ---------------------------------------------------------------------------

function DeepAnalyzeResult({ data }) {
  const {
    overall, levels, gates, indicators, patterns,
    fundamentals_snapshot: fs, ai_analysis, sector, industry, symbol,
    auto_paper_trade: autoTrade, auto_paper_trade_status: autoStatus,
  } = data;
  const hp = overall.high_probability;
  const bt = gates.backtest.metrics;

  return (
    <>
      {data.market_status && <MarketStatusBanner market={data.market_status} dense />}
      {autoTrade && (
        <div
          style={{
            display: 'flex', flexDirection: 'column', gap: 6,
            padding: '0.65rem 0.85rem', marginBottom: 10, borderRadius: 10,
            background: 'rgba(0,212,168,0.12)', border: `1px solid ${C.teal}`, color: C.text, fontSize: 13,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Zap size={14} color={C.teal} />
            <div style={{ flex: 1 }}>
              <b style={{ color: C.teal }}>Paper trade auto-opened</b> · #{autoTrade.id} ·{' '}
              {autoTrade.symbol} · qty {autoTrade.quantity} · entry ₹{fmt(autoTrade.entry_price)} · stop ₹
              {fmt(autoTrade.stop_loss)} · target ₹{fmt(autoTrade.target_price)} · risk{' '}
              {(() => { const v = Number(autoTrade.risk_amount); return v >= 1000 ? `₹${(v/1000).toFixed(1)}K` : `₹${v.toFixed(0)}`; })()}.
            </div>
          </div>
          {autoTrade.source === 'auto-aftermarket' && (
            <div
              style={{
                padding: '4px 10px', borderRadius: 8,
                background: 'rgba(251,191,36,0.12)', border: `1px solid ${C.yellow}`, color: C.yellow, fontSize: 12,
              }}
            >
              <b>
                {data.market_status?.status === 'HOLIDAY'
                  ? `NSE HOLIDAY${data.market_status?.holiday_name ? ` — ${data.market_status.holiday_name}` : ''}.`
                  : 'Market is closed right now.'}
              </b>{' '}
              Entry price is the last NSE session close — treat this as a pending fill.
            </div>
          )}
        </div>
      )}
      {!autoTrade && autoStatus && (
        <div
          style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '0.55rem 0.85rem', marginBottom: 10, borderRadius: 10,
            background: C.dark, border: `1px dashed ${C.border}`, color: C.muted, fontSize: 12,
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
          display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 18,
          alignItems: 'center', padding: '0.9rem 1rem',
          background: hp
            ? 'linear-gradient(135deg, rgba(0,212,168,0.12), rgba(77,159,255,0.08))'
            : 'linear-gradient(135deg, rgba(248,113,113,0.08), rgba(251,191,36,0.05))',
          border: `1px solid ${hp ? C.teal : C.border}`,
          borderRadius: 12, marginBottom: 14,
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

      <Section title="Backtest metrics" icon={<BarChart3 size={18} />} subtitle="Historical walk-forward — 20-day forward return, signals ≥ 5 bars apart">
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

// ---------------------------------------------------------------------------
// DeepAnalyzePanel
// ---------------------------------------------------------------------------

function DeepAnalyzePanel({ backendDown }) {
  const [symbol, setSymbol] = useState('RELIANCE');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [source, setSource] = useState(null);
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
      setHistoryRefresh((n) => n + 1);
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
                padding: '0.5rem 0.75rem', background: C.dark, color: C.text,
                border: `1px solid ${C.border}`, borderRadius: 10, outline: 'none',
                fontFamily: FONT_MONO, minWidth: 180, fontSize: 13,
              }}
            />
            <LiveBtn icon={<Zap size={15} />} label="Analyze" loading={loading} onClick={run} />
          </div>
        }
      >
        {backendDown && (
          <div style={{ color: C.red, fontSize: 12 }}>
            Backend unreachable — check that the server is running at <code>{API_BASE || window.location.origin}</code>.
          </div>
        )}
        {error && <div style={{ color: C.red, fontSize: 13 }}>{error}</div>}
        {!data && !loading && !error && (
          <div style={{ color: C.muted, fontSize: 13 }}>
            Enter any NSE symbol and hit <b>Analyze</b>. The engine pulls live Yahoo/NSE data, runs
            a 3-year backtest of the same setup, and reports per-gate pass/fail with a combined 0–100 score.
          </div>
        )}
        {data && (
          <>
            {source === 'history' && (
              <div
                style={{
                  marginBottom: 12, padding: '0.5rem 0.75rem',
                  background: 'rgba(77,159,255,0.08)', border: `1px solid ${C.blue}`,
                  borderRadius: 10, fontSize: 12, color: C.sub,
                  display: 'flex', alignItems: 'center', gap: 8,
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

      <HistoryPanel onSelect={viewHistory} activeId={activeHistoryId} refreshToken={historyRefresh} />
      <PaperTradingPanel refreshToken={historyRefresh} />
    </>
  );
}

// ---------------------------------------------------------------------------
// AlphaScanTab (main export)
// ---------------------------------------------------------------------------

export function AlphaScanTab({ state, log, runScan, backendDown }) {
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
              padding: '1rem', background: 'rgba(248,113,113,0.08)',
              border: `1px solid ${C.red}`, borderRadius: 10, marginBottom: 12,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: C.red, fontWeight: 700, marginBottom: 8 }}>
              <ShieldAlert size={16} /> Backend unreachable
            </div>
            <div style={{ fontSize: 12, color: C.sub }}>
              The API server at <code>{API_BASE || window.location.origin}</code> is not responding.
              {API_BASE ? ' It may be starting up — wait 30s and try again.' : ' Check your deployment.'}
            </div>
          </div>
        )}
      </Section>

      <Section title="Scan log" subtitle="Most recent first" icon={<Activity size={18} />}>
        <div style={{ maxHeight: 180, overflowY: 'auto', fontFamily: FONT_MONO, fontSize: 12 }}>
          {log.length === 0 ? (
            <div style={{ color: C.muted }}>No scans yet.</div>
          ) : (
            log.slice().reverse().map((entry, idx) => (
              <div
                key={idx}
                style={{ display: 'flex', gap: 10, padding: '4px 0', borderBottom: `1px dashed ${C.border}`, color: C.sub }}
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
        <Section icon={<ShieldAlert size={18} color={C.red} />} title="Scan failed">
          <div style={{ color: C.red, fontSize: 13 }}>{error}</div>
        </Section>
      )}

      {noTrade && (
        <Section icon={<ShieldAlert size={18} color={C.yellow} />} title="No trade opportunity today">
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
                background: 'linear-gradient(135deg, rgba(0,212,168,0.08) 0%, rgba(77,159,255,0.08) 100%)',
                border: `1px solid ${C.teal}`, borderRadius: 12, marginBottom: 12,
                display: 'flex', alignItems: 'center', gap: 10,
              }}
            >
              <Sparkles size={18} color={C.teal} />
              <div style={{ flex: 1, color: C.sub, fontSize: 13 }}>
                Winner selected from <b>NIFTY 500</b>. All 7 hard conditions passed. Expected
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
                    style={{ padding: '0.6rem 0.85rem', background: C.dark, border: `1px solid ${C.border}`, borderRadius: 10 }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ color: C.muted, fontSize: 11, letterSpacing: 0.6, textTransform: 'uppercase' }}>{row.label}</div>
                      <span
                        style={{
                          fontSize: 10, color: above ? C.green : C.red,
                          padding: '2px 6px', borderRadius: 999,
                          background: above ? 'rgba(74,222,128,0.1)' : 'rgba(248,113,113,0.1)',
                          display: 'inline-flex', alignItems: 'center', gap: 4,
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
