// ---------------------------------------------------------------------------
// Palette + design tokens
// ---------------------------------------------------------------------------

export const C = {
  bg:     '#050810',
  card:   'rgba(15, 20, 35, 0.85)',
  dark:   '#080c18',
  border: 'rgba(255, 255, 255, 0.07)',
  border2:'rgba(255, 255, 255, 0.12)',
  muted:  '#475569',
  sub:    '#94a3b8',
  text:   '#f1f5f9',
  teal:   '#00d4a8',
  blue:   '#4d9fff',
  purple: '#a78bfa',
  yellow: '#fbbf24',
  green:  '#4ade80',
  red:    '#f87171',
  orange: '#f0883e',
  // Glass helpers
  glass:        'rgba(255,255,255,0.03)',
  glassBorder:  'rgba(255,255,255,0.07)',
  shadow:       '0 1px 3px rgba(0,0,0,0.3), 0 8px 32px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.04)',
};

export const GRAD = {
  teal:   'linear-gradient(135deg, #00d4a8 0%, #4d9fff 100%)',
  purple: 'linear-gradient(135deg, #a78bfa 0%, #4d9fff 100%)',
  red:    'linear-gradient(135deg, #f87171 0%, #f0883e 100%)',
  green:  'linear-gradient(135deg, #4ade80 0%, #00d4a8 100%)',
};

export const FONT_MONO = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace";

// API base URL resolution:
//   1. VITE_API_BASE env var → always wins (set this on Vercel to your Render URL)
//   2. Local dev on :3000   → http://localhost:8000
//   3. Single-domain deploy (Fly / Koyeb — backend serves frontend) → '' (same origin)
//
// For Render + Vercel: set VITE_API_BASE=https://<your-service>.onrender.com
// in Vercel's project settings → Environment Variables.
export const API_BASE = (() => {
  const envOverride = import.meta.env.VITE_API_BASE;
  if (envOverride) return envOverride.replace(/\/$/, '');
  if (typeof window !== 'undefined') {
    const { hostname, port } = window.location;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return port === '3000' ? 'http://localhost:8000' : '';
    }
    // Non-localhost without VITE_API_BASE = single-domain deploy (same origin)
    return '';
  }
  return '';
})();

export const TOKEN_KEY = 'quantedge_session_token';
