import React, { useMemo, useState } from 'react';
import { BarChart3, Brain, CheckCircle2, Eye, Plus, RefreshCw, X, XCircle } from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, LiveBtn, ProbBadge } from './shared.jsx';
import { computeSetup, mlScoreFromData } from '../utils/indicators.js';
import { fmt, fmtPct } from '../utils/format.js';

export function ScannerTab({
  watchlist, addSymbol, removeSymbol,
  scanStatus, scanData, runScan, scanning,
  openInsights, livePrices,
}) {
  const [draft, setDraft] = useState('');

  const rows = useMemo(() => watchlist.map((sym) => {
    const sd = scanData[sym];
    const lp = livePrices?.[sym];
    const merged = sd ? {
      ...sd,
      price:      lp?.price      ?? sd.price,
      change_pct: lp?.change_pct ?? sd.change_pct,
      volume:     lp?.volume     ?? sd.volume,
    } : null;
    const setup = merged ? computeSetup(merged) : null;
    const ml    = merged ? mlScoreFromData(merged) : null;
    return { sym, sd: merged, setup, ml, status: scanStatus[sym] || 'idle' };
  }), [watchlist, scanData, scanStatus, livePrices]);

  return (
    <div>
      {/* ── Watchlist ─────────────────────────────────────────────────── */}
      <Section
        title="Watchlist"
        subtitle="Add NSE symbols — use RELIANCE or RELIANCE.NSE format."
        icon={<Eye size={16} />}
        right={
          <LiveBtn
            icon={<RefreshCw size={14} />}
            label={`Scan ${watchlist.length} Symbols`}
            loading={scanning}
            onClick={runScan}
          />
        }
      >
        {/* Add symbol input */}
        <form
          onSubmit={(e) => { e.preventDefault(); if (!draft.trim()) return; addSymbol(draft); setDraft(''); }}
          style={{ display: 'flex', gap: 8, marginBottom: 14 }}
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="e.g. RELIANCE or TCS.NSE"
            style={{
              flex: 1, padding: '0.55rem 0.85rem',
              background: 'rgba(255,255,255,0.04)',
              color: C.text,
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 10, outline: 'none',
              fontFamily: FONT_MONO, fontSize: 13,
              transition: 'border-color 0.15s',
            }}
            onFocus={(e) => (e.currentTarget.style.borderColor = 'rgba(0,212,168,0.4)')}
            onBlur={(e)  => (e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)')}
          />
          <button
            type="submit"
            style={{
              padding: '0.55rem 1rem',
              background: 'rgba(0,212,168,0.1)',
              border: '1px solid rgba(0,212,168,0.25)',
              borderRadius: 10, color: C.teal,
              cursor: 'pointer', fontWeight: 600, fontSize: 13,
              display: 'inline-flex', alignItems: 'center', gap: 6,
              transition: 'all 0.15s',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(0,212,168,0.18)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(0,212,168,0.1)'; }}
          >
            <Plus size={14} /> Add
          </button>
        </form>

        {/* Symbol chips */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {watchlist.map((sym) => {
            const status = scanStatus[sym];
            const icon =
              status === 'loading' ? <Spinner size={10} /> :
              status === 'ok'      ? <CheckCircle2 size={11} color={C.green} /> :
              status === 'error'   ? <XCircle size={11} color={C.red} /> : null;
            const isLive = !!livePrices?.[sym];
            return (
              <span key={sym} style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                padding: '4px 10px 4px 8px',
                background: 'rgba(255,255,255,0.04)',
                border: `1px solid ${isLive ? 'rgba(0,212,168,0.3)' : 'rgba(255,255,255,0.09)'}`,
                borderRadius: 999, fontSize: 12,
                fontFamily: FONT_MONO, color: C.sub,
                transition: 'border-color 0.15s',
              }}>
                {icon}
                {isLive && (
                  <span style={{
                    width: 5, height: 5, borderRadius: '50%', background: C.teal,
                    animation: 'qe-pulse 1.4s ease-in-out infinite', flexShrink: 0,
                  }} />
                )}
                {sym}
                <button
                  onClick={() => removeSymbol(sym)}
                  style={{
                    background: 'none', border: 'none', color: C.muted, cursor: 'pointer',
                    padding: 0, display: 'grid', placeItems: 'center',
                    transition: 'color 0.12s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = C.red)}
                  onMouseLeave={(e) => (e.currentTarget.style.color = C.muted)}
                  aria-label={`Remove ${sym}`}
                >
                  <X size={11} />
                </button>
              </span>
            );
          })}
        </div>
      </Section>

      {/* ── Results table ─────────────────────────────────────────────── */}
      <Section
        title="Results"
        subtitle="Click any row to open AI Insights."
        icon={<BarChart3 size={16} />}
      >
        <div style={{ overflowX: 'auto', margin: '0 -0.1rem' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
            <thead>
              <tr>
                {['Symbol','Price','Δ %','Entry','Stop','Target','R:R','Pattern','RSI','ML',''].map((h) => (
                  <th key={h} style={{
                    padding: '0.5rem 0.65rem',
                    borderBottom: '1px solid rgba(255,255,255,0.07)',
                    color: C.muted, fontWeight: 600,
                    fontSize: 10.5, textTransform: 'uppercase', letterSpacing: 0.7,
                    textAlign: 'left', whiteSpace: 'nowrap',
                  }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(({ sym, sd, setup, ml, status }) => {
                const chg = Number(sd?.change_pct);
                const changeColor = chg >= 0 ? C.green : C.red;
                const hasLive = !!livePrices?.[sym];
                return (
                  <tr
                    key={sym}
                    onClick={() => sd && openInsights(sym)}
                    style={{
                      cursor: sd ? 'pointer' : 'default',
                      borderBottom: '1px solid rgba(255,255,255,0.05)',
                      transition: 'background 0.12s',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.025)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    {/* Symbol */}
                    <td style={{ padding: '0.65rem 0.65rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                        <span style={{
                          width: 22, height: 22, borderRadius: 6, flexShrink: 0,
                          background: 'rgba(255,255,255,0.05)',
                          border: '1px solid rgba(255,255,255,0.08)',
                          display: 'grid', placeItems: 'center',
                        }}>
                          {status === 'loading' ? <Spinner size={10} /> :
                           status === 'ok'      ? <CheckCircle2 size={11} color={C.green} /> :
                           status === 'error'   ? <XCircle size={11} color={C.red} /> :
                           <span style={{ fontSize: 8, color: C.muted }}>—</span>}
                        </span>
                        <span style={{ fontWeight: 700, fontFamily: FONT_MONO, color: C.text, fontSize: 13 }}>
                          {sym}
                        </span>
                        {hasLive && (
                          <span title="Live WS price" style={{
                            width: 5, height: 5, borderRadius: '50%',
                            background: C.teal, flexShrink: 0,
                            animation: 'qe-pulse 1.4s ease-in-out infinite',
                          }} />
                        )}
                      </div>
                    </td>

                    {/* Price */}
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO, fontWeight: 600, color: C.text }}>
                      {sd ? `₹${fmt(sd.price)}` : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* Change % */}
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO }}>
                      {sd ? (
                        <span style={{
                          padding: '2px 7px', borderRadius: 6,
                          background: chg >= 0 ? 'rgba(74,222,128,0.1)' : 'rgba(248,113,113,0.1)',
                          color: changeColor, fontWeight: 600, fontSize: 12,
                        }}>
                          {fmtPct(sd.change_pct)}
                        </span>
                      ) : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* Entry / Stop / Target */}
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO, color: C.sub }}>
                      {setup ? fmt(setup.entry) : <span style={{ color: C.muted }}>—</span>}
                    </td>
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO, color: C.red }}>
                      {setup ? fmt(setup.stop) : <span style={{ color: C.muted }}>—</span>}
                    </td>
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO, color: C.green }}>
                      {setup ? fmt(setup.target) : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* R:R */}
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO, color: C.sub }}>
                      {setup ? (
                        <span style={{ color: Number(setup.rr) >= 2 ? C.teal : C.sub }}>
                          {fmt(setup.rr)}
                        </span>
                      ) : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* Pattern */}
                    <td style={{ padding: '0.65rem 0.65rem' }}>
                      {setup?.pattern ? (
                        <span style={{
                          padding: '2px 7px', borderRadius: 6,
                          background: 'rgba(77,159,255,0.1)',
                          border: '1px solid rgba(77,159,255,0.15)',
                          color: C.blue, fontSize: 11, fontWeight: 600,
                        }}>
                          {setup.pattern}
                        </span>
                      ) : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* RSI */}
                    <td style={{ padding: '0.65rem 0.65rem', fontFamily: FONT_MONO }}>
                      {sd ? (
                        <span style={{
                          color: sd.rsi_estimate > 70 ? C.red : sd.rsi_estimate < 30 ? C.green : C.sub,
                          fontWeight: 600,
                        }}>
                          {fmt(sd.rsi_estimate, 1)}
                        </span>
                      ) : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* ML Score */}
                    <td style={{ padding: '0.65rem 0.65rem' }}>
                      {ml !== null ? <ProbBadge score={ml} /> : <span style={{ color: C.muted }}>—</span>}
                    </td>

                    {/* Action */}
                    <td style={{ padding: '0.65rem 0.65rem', textAlign: 'right' }}>
                      {sd && (
                        <button
                          onClick={(e) => { e.stopPropagation(); openInsights(sym); }}
                          style={{
                            padding: '4px 10px',
                            background: 'rgba(0,212,168,0.08)',
                            border: '1px solid rgba(0,212,168,0.2)',
                            borderRadius: 8, color: C.teal,
                            cursor: 'pointer',
                            display: 'inline-flex', alignItems: 'center', gap: 4,
                            fontSize: 11.5, fontWeight: 600,
                            transition: 'all 0.15s',
                            whiteSpace: 'nowrap',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.background = 'rgba(0,212,168,0.16)';
                            e.currentTarget.style.boxShadow = '0 0 12px rgba(0,212,168,0.2)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.background = 'rgba(0,212,168,0.08)';
                            e.currentTarget.style.boxShadow = 'none';
                          }}
                        >
                          <Brain size={11} /> AI View
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
              {!rows.length && (
                <tr>
                  <td colSpan={11} style={{
                    padding: '2.5rem', textAlign: 'center', color: C.muted, fontSize: 13,
                  }}>
                    Add symbols above and hit <strong style={{ color: C.sub }}>Scan</strong>.
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
