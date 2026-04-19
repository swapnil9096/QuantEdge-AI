import React from 'react';
import { LogOut, Sparkles, WifiOff } from 'lucide-react';
import { C, FONT_MONO, GRAD } from '../constants.js';

export function Header({ lastScan, lastError, onLogout, wsConnected, currentUser }) {
  const live = !!lastScan;

  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0.75rem 1.5rem',
        background: 'rgba(5,8,16,0.88)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        borderBottom: '1px solid rgba(255,255,255,0.07)',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        gap: 12,
      }}
    >
      {/* ── Brand ─────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
        <div
          style={{
            width: 36, height: 36, borderRadius: 10,
            background: GRAD.teal,
            display: 'grid', placeItems: 'center',
            color: '#041018',
            boxShadow: '0 0 20px rgba(0,212,168,0.4)',
            flexShrink: 0,
          }}
        >
          <Sparkles size={18} strokeWidth={2.5} />
        </div>
        <div>
          <div style={{ fontSize: 15, fontWeight: 800, letterSpacing: 0.1, lineHeight: 1.2 }}>
            QuantEdge <span style={{ color: C.teal }}>AI</span>
          </div>
          <div style={{ fontSize: 10, color: C.muted, letterSpacing: 0.3, lineHeight: 1 }}>
            Institutional AlphaScan
          </div>
        </div>
      </div>

      {/* ── Right controls ────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>

        {/* WS status */}
        {wsConnected !== undefined && (
          <div
            title={wsConnected ? 'Live feed connected' : 'Reconnecting…'}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 5,
              padding: '4px 10px', borderRadius: 999,
              background: wsConnected ? 'rgba(74,222,128,0.08)' : 'rgba(248,113,113,0.08)',
              border: `1px solid ${wsConnected ? 'rgba(74,222,128,0.25)' : 'rgba(248,113,113,0.25)'}`,
              color: wsConnected ? C.green : C.red,
              fontSize: 11, fontWeight: 600,
            }}
          >
            <span style={{
              width: 5, height: 5, borderRadius: '50%',
              background: wsConnected ? C.green : C.red,
              animation: wsConnected ? 'qe-pulse 1.4s ease-in-out infinite' : 'none',
            }} />
            {wsConnected ? 'Live' : 'WS off'}
          </div>
        )}

        {/* Scan status */}
        <div
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '4px 10px', borderRadius: 999,
            background: live ? 'rgba(74,222,128,0.08)' : 'rgba(255,255,255,0.04)',
            border: `1px solid ${live ? 'rgba(74,222,128,0.2)' : 'rgba(255,255,255,0.07)'}`,
            color: live ? C.green : C.muted,
            fontSize: 11, fontWeight: 600,
          }}
        >
          {live ? (
            <span style={{
              width: 5, height: 5, borderRadius: '50%', background: C.green,
              animation: 'qe-pulse 1.4s ease-in-out infinite',
            }} />
          ) : (
            <WifiOff size={11} />
          )}
          {live
            ? new Date(lastScan).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            : lastError ? 'Error' : 'Not scanned'
          }
        </div>

        {/* User + logout */}
        {currentUser && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 7,
              padding: '4px 10px', borderRadius: 999,
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              fontSize: 11, color: C.sub, fontFamily: FONT_MONO,
            }}>
              <span style={{
                width: 20, height: 20, borderRadius: '50%',
                background: GRAD.teal,
                display: 'grid', placeItems: 'center',
                color: '#041018', fontSize: 9, fontWeight: 800,
              }}>
                {currentUser.username[0].toUpperCase()}
              </span>
              {currentUser.username}
              {currentUser.is_admin && (
                <span style={{
                  padding: '1px 5px', borderRadius: 4,
                  background: 'rgba(0,212,168,0.15)',
                  border: '1px solid rgba(0,212,168,0.25)',
                  color: C.teal, fontSize: 9, fontWeight: 700, letterSpacing: 0.5,
                }}>
                  ADMIN
                </span>
              )}
            </div>
            <button
              onClick={onLogout}
              title="Sign out"
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                padding: '5px 11px', borderRadius: 999,
                background: 'transparent',
                border: '1px solid rgba(255,255,255,0.07)',
                color: C.muted, fontSize: 11, fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.18s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = C.red;
                e.currentTarget.style.borderColor = 'rgba(248,113,113,0.35)';
                e.currentTarget.style.background = 'rgba(248,113,113,0.08)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = C.muted;
                e.currentTarget.style.borderColor = 'rgba(255,255,255,0.07)';
                e.currentTarget.style.background = 'transparent';
              }}
            >
              <LogOut size={11} /> Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
