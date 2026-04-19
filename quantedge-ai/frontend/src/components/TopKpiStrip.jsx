import React from 'react';
import { Cpu, Eye, Gauge, Search, TrendingUp, Trophy } from 'lucide-react';
import { C } from '../constants.js';
import { KpiCard } from './shared.jsx';
import { mlScoreFromData } from '../utils/indicators.js';
import { fmt } from '../utils/format.js';

export function TopKpiStrip({ watchlist, scanData, backtest, ml }) {
  const scannedCount = Object.keys(scanData || {}).length;
  const highProbCount = Object.values(scanData || {}).filter(
    (sd) => mlScoreFromData(sd) >= 80,
  ).length;
  const bestModel = ml?.models?.find((m) => m.best)?.name || '—';

  return (
    <div style={{
      display: 'flex', flexWrap: 'wrap', gap: 10,
      padding: '1rem 1.5rem 0.25rem',
    }}>
      <KpiCard
        icon={<Eye size={13} />}
        label="Watchlist"
        value={watchlist.length}
        sub="symbols tracked"
        color={C.teal}
      />
      <KpiCard
        icon={<Search size={13} />}
        label="Scanned"
        value={scannedCount}
        sub="last scan"
        color={C.blue}
      />
      <KpiCard
        icon={<Trophy size={13} />}
        label="High Prob ≥80"
        value={highProbCount}
        sub="trade setups"
        color={C.green}
      />
      <KpiCard
        icon={<TrendingUp size={13} />}
        label="Win Rate"
        value={backtest ? `${fmt(backtest.win_rate, 1)}%` : '—'}
        sub={backtest ? 'backtest result' : 'run backtest'}
        color={C.yellow}
      />
      <KpiCard
        icon={<Gauge size={13} />}
        label="Sharpe Ratio"
        value={backtest ? fmt(backtest.sharpe_ratio) : '—'}
        sub={backtest ? `Sortino ${fmt(backtest.sortino_ratio)}` : '—'}
        color={C.purple}
      />
      <KpiCard
        icon={<Cpu size={13} />}
        label="Best Model"
        value={bestModel}
        sub={ml ? `${ml.models?.length || 0} models trained` : 'run ML training'}
        color={C.orange}
      />
    </div>
  );
}
