import React, { useEffect, useMemo, useState } from 'react';
import { BarChart3, Brain, CheckCircle2, Eye, Plus, RefreshCw, X, XCircle } from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, LiveBtn, ProbBadge } from './shared.jsx';
import { computeSetup, mlScoreFromData } from '../utils/indicators.js';
import { fmt, fmtPct } from '../utils/format.js';

export function ScannerTab({
  watchlist,
  addSymbol,
  removeSymbol,
  scanStatus,
  scanData,
  runScan,
  scanning,
  openInsights,
  // WebSocket live prices (Phase 3)
  livePrices,
}) {
  const [draft, setDraft] = useState('');

  const rows = useMemo(() => {
    return watchlist.map((sym) => {
      // Merge live WS price over stock-data price if available
      const sd = scanData[sym];
      const lp = livePrices?.[sym];
      const merged = sd
        ? {
            ...sd,
            price: lp?.price ?? sd.price,
            change_pct: lp?.change_pct ?? sd.change_pct,
            volume: lp?.volume ?? sd.volume,
          }
        : null;
      const setup = merged ? computeSetup(merged) : null;
      const ml = merged ? mlScoreFromData(merged) : null;
      return { sym, sd: merged, setup, ml, status: scanStatus[sym] || 'idle' };
    });
  }, [watchlist, scanData, scanStatus, livePrices]);

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
                {['Symbol', 'Price', 'Δ %', 'Entry', 'Stop', 'Target', 'R:R', 'Pattern', 'RSI', 'ML Score', ''].map((h) => (
                  <th key={h} style={{ padding: '0.55rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(({ sym, sd, setup, ml, status }) => {
                const changeColor = Number(sd?.change_pct) >= 0 ? C.green : C.red;
                const hasLive = !!livePrices?.[sym];
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
                        {hasLive && (
                          <span
                            title="Live price via WebSocket"
                            style={{
                              width: 6, height: 6, borderRadius: '50%',
                              background: C.teal,
                              animation: 'qe-pulse 1.4s ease-in-out infinite',
                            }}
                          />
                        )}
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
