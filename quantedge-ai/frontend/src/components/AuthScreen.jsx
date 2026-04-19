import React, { useState } from 'react';
import { Sparkles, Zap, UserPlus } from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner } from './shared.jsx';
import { apiAuthLogin, apiAuthRegister } from '../utils/api.js';

const inputStyle = (hasError) => ({
  width: '100%',
  padding: '0.65rem 0.9rem',
  background: C.dark,
  color: C.text,
  border: `1px solid ${hasError ? C.red : C.border}`,
  borderRadius: 10,
  outline: 'none',
  fontFamily: 'inherit',
  fontSize: 14,
  boxSizing: 'border-box',
  marginBottom: 10,
});

const labelStyle = {
  display: 'block',
  fontSize: 11,
  letterSpacing: 0.6,
  textTransform: 'uppercase',
  color: C.muted,
  marginBottom: 4,
};

export function AuthScreen({ onLoggedIn }) {
  const [tab, setTab] = useState('login');    // 'login' | 'register'
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const reset = () => {
    setError(null);
    setPassword('');
    setConfirmPwd('');
  };

  const switchTab = (t) => {
    setTab(t);
    reset();
  };

  const handleSubmit = async (e) => {
    e?.preventDefault?.();
    if (loading) return;

    if (tab === 'register') {
      if (!username.trim()) return setError('Username is required.');
      if (!/^[a-zA-Z0-9_\-]{3,40}$/.test(username.trim())) {
        return setError('Username: 3-40 characters, letters/numbers/_ only.');
      }
      if (password.length < 6) return setError('Password must be at least 6 characters.');
      if (password !== confirmPwd) return setError('Passwords do not match.');
    } else {
      if (!username.trim() || !password) return;
    }

    setLoading(true);
    setError(null);
    try {
      const fn = tab === 'login' ? apiAuthLogin : apiAuthRegister;
      const res = await fn({ username: username.trim(), password });
      // Store token + user info
      localStorage.setItem('quantedge_session_token', res.token);
      localStorage.setItem('quantedge_user', JSON.stringify({
        user_id: res.user_id,
        username: res.username,
        is_admin: res.is_admin,
      }));
      onLoggedIn(res);
    } catch (exc) {
      setError(exc?.message || (tab === 'login' ? 'Login failed.' : 'Registration failed.'));
      setPassword('');
      setConfirmPwd('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: C.bg,
        color: C.text,
        padding: '2rem',
      }}
    >
      <form
        onSubmit={handleSubmit}
        style={{
          width: '100%',
          maxWidth: 420,
          background: C.card,
          border: `1px solid ${C.border}`,
          borderRadius: 14,
          padding: '1.75rem 1.5rem',
          boxShadow: '0 20px 60px rgba(0,0,0,0.4)',
          animation: 'qe-fade-in 0.35s ease',
        }}
      >
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <div
            style={{
              width: 44,
              height: 44,
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
            <h1 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>
              QuantEdge <span style={{ color: C.teal }}>AI</span>
            </h1>
            <div style={{ fontSize: 12, color: C.muted }}>Institutional-grade quant research</div>
          </div>
        </div>

        {/* Tabs */}
        <div
          style={{
            display: 'flex',
            gap: 0,
            marginBottom: 20,
            background: C.dark,
            borderRadius: 10,
            padding: 3,
          }}
        >
          {['login', 'register'].map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => switchTab(t)}
              style={{
                flex: 1,
                padding: '0.5rem',
                background: tab === t ? C.card : 'transparent',
                border: tab === t ? `1px solid ${C.border}` : '1px solid transparent',
                borderRadius: 8,
                color: tab === t ? C.text : C.muted,
                fontWeight: tab === t ? 700 : 400,
                fontSize: 13,
                cursor: 'pointer',
                transition: 'all 0.15s',
              }}
            >
              {t === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          ))}
        </div>

        {/* Fields */}
        <label style={labelStyle}>Username</label>
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

        <label style={labelStyle}>Password</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
          placeholder="••••••••"
          disabled={loading}
          style={inputStyle(false)}
        />

        {tab === 'register' && (
          <>
            <label style={labelStyle}>Confirm password</label>
            <input
              type="password"
              value={confirmPwd}
              onChange={(e) => setConfirmPwd(e.target.value)}
              autoComplete="new-password"
              placeholder="••••••••"
              disabled={loading}
              style={inputStyle(!!error && error.includes('match'))}
            />
          </>
        )}

        {error && (
          <div
            style={{
              padding: '0.5rem 0.75rem',
              background: 'rgba(248,113,113,0.1)',
              border: `1px solid ${C.red}`,
              borderRadius: 8,
              color: C.red,
              fontSize: 12,
              marginBottom: 10,
            }}
          >
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading || !username.trim() || !password}
          style={{
            width: '100%',
            marginTop: 4,
            padding: '0.7rem 1rem',
            background:
              loading || !username.trim() || !password
                ? C.border
                : `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
            color: loading || !username.trim() || !password ? C.muted : '#041018',
            border: 'none',
            borderRadius: 10,
            fontWeight: 700,
            fontSize: 14,
            cursor: loading || !username.trim() || !password ? 'not-allowed' : 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
          }}
        >
          {loading ? (
            <Spinner color="#041018" />
          ) : tab === 'login' ? (
            <Zap size={15} />
          ) : (
            <UserPlus size={15} />
          )}
          {tab === 'login' ? 'Sign In' : 'Create Account'}
        </button>

        {tab === 'register' && (
          <div
            style={{
              marginTop: 12,
              fontSize: 11,
              color: C.muted,
              lineHeight: 1.6,
              textAlign: 'center',
            }}
          >
            Your paper portfolio and settings are isolated to your account.
          </div>
        )}
      </form>
    </div>
  );
}
