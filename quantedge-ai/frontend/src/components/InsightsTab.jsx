import React, { useEffect } from 'react';
import {
  Brain, CircleDollarSign, LayoutDashboard, RefreshCw, Search, Target,
} from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, ProbBadge, Tag } from './shared.jsx';
import { computeSetup, mlScoreFromData } from '../utils/indicators.js';
import { fmt, fmtPct, formatCrore } from '../utils/format.js';

export function InsightsTab({ watchlist, scanData, activeSymbol, setActiveSymbol, aiText, aiLoading, runExplain }) {
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
