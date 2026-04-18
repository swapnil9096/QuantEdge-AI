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
