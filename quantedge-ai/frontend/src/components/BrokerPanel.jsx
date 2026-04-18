import React, { useState, useEffect, useCallback } from 'react';
import {
  Activity, AlertTriangle, Building2, CheckCircle2, CircleDollarSign,
  Gauge, RefreshCw, TrendingDown, TrendingUp, Wifi, WifiOff, Zap,
} from 'lucide-react';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, KpiCard } from './shared.jsx';
import { fmt, fmtPct, fmtMoney } from '../utils/format.js';
import {
  fetchBrokerStatus, connectBroker, disconnectBroker,
  fetchBrokerPositions, fetchBrokerHoldings, fetchBrokerFunds, fetchBrokerOrders,
} from '../utils/api.js';

export function BrokerPanel() {
  const [status, setStatus] = useState(null);
  const [funds, setFunds] = useState(null);
  const [positions, setPositions] = useState([]);
  const [holdings, setHoldings] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('positions');

  const loadStatus = useCallback(async () => {
    try {
      const s = await fetchBrokerStatus();
      setStatus(s);
      return s;
    } catch (exc) {
      setStatus({ connected: false, error: exc.message });
      return null;
    }
  }, []);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const s = await fetchBrokerStatus();
      setStatus(s);
      if (s?.connected) {
        const [f, pos, hold, ord] = await Promise.all([
          fetchBrokerFunds().catch(() => null),
          fetchBrokerPositions().catch(() => ({ positions: [] })),
          fetchBrokerHoldings().catch(() => ({ holdings: [] })),
          fetchBrokerOrders().catch(() => ({ orders: [] })),
        ]);
        setFunds(f);
        setPositions(pos?.positions || []);
        setHoldings(hold?.holdings || []);
        setOrders(ord?.orders || []);
      }
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const handleConnect = async () => {
    setConnecting(true);
    setError(null);
    try {
      await connectBroker({});
      await loadAll();
    } catch (exc) {
      setError(exc.message || String(exc));
    } finally {
      setConnecting(false);
    }
  };

  const handleDisconnect = async () => {
    if (!window.confirm('Disconnect from Angel One? Active session will be terminated.')) return;
    try {
      await disconnectBroker();
      setPositions([]);
      setHoldings([]);
      setFunds(null);
      await loadStatus();
    } catch (exc) {
      setError(exc.message || String(exc));
    }
  };

  const connected = !!status?.connected;
  const keysConfigured = status?.keys_configured !== false;

  const SUB_TABS = [
    { id: 'positions', label: 'Positions' },
    { id: 'holdings', label: 'Holdings' },
    { id: 'orders', label: 'Orders' },
  ];

  return (
    <div>
      {/* Connection status */}
      <Section
        title="Angel One SmartAPI"
        subtitle="Real-money broker integration via Angel One SmartAPI + TOTP authentication."
        icon={<Building2 size={18} />}
        right={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button
              onClick={loadAll}
              disabled={loading}
              style={{
                padding: '5px 10px',
                background: 'transparent',
                border: `1px solid ${C.border}`,
                borderRadius: 8,
                color: C.teal,
                cursor: loading ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
              }}
            >
              {loading ? <Spinner size={12} /> : <RefreshCw size={12} />} Refresh
            </button>
            {connected ? (
              <button
                onClick={handleDisconnect}
                style={{
                  padding: '5px 12px',
                  background: 'rgba(248,113,113,0.12)',
                  border: `1px solid ${C.red}`,
                  borderRadius: 8,
                  color: C.red,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  fontWeight: 600,
                }}
              >
                <WifiOff size={12} /> Disconnect
              </button>
            ) : (
              <button
                onClick={handleConnect}
                disabled={connecting || !keysConfigured}
                style={{
                  padding: '5px 12px',
                  background: connecting || !keysConfigured ? C.border : `linear-gradient(135deg, ${C.teal} 0%, ${C.blue} 100%)`,
                  border: 'none',
                  borderRadius: 8,
                  color: connecting || !keysConfigured ? C.muted : '#041018',
                  cursor: connecting || !keysConfigured ? 'not-allowed' : 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  fontWeight: 700,
                }}
              >
                {connecting ? <Spinner size={12} color="#041018" /> : <Zap size={12} />}
                {connecting ? 'Connecting…' : 'Connect'}
              </button>
            )}
          </div>
        }
      >
        {error && (
          <div
            style={{
              padding: '0.5rem 0.75rem',
              background: 'rgba(248,113,113,0.08)',
              border: `1px solid ${C.red}`,
              borderRadius: 8,
              color: C.red,
              fontSize: 12,
              marginBottom: 10,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <AlertTriangle size={13} /> {error}
          </div>
        )}

        {!keysConfigured && (
          <div
            style={{
              padding: '0.7rem 0.85rem',
              background: 'rgba(251,191,36,0.1)',
              border: `1px solid ${C.yellow}`,
              borderRadius: 10,
              color: C.yellow,
              fontSize: 12,
              lineHeight: 1.6,
              marginBottom: 12,
            }}
          >
            <b>Angel One API keys not configured.</b> Add them via the secrets vault:
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
            >{`python scripts/setup_secrets.py \\
  --add-secret ANGEL_CLIENT_ID=<your_id> \\
  --add-secret ANGEL_PASSWORD=<your_pwd> \\
  --add-secret ANGEL_TOTP_SECRET=<totp_key> \\
  --add-secret ANGEL_API_KEY=<api_key>`}</pre>
          </div>
        )}

        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 14px',
            borderRadius: 999,
            background: connected ? 'rgba(74,222,128,0.1)' : C.dark,
            border: `1px solid ${connected ? C.green : C.border}`,
            color: connected ? C.green : C.muted,
            fontSize: 12,
            fontWeight: 600,
          }}
        >
          {connected ? (
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: C.green, animation: 'qe-pulse 1.4s ease-in-out infinite' }} />
          ) : (
            <WifiOff size={12} />
          )}
          {connected ? `Connected · ${status?.client_id || ''}` : 'Disconnected'}
          {status?.last_connected_at && !connected && (
            <span style={{ color: C.muted, fontSize: 11, marginLeft: 4 }}>
              · Last: {new Date(status.last_connected_at).toLocaleString()}
            </span>
          )}
        </div>
      </Section>

      {/* Funds */}
      {connected && funds && (
        <Section title="Funds" subtitle="Available margin and net worth" icon={<CircleDollarSign size={18} />}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            <KpiCard icon={<Gauge size={14} />} label="Net value" value={fmtMoney(funds.net)} color={C.teal} />
            <KpiCard icon={<CheckCircle2 size={14} />} label="Available cash" value={fmtMoney(funds.available_cash)} color={C.green} />
            <KpiCard icon={<Activity size={14} />} label="Used margin" value={fmtMoney(funds.used_margin)} color={C.orange} />
          </div>
        </Section>
      )}

      {/* Sub-tabs: Positions / Holdings / Orders */}
      {connected && (
        <>
          <div style={{ display: 'flex', gap: 4, marginBottom: 14, borderBottom: `1px solid ${C.border}`, paddingBottom: 0 }}>
            {SUB_TABS.map((t) => {
              const active = t.id === activeTab;
              return (
                <button
                  key={t.id}
                  onClick={() => setActiveTab(t.id)}
                  style={{
                    padding: '0.5rem 1rem',
                    border: 'none',
                    borderBottom: `2px solid ${active ? C.teal : 'transparent'}`,
                    background: 'transparent',
                    color: active ? C.teal : C.muted,
                    fontWeight: 600,
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                >
                  {t.label}
                </button>
              );
            })}
          </div>

          {/* Positions table */}
          {activeTab === 'positions' && (
            <Section title="Open positions" subtitle="Day positions from Angel One" icon={<TrendingUp size={18} />}>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                      {['Symbol', 'Qty', 'Avg Price', 'LTP', 'P&L', 'P&L %'].map((h) => (
                        <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {positions.length === 0 && (
                      <tr>
                        <td colSpan={6} style={{ padding: '1rem', textAlign: 'center', color: C.muted }}>No open positions.</td>
                      </tr>
                    )}
                    {positions.map((p, i) => {
                      const pnl = Number(p.pnl || 0);
                      const pnlColor = pnl >= 0 ? C.green : C.red;
                      return (
                        <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}
                          onMouseEnter={(e) => (e.currentTarget.style.background = C.dark)}
                          onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                        >
                          <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{p.symbol}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{p.qty ?? p.netqty ?? '—'}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(p.avgprice ?? p.avg_price)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(p.ltp)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{fmtMoney(pnl)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>
                            {p.pnlpercent != null ? fmtPct(p.pnlpercent) : '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </Section>
          )}

          {/* Holdings table */}
          {activeTab === 'holdings' && (
            <Section title="Holdings" subtitle="Long-term delivery holdings" icon={<TrendingDown size={18} />}>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                      {['Symbol', 'Qty', 'Avg Price', 'LTP', 'Current Value', 'P&L'].map((h) => (
                        <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {holdings.length === 0 && (
                      <tr>
                        <td colSpan={6} style={{ padding: '1rem', textAlign: 'center', color: C.muted }}>No holdings.</td>
                      </tr>
                    )}
                    {holdings.map((h, i) => {
                      const pnl = Number(h.profitandloss || 0);
                      const pnlColor = pnl >= 0 ? C.green : C.red;
                      return (
                        <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}
                          onMouseEnter={(e) => (e.currentTarget.style.background = C.dark)}
                          onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                        >
                          <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{h.tradingsymbol}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{h.quantity}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(h.averageprice)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(h.ltp)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{fmtMoney(h.holdingvalue)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: pnlColor }}>{fmtMoney(pnl)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </Section>
          )}

          {/* Orders table */}
          {activeTab === 'orders' && (
            <Section title="Order history" subtitle="Recent orders placed via this session" icon={<Activity size={18} />}>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ color: C.muted, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                      {['Order ID', 'Symbol', 'Side', 'Qty', 'Price', 'Type', 'Status', 'Time'].map((h) => (
                        <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: `1px solid ${C.border}` }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {orders.length === 0 && (
                      <tr>
                        <td colSpan={8} style={{ padding: '1rem', textAlign: 'center', color: C.muted }}>No orders yet.</td>
                      </tr>
                    )}
                    {orders.map((o, i) => {
                      const statusColor =
                        o.status === 'COMPLETE' ? C.green
                        : o.status === 'REJECTED' || o.status === 'CANCELLED' ? C.red
                        : C.yellow;
                      return (
                        <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO, color: C.muted, fontSize: 11 }}>{o.order_id || '—'}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, fontFamily: FONT_MONO }}>{o.symbol}</td>
                          <td style={{ padding: '0.55rem 0.6rem', color: o.side === 'BUY' ? C.green : C.red, fontWeight: 700 }}>{o.side}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>{o.qty}</td>
                          <td style={{ padding: '0.55rem 0.6rem', fontFamily: FONT_MONO }}>₹{fmt(o.price)}</td>
                          <td style={{ padding: '0.55rem 0.6rem', color: C.muted }}>{o.order_type}</td>
                          <td style={{ padding: '0.55rem 0.6rem' }}>
                            <span style={{ padding: '2px 8px', borderRadius: 999, background: `${statusColor}22`, color: statusColor, fontSize: 11, fontWeight: 700 }}>
                              {o.status}
                            </span>
                          </td>
                          <td style={{ padding: '0.55rem 0.6rem', color: C.muted, fontSize: 11, fontFamily: FONT_MONO }}>
                            {o.placed_at ? new Date(o.placed_at).toLocaleString() : '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </Section>
          )}
        </>
      )}

      {!connected && !loading && keysConfigured && (
        <Section title="Not connected" icon={<WifiOff size={18} />}>
          <div style={{ color: C.muted, fontSize: 13 }}>
            Click <b>Connect</b> above to authenticate with Angel One via TOTP. Positions, holdings and
            order history will appear here once connected.
          </div>
        </Section>
      )}
    </div>
  );
}
