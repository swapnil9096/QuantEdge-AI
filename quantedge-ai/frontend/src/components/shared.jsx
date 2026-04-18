import React from 'react';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  XCircle,
} from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';

// ---------------------------------------------------------------------------
// Spinner
// ---------------------------------------------------------------------------

export function Spinner({ size = 16, color = C.teal }) {
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

// ---------------------------------------------------------------------------
// Section
// ---------------------------------------------------------------------------

export function Section({ title, subtitle, icon, children, right }) {
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

// ---------------------------------------------------------------------------
// KpiCard
// ---------------------------------------------------------------------------

export function KpiCard({ icon, label, value, sub, color = C.teal }) {
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

// ---------------------------------------------------------------------------
// ProbBadge
// ---------------------------------------------------------------------------

export function ProbBadge({ score }) {
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

// ---------------------------------------------------------------------------
// LiveBtn
// ---------------------------------------------------------------------------

export function LiveBtn({ icon, label, loading, onClick, gradient = `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)` }) {
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

// ---------------------------------------------------------------------------
// Tag
// ---------------------------------------------------------------------------

export function Tag({ label, value, color = C.teal }) {
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
// ScoreRing
// ---------------------------------------------------------------------------

export function ScoreRing({ score }) {
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
// MarketStatusBanner
// ---------------------------------------------------------------------------

export function MarketStatusBanner({ market, dense = false }) {
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

// ---------------------------------------------------------------------------
// CheckRow + GateCard
// ---------------------------------------------------------------------------

export function CheckRow({ check }) {
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

export function GateCard({ title, icon, gate }) {
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
