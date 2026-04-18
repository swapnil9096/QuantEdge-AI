import React, { useEffect, useState } from 'react';
import { Sparkles, Zap } from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner } from './shared.jsx';
import { apiUnlock, apiLockStatus } from '../utils/api.js';

export function LockScreen({ initialStatus, onUnlocked }) {
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(initialStatus || null);
  const [retryCountdown, setRetryCountdown] = useState(0);

  useEffect(() => {
    if (retryCountdown <= 0) return;
    const id = setInterval(() => setRetryCountdown((n) => Math.max(0, n - 1)), 1000);
    return () => clearInterval(id);
  }, [retryCountdown]);

  useEffect(() => {
    apiLockStatus()
      .then((s) => {
        setStatus(s);
        if (s.retry_after_seconds) setRetryCountdown(s.retry_after_seconds);
      })
      .catch(() => {});
  }, []);

  const submit = async (e) => {
    e?.preventDefault?.();
    if (!password.trim() || loading || retryCountdown > 0) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiUnlock(password);
      localStorage.setItem('quantedge_session_token', res.access_token);
      setPassword('');
      onUnlocked();
    } catch (exc) {
      setError(exc);
      if (exc.retryAfter) setRetryCountdown(exc.retryAfter);
      setPassword('');
    } finally {
      setLoading(false);
    }
  };

  const notConfigured = status && status.configured === false;
  const depsMissing = status && status.deps_available === false;

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
        onSubmit={submit}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 18 }}>
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
            <div style={{ fontSize: 12, color: C.muted }}>Enter master password to unlock</div>
          </div>
        </div>

        {notConfigured && (
          <div
            style={{
              padding: '0.7rem 0.85rem',
              background: 'rgba(251,191,36,0.1)',
              border: `1px solid ${C.yellow}`,
              borderRadius: 10,
              color: C.yellow,
              fontSize: 12,
              marginBottom: 14,
              lineHeight: 1.6,
            }}
          >
            <b>Vault not configured.</b>
            <br />
            Run this on the server, then reload:
            <pre
              style={{
                background: C.dark,
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                padding: '6px 10px',
                margin: '8px 0 0',
                fontSize: 11,
                fontFamily: FONT_MONO,
                color: C.sub,
                overflowX: 'auto',
              }}
            >
              python scripts/setup_secrets.py
            </pre>
          </div>
        )}

        {depsMissing && (
          <div
            style={{
              padding: '0.6rem 0.85rem',
              background: 'rgba(248,113,113,0.08)',
              border: `1px solid ${C.red}`,
              borderRadius: 10,
              color: C.red,
              fontSize: 12,
              marginBottom: 14,
            }}
          >
            Server is missing <code>cryptography</code> / <code>PyJWT</code>. Run{' '}
            <code>pip install -r requirements.txt</code>.
          </div>
        )}

        <label
          style={{
            display: 'block',
            fontSize: 11,
            letterSpacing: 0.6,
            textTransform: 'uppercase',
            color: C.muted,
            marginBottom: 6,
          }}
        >
          Master password
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoFocus
          disabled={loading || retryCountdown > 0 || notConfigured}
          placeholder="••••••••••••"
          style={{
            width: '100%',
            padding: '0.7rem 0.9rem',
            background: C.dark,
            color: C.text,
            border: `1px solid ${error ? C.red : C.border}`,
            borderRadius: 10,
            outline: 'none',
            fontFamily: FONT_MONO,
            fontSize: 14,
            letterSpacing: 1.5,
            boxSizing: 'border-box',
          }}
        />

        {error && (
          <div
            style={{
              marginTop: 10,
              padding: '0.5rem 0.75rem',
              background: 'rgba(248,113,113,0.1)',
              border: `1px solid ${C.red}`,
              borderRadius: 8,
              color: C.red,
              fontSize: 12,
            }}
          >
            {error.code === 'rate_limited'
              ? `Too many attempts. Wait ${retryCountdown || error.retryAfter}s before retrying.`
              : error.code === 'wrong_password'
              ? `Invalid password${error.failedAttempts ? ` (attempt ${error.failedAttempts})` : ''}.${
                  retryCountdown ? ` Cooling down ${retryCountdown}s.` : ''
                }`
              : error.message || 'Unlock failed.'}
          </div>
        )}

        {retryCountdown > 0 && !error && (
          <div
            style={{
              marginTop: 10,
              padding: '0.5rem 0.75rem',
              background: 'rgba(251,191,36,0.1)',
              border: `1px solid ${C.yellow}`,
              borderRadius: 8,
              color: C.yellow,
              fontSize: 12,
            }}
          >
            Cooling down — retry in {retryCountdown}s.
          </div>
        )}

        <button
          type="submit"
          disabled={loading || !password.trim() || retryCountdown > 0 || notConfigured}
          style={{
            width: '100%',
            marginTop: 14,
            padding: '0.7rem 1rem',
            background:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? C.border
                : `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
            color:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? C.muted
                : '#041018',
            border: 'none',
            borderRadius: 10,
            fontWeight: 700,
            fontSize: 14,
            cursor:
              loading || !password.trim() || retryCountdown > 0 || notConfigured
                ? 'not-allowed'
                : 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
          }}
        >
          {loading ? <Spinner color="#041018" /> : <Zap size={15} />}
          Unlock
        </button>

        <div
          style={{
            marginTop: 16,
            fontSize: 11,
            color: C.muted,
            lineHeight: 1.6,
            textAlign: 'center',
          }}
        >
          Secrets are encrypted at rest with scrypt + AES-256-GCM.
          <br />
          Password is never stored. Lost password = rotate API keys and rerun setup.
        </div>
      </form>
    </div>
  );
}
