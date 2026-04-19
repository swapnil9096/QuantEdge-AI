import React, { useEffect } from 'react';
import {
  X, TrendingUp, BarChart2, Cpu, BookOpen,
  Rocket, Bell, Zap, Building2,
} from 'lucide-react';
import { C, GRAD } from '../constants.js';

const FEATURES = [
  {
    icon: <TrendingUp size={20} />,
    title: 'Real-time Scanner',
    desc: 'Scan NSE stocks live via WebSocket — RSI estimates, trend signals, and momentum scoring updated every 5 seconds.',
    accent: C.teal,
  },
  {
    icon: <Zap size={20} />,
    title: 'AI Deep Analysis',
    desc: 'Claude-powered institutional trade theses covering pattern structure, volume, momentum, and risk/reward defensibility.',
    accent: C.blue,
  },
  {
    icon: <BookOpen size={20} />,
    title: 'Paper Trading',
    desc: 'Practice with ₹10,00,000 virtual capital. Track open positions, P&L curves, drawdown, and portfolio performance.',
    accent: C.green,
  },
  {
    icon: <Cpu size={20} />,
    title: 'ML Signal Models',
    desc: 'XGBoost & LightGBM models trained on real OHLCV data using time-series cross-validation for trade signal generation.',
    accent: C.purple,
  },
  {
    icon: <Rocket size={20} />,
    title: 'AlphaScan',
    desc: 'Automated best-stock finder scanning the NIFTY 500 universe to surface the single highest-probability setup.',
    accent: C.orange,
  },
  {
    icon: <BarChart2 size={20} />,
    title: 'Backtesting Engine',
    desc: 'Claude-powered strategy backtesting with win rate, profit factor, Sharpe/Sortino ratios, and monthly returns breakdown.',
    accent: C.yellow,
  },
  {
    icon: <Bell size={20} />,
    title: 'Telegram Alerts',
    desc: 'Real-time trade alerts, stop-loss hits, and portfolio updates sent directly to your Telegram channel.',
    accent: C.teal,
  },
  {
    icon: <Building2 size={20} />,
    title: 'Broker Integration',
    desc: 'Connect Angel One SmartAPI to sync paper trades into real orders with a single click — TOTP auth included.',
    accent: C.blue,
  },
];

export function FeatureModal({ onClose }) {
  // Close on Escape key
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        background: 'rgba(5, 8, 16, 0.88)',
        backdropFilter: 'blur(10px)',
        WebkitBackdropFilter: 'blur(10px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '1rem',
        overflowY: 'auto',
      }}
    >
      {/* Modal card — stop click-through */}
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%', maxWidth: 760,
          background: 'rgba(13, 18, 32, 0.97)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 20,
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.06)',
          animation: 'qe-fade-up 0.28s cubic-bezier(0.4,0,0.2,1) both',
          overflow: 'hidden',
          margin: 'auto',
        }}
      >
        {/* Header */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '1.4rem 1.6rem 1.2rem',
          borderBottom: '1px solid rgba(255,255,255,0.07)',
          background: 'rgba(0,212,168,0.04)',
        }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, letterSpacing: -0.4, color: C.text }}>
              Platform Features
            </h2>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: C.muted }}>
              Everything included in your QuantEdge AI workspace
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              width: 34, height: 34, borderRadius: 10,
              background: 'rgba(255,255,255,0.05)',
              border: '1px solid rgba(255,255,255,0.08)',
              color: C.muted, cursor: 'pointer',
              display: 'grid', placeItems: 'center',
              transition: 'all 0.15s ease',
              flexShrink: 0,
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.1)'; e.currentTarget.style.color = C.text; }}
            onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.05)'; e.currentTarget.style.color = C.muted; }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Feature grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
          gap: '1px',
          background: 'rgba(255,255,255,0.06)',
        }}>
          {FEATURES.map(({ icon, title, desc, accent }) => (
            <div
              key={title}
              style={{
                background: 'rgba(13, 18, 32, 0.97)',
                padding: '1.1rem 1.3rem',
                transition: 'background 0.15s ease',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(20, 28, 50, 0.99)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(13, 18, 32, 0.97)'; }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <div style={{
                  width: 36, height: 36, borderRadius: 10, flexShrink: 0,
                  background: `${accent}18`,
                  border: `1px solid ${accent}30`,
                  display: 'grid', placeItems: 'center',
                  color: accent,
                }}>
                  {icon}
                </div>
                <div>
                  <div style={{ fontSize: 13.5, fontWeight: 700, color: C.text, marginBottom: 4 }}>
                    {title}
                  </div>
                  <div style={{ fontSize: 12.5, color: C.muted, lineHeight: 1.55 }}>
                    {desc}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Footer */}
        <div style={{
          padding: '1rem 1.6rem',
          borderTop: '1px solid rgba(255,255,255,0.07)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <span style={{ fontSize: 12, color: C.muted }}>
            All features available after creating a free account
          </span>
          <button
            onClick={onClose}
            style={{
              padding: '0.45rem 1.1rem',
              background: GRAD.teal,
              border: 'none', borderRadius: 9,
              color: '#041018', fontWeight: 700, fontSize: 13,
              cursor: 'pointer',
              boxShadow: '0 0 16px rgba(0,212,168,0.3)',
              transition: 'box-shadow 0.18s ease',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.boxShadow = '0 0 24px rgba(0,212,168,0.5)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.boxShadow = '0 0 16px rgba(0,212,168,0.3)'; }}
          >
            Get Started
          </button>
        </div>
      </div>
    </div>
  );
}
