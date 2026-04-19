import React, { useState } from 'react';
import { Sparkles, Zap, UserPlus, Shield, TrendingUp, BarChart2, Cpu } from 'lucide-react';
import { C, GRAD } from '../constants.js';
import { Spinner } from './shared.jsx';
import { apiAuthLogin, apiAuthRegister } from '../utils/api.js';

const FEATURES = [
  { icon: <TrendingUp size={15} />, text: 'Real-time NSE scanner with live prices' },
  { icon: <BarChart2 size={15} />, text: 'AI-powered backtesting & momentum analysis' },
  { icon: <Cpu size={15} />,       text: 'XGBoost / LightGBM ML signal models' },
  { icon: <Shield size={15} />,    text: 'Isolated paper portfolio per account' },
];

export function AuthScreen({ onLoggedIn }) {
  const [tab, setTab] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const reset = () => { setError(null); setPassword(''); setConfirmPwd(''); };
  const switchTab = (t) => { setTab(t); reset(); };

  const handleSubmit = async (e) => {
    e?.preventDefault?.();
    if (loading) return;
    if (tab === 'register') {
      if (!username.trim()) return setError('Username is required.');
      if (!/^[a-zA-Z0-9_\-]{3,40}$/.test(username.trim()))
        return setError('3-40 characters, letters/numbers/_ only.');
      if (password.length < 6) return setError('Password must be at least 6 characters.');
      if (password !== confirmPwd) return setError('Passwords do not match.');
    } else {
      if (!username.trim() || !password) return;
    }
    setLoading(true); setError(null);
    try {
      const fn = tab === 'login' ? apiAuthLogin : apiAuthRegister;
      const res = await fn({ username: username.trim(), password });
      localStorage.setItem('quantedge_session_token', res.token);
      localStorage.setItem('quantedge_user', JSON.stringify({
        user_id: res.user_id, username: res.username, is_admin: res.is_admin,
      }));
      onLoggedIn(res);
    } catch (exc) {
      setError(exc?.message || (tab === 'login' ? 'Login failed.' : 'Registration failed.'));
      setPassword(''); setConfirmPwd('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="qe-auth-grid">
      {/* ── Left panel — brand / features ───────────────────────────── */}
      <div className="qe-auth-brand" style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '3rem 4rem',
        background: 'rgba(0,212,168,0.03)',
        borderRight: '1px solid rgba(255,255,255,0.05)',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* Background glow orbs */}
        <div style={{
          position: 'absolute', top: '10%', left: '20%',
          width: 320, height: 320, borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(0,212,168,0.08) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute', bottom: '15%', right: '10%',
          width: 240, height: 240, borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(77,159,255,0.07) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />

        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 48, position: 'relative' }}>
          <div style={{
            width: 52, height: 52, borderRadius: 16,
            background: GRAD.teal,
            display: 'grid', placeItems: 'center',
            color: '#041018',
            boxShadow: '0 0 32px rgba(0,212,168,0.45)',
          }}>
            <Sparkles size={26} strokeWidth={2.3} />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: 26, fontWeight: 800, letterSpacing: -0.5, lineHeight: 1.1 }}>
              QuantEdge <span style={{ color: C.teal }}>AI</span>
            </h1>
            <div style={{ fontSize: 12.5, color: C.muted, marginTop: 3 }}>
              Institutional-grade quant research
            </div>
          </div>
        </div>

        {/* Headline */}
        <div style={{ marginBottom: 36, position: 'relative' }}>
          <h2 style={{
            margin: 0, fontSize: 32, fontWeight: 800, lineHeight: 1.25,
            letterSpacing: -0.8, color: C.text,
          }}>
            Trade smarter.<br />
            <span style={{
              background: GRAD.teal,
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              backgroundClip: 'text',
            }}>
              Backed by data.
            </span>
          </h2>
          <p style={{ margin: '14px 0 0', fontSize: 14.5, color: C.sub, lineHeight: 1.7, maxWidth: 380 }}>
            Scan NSE markets in real-time, run AI-powered analysis, and manage
            your paper portfolio — all in one platform.
          </p>
        </div>

        {/* Feature list */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, position: 'relative' }}>
          {FEATURES.map(({ icon, text }) => (
            <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 30, height: 30, borderRadius: 8, flexShrink: 0,
                background: 'rgba(0,212,168,0.1)',
                border: '1px solid rgba(0,212,168,0.2)',
                display: 'grid', placeItems: 'center',
                color: C.teal,
              }}>
                {icon}
              </div>
              <span style={{ fontSize: 13.5, color: C.sub }}>{text}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Right panel — form ───────────────────────────────────────── */}
      <div className="qe-auth-form-col" style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem',
      }}>
        <form
          onSubmit={handleSubmit}
          style={{
            width: '100%',
            maxWidth: 400,
            animation: 'qe-fade-up 0.35s cubic-bezier(0.4,0,0.2,1) both',
          }}
        >
          {/* Card heading */}
          <div style={{ marginBottom: 28 }}>
            <h2 style={{ margin: 0, fontSize: 22, fontWeight: 800, letterSpacing: -0.3 }}>
              {tab === 'login' ? 'Welcome back' : 'Create account'}
            </h2>
            <p style={{ margin: '6px 0 0', fontSize: 13, color: C.muted }}>
              {tab === 'login'
                ? 'Sign in to your workspace'
                : 'Get started with QuantEdge AI'}
            </p>
          </div>

          {/* Tab switcher */}
          <div style={{
            display: 'flex', gap: 0, marginBottom: 24,
            background: 'rgba(255,255,255,0.04)',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: 12, padding: 3,
          }}>
            {['login', 'register'].map((t) => (
              <button
                key={t} type="button" onClick={() => switchTab(t)}
                style={{
                  flex: 1, padding: '0.5rem 0',
                  background: tab === t
                    ? 'rgba(255,255,255,0.07)'
                    : 'transparent',
                  border: tab === t
                    ? '1px solid rgba(255,255,255,0.1)'
                    : '1px solid transparent',
                  borderRadius: 9,
                  color: tab === t ? C.text : C.muted,
                  fontWeight: tab === t ? 700 : 500,
                  fontSize: 13, cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  letterSpacing: 0.1,
                }}
              >
                {t === 'login' ? 'Sign In' : 'Register'}
              </button>
            ))}
          </div>

          {/* Fields */}
          <FieldGroup label="Username">
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
              placeholder="your_username"
              disabled={loading}
              style={inputStyle(false)}
            />
          </FieldGroup>

          <FieldGroup label="Password">
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
              placeholder="••••••••"
              disabled={loading}
              style={inputStyle(false)}
            />
          </FieldGroup>

          {tab === 'register' && (
            <FieldGroup label="Confirm Password">
              <input
                type="password"
                value={confirmPwd}
                onChange={(e) => setConfirmPwd(e.target.value)}
                autoComplete="new-password"
                placeholder="••••••••"
                disabled={loading}
                style={inputStyle(!!error && error.includes('match'))}
              />
            </FieldGroup>
          )}

          {error && (
            <div style={{
              padding: '0.6rem 0.85rem',
              background: 'rgba(248,113,113,0.08)',
              border: '1px solid rgba(248,113,113,0.3)',
              borderRadius: 10,
              color: C.red, fontSize: 12.5,
              marginBottom: 16,
              display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <span style={{ fontSize: 15 }}>⚠</span>
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading || !username.trim() || !password}
            style={{
              width: '100%',
              padding: '0.75rem 1rem',
              background: (loading || !username.trim() || !password)
                ? 'rgba(255,255,255,0.05)'
                : GRAD.teal,
              color: (loading || !username.trim() || !password) ? C.muted : '#041018',
              border: '1px solid ' + ((loading || !username.trim() || !password)
                ? 'rgba(255,255,255,0.07)'
                : 'transparent'),
              borderRadius: 12,
              fontWeight: 700, fontSize: 14,
              cursor: (loading || !username.trim() || !password) ? 'not-allowed' : 'pointer',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              boxShadow: (loading || !username.trim() || !password)
                ? 'none'
                : '0 0 24px rgba(0,212,168,0.35)',
              transition: 'all 0.18s ease',
              letterSpacing: 0.2,
            }}
          >
            {loading
              ? <Spinner color="#94a3b8" size={14} />
              : tab === 'login' ? <Zap size={15} /> : <UserPlus size={15} />
            }
            {loading
              ? 'Signing in…'
              : tab === 'login' ? 'Sign In' : 'Create Account'}
          </button>

          {tab === 'register' && (
            <p style={{
              marginTop: 14, fontSize: 11.5, color: C.muted,
              textAlign: 'center', lineHeight: 1.6,
            }}>
              Your paper portfolio and settings are fully isolated to your account.
            </p>
          )}
        </form>
      </div>
    </div>
  );
}

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function FieldGroup({ label, children }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{
        display: 'block', fontSize: 11, fontWeight: 600,
        letterSpacing: 0.7, textTransform: 'uppercase',
        color: C.muted, marginBottom: 6,
      }}>
        {label}
      </label>
      {children}
    </div>
  );
}

function inputStyle(hasError) {
  return {
    width: '100%',
    padding: '0.65rem 0.9rem',
    background: 'rgba(255,255,255,0.04)',
    color: C.text,
    border: `1px solid ${hasError ? 'rgba(248,113,113,0.5)' : 'rgba(255,255,255,0.1)'}`,
    borderRadius: 10,
    outline: 'none',
    fontFamily: 'inherit',
    fontSize: 14,
    boxSizing: 'border-box',
    transition: 'border-color 0.15s ease, box-shadow 0.15s ease',
  };
}
