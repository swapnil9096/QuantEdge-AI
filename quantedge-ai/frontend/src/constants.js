// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

export const C = {
  bg: '#0a0e1a',
  card: '#111827',
  border: '#1f2937',
  muted: '#6b7280',
  text: '#e2e8f0',
  sub: '#cbd5e1',
  teal: '#00d4a8',
  blue: '#4d9fff',
  purple: '#a78bfa',
  yellow: '#fbbf24',
  green: '#4ade80',
  red: '#f87171',
  orange: '#f0883e',
  dark: '#0d1526',
};

export const FONT_MONO = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace";

// API base URL. In dev (Vite on :3000) we hit the backend on :8000 directly.
// In production the backend serves the built frontend on the same origin so
// relative URLs are correct.
export const API_BASE = (() => {
  const envOverride = import.meta?.env?.VITE_API_BASE;
  if (envOverride) return envOverride.replace(/\/$/, '');
  if (typeof window !== 'undefined') {
    const { hostname, port } = window.location;
    const sameOrigin = hostname !== 'localhost' && hostname !== '127.0.0.1';
    if (sameOrigin) return '';
    if (port === '3000') return 'http://localhost:8000';
  }
  return 'http://localhost:8000';
})();

export const TOKEN_KEY = 'quantedge_session_token';
