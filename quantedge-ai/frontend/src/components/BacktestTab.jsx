import React, { useMemo } from 'react';
import {
  Activity, BarChart3, Brain, CircleDollarSign, Gauge,
  LineChart as LineIcon, Rocket, TrendingDown, TrendingUp, Trophy,
} from 'lucide-react';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell,
  Legend, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { C } from '../constants.js';
import { Spinner, Section, KpiCard, LiveBtn } from './shared.jsx';
import { fmt } from '../utils/format.js';

export function BacktestTab({ backtest, loading, runBacktest }) {
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
            <KpiCard icon={<Trophy size={14} />} label="Win rate" value={`${fmt(backtest.win_rate, 1)}%`} color={C.teal} />
            <KpiCard icon={<TrendingUp size={14} />} label="Profit factor" value={fmt(backtest.profit_factor)} color={C.green} />
            <KpiCard icon={<Activity size={14} />} label="Avg return" value={`${fmt(backtest.avg_return)}%`} color={C.blue} />
            <KpiCard icon={<TrendingDown size={14} />} label="Max drawdown" value={`${fmt(backtest.max_drawdown)}%`} color={C.red} />
            <KpiCard icon={<Gauge size={14} />} label="Sharpe" value={fmt(backtest.sharpe_ratio)} color={C.yellow} />
            <KpiCard icon={<Gauge size={14} />} label="Sortino" value={fmt(backtest.sortino_ratio)} color={C.orange} />
            <KpiCard icon={<Gauge size={14} />} label="Calmar" value={fmt(backtest.calmar_ratio)} color={C.purple} />
            <KpiCard icon={<CircleDollarSign size={14} />} label="Total trades" value={fmt(backtest.total_trades, 0)} color={C.sub} />
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
                    contentStyle={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 8, fontSize: 12 }}
                    formatter={(v) => `${Number(v).toFixed(2)}%`}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Area type="monotone" dataKey="strategy" stroke={C.teal} fill="url(#qe-strat)" strokeWidth={2} name="QuantEdge" />
                  <Area type="monotone" dataKey="benchmark" stroke={C.blue} fill="url(#qe-bench)" strokeWidth={2} name="NIFTY 50" />
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
