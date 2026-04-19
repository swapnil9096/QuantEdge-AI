import React from 'react';
import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import { C, FONT_MONO, GRAD } from '../constants.js';

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
        border: `2px solid rgba(255,255,255,0.08)`,
        borderTopColor: color,
        borderRadius: '50%',
        animation: 'qe-spin 0.7s linear infinite',
        verticalAlign: 'middle',
        flexShrink: 0,
      }}
    />
  );
}

// ---------------------------------------------------------------------------
// Section  (glass card)
// ---------------------------------------------------------------------------

export function Section({ title, subtitle, icon, children, right }) {
  return (
    <section
      style={{
        background: C.card,
        backdropFilter: 'blur(16px)',
        WebkitBackdropFilter: 'blur(16px)',
        border: `1px solid ${C.border}`,
        borderRadius: 16,
        padding: '1.25rem 1.4rem 1.2rem',
        marginBottom: 16,
        boxShadow: C.shadow,
        animation: 'qe-fade-up 0.28s cubic-bezier(0.4,0,0.2,1) both',
      }}
    >
      {(title || right) && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
          {icon && (
            <div
              style={{
                width: 34,
                height: 34,
                borderRadius: 10,
                background: 'rgba(0,212,168,0.1)',
                border: '1px solid rgba(0,212,168,0.18)',
                display: 'grid',
                placeItems: 'center',
                color: C.teal,
                flexShrink: 0,
              }}
            >
              {icon}
            </div>
          )}
          <div style={{ flex: 1, minWidth: 0 }}>
            {title && (
              <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: 0.1 }}>
                {title}
              </h3>
            )}
            {subtitle && (
              <p style={{ margin: '2px 0 0', fontSize: 11.5, color: C.muted, lineHeight: 1.5 }}>
                {subtitle}
              </p>
            )}
          </div>
          {right && <div style={{ flexShrink: 0 }}>{right}</div>}
        </div>
      )}
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
        flex: '1 1 148px',
        background: C.card,
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        border: `1px solid ${C.border}`,
        borderRadius: 14,
        padding: '0.9rem 1rem',
        minWidth: 130,
        boxShadow: C.shadow,
        position: 'relative',
        overflow: 'hidden',
        transition: 'transform 0.18s ease, box-shadow 0.18s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-2px)';
        e.currentTarget.style.boxShadow = `${C.shadow}, 0 0 20px ${color}20`;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = C.shadow;
      }}
    >
      {/* Accent bar */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, right: 0,
          height: 2,
          background: `linear-gradient(90deg, ${color}99 0%, transparent 100%)`,
          borderRadius: '14px 14px 0 0',
        }}
      />
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6,
        color: C.muted, fontSize: 10.5, letterSpacing: 0.8,
        textTransform: 'uppercase', fontWeight: 600,
      }}>
        <span style={{ color, opacity: 0.85 }}>{icon}</span>
        {label}
      </div>
      <div style={{
        marginTop: 8, fontSize: 22, fontWeight: 800, color: C.text,
        fontFamily: FONT_MONO, letterSpacing: -0.5, lineHeight: 1,
      }}>
        {value}
      </div>
      {sub && (
        <div style={{ marginTop: 4, fontSize: 11, color: C.muted }}>{sub}</div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// ProbBadge
// ---------------------------------------------------------------------------

export function ProbBadge({ score }) {
  const n = Number(score) || 0;
  let bg = 'rgba(248,113,113,0.12)';
  let fg = C.red;
  let border = 'rgba(248,113,113,0.25)';
  if (n >= 85) {
    bg = 'rgba(0,212,168,0.12)'; fg = C.teal; border = 'rgba(0,212,168,0.25)';
  } else if (n >= 70) {
    bg = 'rgba(77,159,255,0.12)'; fg = C.blue; border = 'rgba(77,159,255,0.25)';
  } else if (n >= 50) {
    bg = 'rgba(251,191,36,0.12)'; fg = C.yellow; border = 'rgba(251,191,36,0.25)';
  }
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        padding: '2px 9px', borderRadius: 999,
        background: bg, color: fg, border: `1px solid ${border}`,
        fontSize: 11, fontWeight: 700, fontFamily: FONT_MONO,
        letterSpacing: 0.3,
      }}
    >
      {n}%
    </span>
  );
}

// ---------------------------------------------------------------------------
// LiveBtn
// ---------------------------------------------------------------------------

export function LiveBtn({ icon, label, loading, onClick, gradient = GRAD.teal }) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 7,
        padding: '0.5rem 1.15rem',
        background: loading ? 'rgba(255,255,255,0.05)' : gradient,
        color: loading ? C.muted : '#041018',
        border: loading ? `1px solid ${C.border}` : 'none',
        borderRadius: 10,
        fontWeight: 700, fontSize: 13,
        cursor: loading ? 'not-allowed' : 'pointer',
        boxShadow: loading ? 'none' : '0 0 20px rgba(0,212,168,0.3)',
        transition: 'all 0.18s ease',
        whiteSpace: 'nowrap',
        letterSpacing: 0.1,
      }}
      onMouseEnter={(e) => {
        if (!loading) e.currentTarget.style.boxShadow = '0 0 28px rgba(0,212,168,0.5)';
      }}
      onMouseLeave={(e) => {
        if (!loading) e.currentTarget.style.boxShadow = '0 0 20px rgba(0,212,168,0.3)';
      }}
      onMouseDown={(e) => !loading && (e.currentTarget.style.transform = 'scale(0.97)')}
      onMouseUp={(e) => !loading && (e.currentTarget.style.transform = 'scale(1)')}
    >
      {loading ? <Spinner color="#94a3b8" size={13} /> : icon}
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
        background: 'rgba(255,255,255,0.03)',
        border: `1px solid ${C.border}`,
        borderRadius: 10,
        transition: 'border-color 0.18s',
      }}
    >
      <div style={{ fontSize: 10, letterSpacing: 0.8, color: C.muted, textTransform: 'uppercase', fontWeight: 600 }}>
        {label}
      </div>
      <div style={{ marginTop: 3, fontSize: 13, fontWeight: 700, color, fontFamily: FONT_MONO }}>
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
  const stroke = 9;
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, Number(score) || 0));
  const offset = circ - (pct / 100) * circ;
  const color = pct >= 90 ? C.teal : pct >= 70 ? C.blue : C.yellow;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size/2} cy={size/2} r={r} stroke="rgba(255,255,255,0.06)" strokeWidth={stroke} fill="none" />
        <circle
          cx={size/2} cy={size/2} r={r}
          stroke={color} strokeWidth={stroke} fill="none"
          strokeLinecap="round"
          strokeDasharray={circ}
          style={{
            '--ring-circ': circ, '--ring-target': offset,
            strokeDashoffset: offset,
            animation: 'qe-ring-draw 1.2s cubic-bezier(0.4,0,0.2,1)',
            transform: 'rotate(-90deg)', transformOrigin: 'center',
            filter: `drop-shadow(0 0 8px ${color}77)`,
          }}
        />
      </svg>
      <div style={{
        position: 'absolute', inset: 0,
        display: 'grid', placeItems: 'center', textAlign: 'center',
      }}>
        <div>
          <div style={{ fontSize: 28, fontWeight: 800, color, fontFamily: FONT_MONO, lineHeight: 1 }}>{pct}</div>
          <div style={{ fontSize: 9.5, color: C.muted, letterSpacing: 0.8, textTransform: 'uppercase', marginTop: 3 }}>
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
  const label =
    market.status === 'OPEN'       ? 'NSE OPEN'
    : market.status === 'PRE_OPEN'  ? 'PRE-OPEN AUCTION'
    : market.status === 'POST_CLOSE'? 'POST-CLOSE'
    : market.status === 'PRE_MARKET'? 'PRE-MARKET'
    : market.status === 'HOLIDAY'   ? 'NSE HOLIDAY'
    : 'WEEKEND';
  const nextOpen = market.next_open_ist
    ? new Date(market.next_open_ist).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
    : '—';
  const nowIst = market.now_ist
    ? new Date(market.now_ist).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
    : '';
  return (
    <div
      style={{
        display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap',
        padding: dense ? '0.4rem 0.75rem' : '0.55rem 0.9rem',
        borderRadius: 10,
        background: `${tone}10`,
        border: `1px solid ${tone}40`,
        fontSize: 12,
        marginBottom: dense ? 8 : 12,
      }}
    >
      <span style={{
        width: 7, height: 7, borderRadius: '50%', background: tone, flexShrink: 0,
        animation: open ? 'qe-pulse 1.6s ease-in-out infinite' : 'none',
      }} />
      <b style={{ color: tone, letterSpacing: 0.3 }}>{label}</b>
      {isHoliday && market.holiday_name && (
        <span style={{ color: C.purple, fontWeight: 600 }}>· {market.holiday_name}</span>
      )}
      <span style={{ color: C.muted, fontFamily: FONT_MONO, fontSize: 11 }}>{nowIst}</span>
      {!open && (
        <span style={{ color: C.sub, fontSize: 11.5 }}>
          · Next open <b style={{ color: C.text }}>{nextOpen}</b>
          {typeof market.minutes_to_open === 'number' && market.minutes_to_open > 0 && (
            <span style={{ color: C.muted }}> ({Math.round(market.minutes_to_open / 60)}h away)</span>
          )}
        </span>
      )}
      {market.calendar_source && (
        <span style={{ color: C.muted, fontSize: 10, marginLeft: 'auto' }}>
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
    p === true  ? <CheckCircle2 size={13} color={color} /> :
    p === false ? <XCircle size={13} color={color} /> :
                  <AlertTriangle size={13} color={color} />;
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 8,
      padding: '6px 0',
      borderBottom: `1px solid ${C.border}`,
    }}>
      <span style={{ marginTop: 1, flexShrink: 0 }}>{icon}</span>
      <div style={{ flex: 1, fontSize: 12 }}>
        <div style={{ color: C.sub, fontWeight: 600, fontFamily: FONT_MONO }}>{check.name}</div>
        <div style={{ color: C.muted, marginTop: 1, lineHeight: 1.5 }}>{check.detail}</div>
      </div>
    </div>
  );
}

export function GateCard({ title, icon, gate }) {
  if (!gate) return null;
  const passed = gate.passed;
  const accent = passed ? C.green : C.red;
  return (
    <div style={{
      background: 'rgba(255,255,255,0.02)',
      border: `1px solid ${passed ? 'rgba(74,222,128,0.25)' : 'rgba(248,113,113,0.25)'}`,
      borderRadius: 12,
      padding: '0.9rem 1rem',
      position: 'relative',
      overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 2,
        background: `linear-gradient(90deg, ${accent}80 0%, transparent 80%)`,
      }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
        <span style={{ color: accent }}>{icon}</span>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: C.text }}>{title}</div>
          <div style={{ fontSize: 10.5, color: C.muted, fontFamily: FONT_MONO, marginTop: 1 }}>
            {gate.score}/{gate.max_score}
          </div>
        </div>
        <span style={{
          padding: '2px 9px', borderRadius: 999,
          background: passed ? 'rgba(74,222,128,0.12)' : 'rgba(248,113,113,0.12)',
          border: `1px solid ${passed ? 'rgba(74,222,128,0.3)' : 'rgba(248,113,113,0.3)'}`,
          color: accent, fontSize: 10.5, fontWeight: 700, letterSpacing: 0.5,
        }}>
          {passed ? 'PASS' : 'FAIL'}
        </span>
      </div>
      {(gate.checks || []).map((c) => <CheckRow key={c.name} check={c} />)}
    </div>
  );
}
