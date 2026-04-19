import React from 'react';
import { LogOut, Sparkles, WifiOff } from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';

export function Header({ lastScan, lastError, onLogout, wsConnected, currentUser }) {
  const live = !!lastScan;
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '1rem 1.5rem',
        borderBottom: `1px solid ${C.border}`,
        background: 'linear-gradient(180deg, #0b1224 0%, #0a0e1a 100%)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div
          style={{
            width: 40,
            height: 40,
            borderRadius: 12,
            background: `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
            display: 'grid',
            placeItems: 'center',
            color: '#041018',
            boxShadow: '0 6px 24px rgba(0,212,168,0.35)',
          }}
        >
          <Sparkles size={22} strokeWidth={2.4} />
        </div>
        <div>
          <h1 style={{ margin: 0, fontSize: 17, fontWeight: 800, letterSpacing: 0.3 }}>
            QuantEdge <span style={{ color: C.teal }}>AI</span>
          </h1>
          <div style={{ fontSize: 11, color: C.muted }}>
            Institutional AlphaScan · Live market research · GPT-4o &amp; Claude
          </div>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {/* Live feed status indicator */}
        {wsConnected !== undefined && (
          <div
            title={wsConnected ? 'WebSocket live feed connected' : 'Live feed disconnected — reconnecting…'}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              padding: '4px 10px',
              borderRadius: 999,
              background: wsConnected ? 'rgba(74,222,128,0.1)' : 'rgba(248,113,113,0.08)',
              border: `1px solid ${wsConnected ? C.green : C.red}`,
              color: wsConnected ? C.green : C.red,
              fontSize: 11,
              fontWeight: 600,
            }}
          >
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: wsConnected ? C.green : C.red,
                animation: wsConnected ? 'qe-pulse 1.4s ease-in-out infinite' : 'none',
              }}
            />
            {wsConnected ? 'WS' : 'WS off'}
          </div>
        )}

        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 12px',
            borderRadius: 999,
            background: live ? 'rgba(74,222,128,0.12)' : C.dark,
            border: `1px solid ${live ? C.green : C.border}`,
            color: live ? C.green : C.muted,
            fontSize: 12,
            fontWeight: 600,
          }}
        >
          {live ? (
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: C.green,
                animation: 'qe-pulse 1.4s ease-in-out infinite',
              }}
            />
          ) : (
            <WifiOff size={12} />
          )}
          {live ? 'Live' : 'Not scanned'}
        </div>
        <div style={{ fontSize: 11, color: C.muted, fontFamily: FONT_MONO }}>
          {lastScan ? `Last scan: ${new Date(lastScan).toLocaleTimeString()}` : lastError ? 'Error' : '—'}
        </div>
        {currentUser && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: C.muted, fontFamily: FONT_MONO }}>
              {currentUser.username}
              {currentUser.is_admin && (
                <span style={{ marginLeft: 4, color: C.teal, fontSize: 10, fontWeight: 700 }}>ADMIN</span>
              )}
            </span>
            <button
              onClick={onLogout}
              title="Sign out"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 12px',
                borderRadius: 999,
                background: C.dark,
                border: `1px solid ${C.border}`,
                color: C.muted,
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = C.red;
                e.currentTarget.style.borderColor = C.red;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = C.muted;
                e.currentTarget.style.borderColor = C.border;
              }}
            >
              <LogOut size={12} /> Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
