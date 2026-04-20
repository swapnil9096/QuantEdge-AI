import React, { useState, useCallback, useEffect } from 'react';
import {
  Activity, BarChart3, DollarSign, TrendingDown, TrendingUp,
  Target, AlertTriangle, RefreshCw, Clock, Zap,
} from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Section, Spinner, KpiCard } from './shared.jsx';
import { fmtMoney, fmtPct } from '../utils/format.js';
import { fetchDashboardSummary } from '../utils/api.js';

export function DashboardTab() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fetchDashboardSummary());
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const id = setInterval(load, 30_000);
    return () => clearInterval(id);
  }, [load]);

  const d = data || {};
  const equityChange = (d.totalEquity || 0) - (d.startingCapital || 0);
  const equityChangePct = d.startingCapital ? (equityChange / d.startingCapital * 100) : 0;

  return (
    <Section
      title="Dashboard"
      subtitle="Portfolio overview — auto-refreshes every 30s"
      icon={<BarChart3 size={18} />}
      right={
        <button
          onClick={load}
          disabled={loading}
          style={{
            background: 'transparent', border: `1px solid ${C.border}`, borderRadius: 8,
            color: C.sub, cursor: loading ? 'not-allowed' : 'pointer', padding: '4px 10px',
            fontSize: 12, display: 'flex', alignItems: 'center', gap: 4,
          }}
        >
          {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
        </button>
      }
    >
      {error && (
        <div style={{ color: C.red, fontSize: 12, marginBottom: 12, padding: '8px 12px', background: 'rgba(248,113,113,0.08)', borderRadius: 8 }}>
          {error}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12, marginBottom: 20 }}>
        <KpiCard
          icon={<DollarSign size={14} />}
          label="Total Equity"
          value={`₹${Number(d.totalEquity || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`}
          sub={`${equityChange >= 0 ? '+' : ''}${fmtMoney(equityChange)} (${fmtPct(equityChangePct)})`}
          color={equityChange >= 0 ? C.green : C.red}
        />
        <KpiCard
          icon={<TrendingUp size={14} />}
          label="Today's P&L"
          value={fmtMoney(d.todaysPnL)}
          sub={d.todaysPnLPct != null ? fmtPct(d.todaysPnLPct) : '—'}
          color={(d.todaysPnL || 0) >= 0 ? C.green : C.red}
        />
        <KpiCard
          icon={<Target size={14} />}
          label="Win Rate"
          value={d.winRate != null ? `${d.winRate}%` : '—'}
          sub={`${d.closedTradesCount || 0} trades`}
          color={C.teal}
        />
        <KpiCard
          icon={<TrendingDown size={14} />}
          label="Max Drawdown"
          value={d.maxDrawdownPct != null ? `${d.maxDrawdownPct}%` : '—'}
          color={C.red}
        />
        <KpiCard
          icon={<Activity size={14} />}
          label="Open Positions"
          value={String(d.openPositionsCount || 0)}
          sub={`${d.pendingOrdersCount || 0} pending`}
          color={C.blue}
        />
        <KpiCard
          icon={<Zap size={14} />}
          label="Avg Return"
          value={d.avgReturnPct != null ? fmtPct(d.avgReturnPct) : '—'}
          color={(d.avgReturnPct || 0) >= 0 ? C.green : C.red}
        />
        <KpiCard
          icon={<TrendingUp size={14} />}
          label="Best Trade"
          value={fmtMoney(d.bestTrade)}
          sub={d.bestTradePct != null ? fmtPct(d.bestTradePct) : ''}
          color={C.green}
        />
        <KpiCard
          icon={<AlertTriangle size={14} />}
          label="Worst Trade"
          value={fmtMoney(d.worstTrade)}
          sub={d.worstTradePct != null ? fmtPct(d.worstTradePct) : ''}
          color={C.red}
        />
      </div>

      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
        gap: 10, padding: '14px 16px', background: C.glass, borderRadius: 12,
        border: `1px solid ${C.glassBorder}`, fontSize: 13,
      }}>
        {[
          { label: 'Starting Capital', value: `₹${Number(d.startingCapital || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}` },
          { label: 'Realised P&L', value: fmtMoney(d.realisedPnL), color: (d.realisedPnL || 0) >= 0 ? C.green : C.red },
          { label: 'Unrealised P&L', value: fmtMoney(d.unrealisedPnL), color: (d.unrealisedPnL || 0) >= 0 ? C.green : C.red },
          { label: 'SL Mode', value: (d.settings?.sl_mode || 'fixed').toUpperCase(), color: C.yellow },
          { label: 'Risk/Trade', value: `${d.settings?.risk_per_trade_pct || 1}%` },
          { label: 'ATR Multiplier', value: `${d.settings?.atr_multiplier || 1.5}×` },
        ].map((item) => (
          <div key={item.label} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ color: C.muted, fontSize: 10.5, textTransform: 'uppercase', letterSpacing: 0.5 }}>{item.label}</span>
            <span style={{ color: item.color || C.text, fontFamily: FONT_MONO, fontWeight: 600 }}>{item.value}</span>
          </div>
        ))}
      </div>
    </Section>
  );
}
