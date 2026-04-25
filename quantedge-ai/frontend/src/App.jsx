import React, { useState, useCallback, useEffect } from 'react';
import {
  BarChart3, Brain, Building2, Cpu, LineChart as LineIcon,
  Rocket, Search,
} from 'lucide-react';
import { C, API_BASE, TOKEN_KEY } from './constants.js';
import { Header } from './components/Header.jsx';
import { TopKpiStrip } from './components/TopKpiStrip.jsx';
import { ScannerTab } from './components/ScannerTab.jsx';
import { BacktestTab } from './components/BacktestTab.jsx';
import { MLTab } from './components/MLTab.jsx';
import { InsightsTab } from './components/InsightsTab.jsx';
import { AlphaScanTab } from './components/AlphaScanTab.jsx';
import { BrokerPanel } from './components/BrokerPanel.jsx';
import { DashboardTab } from './components/DashboardTab.jsx';
import { AuthScreen } from './components/AuthScreen.jsx';
import { WelcomeModal } from './components/WelcomeModal.jsx';
import { useWebSocket } from './hooks/useWebSocket.js';
import { usePersistedState } from './hooks/usePersistedState.js';
import {
  installFetchInterceptor, apiAuthMe,
  fetchStockData, fetchBacktest, fetchMLTraining, fetchAIExplanation,
} from './utils/api.js';
import { Spinner } from './components/shared.jsx';
import { parseSymbol, computeSetup } from './utils/indicators.js';

// ---------------------------------------------------------------------------
// Tab definitions (JSX, so kept here not in constants.js)
// ---------------------------------------------------------------------------

const TABS = [
  { id: 'dashboard', label: 'Dashboard',   icon: <BarChart3 size={14} /> },
  { id: 'scanner',   label: 'Scanner',     icon: <Search size={14} /> },
  { id: 'backtest',  label: 'Backtest',    icon: <LineIcon size={14} /> },
  { id: 'ml',        label: 'ML Models',   icon: <Cpu size={14} /> },
  { id: 'insights',  label: 'AI Insights', icon: <Brain size={14} /> },
  { id: 'alphascan', label: 'AlphaScan',   icon: <Rocket size={14} /> },
  { id: 'broker',    label: 'Broker',      icon: <Building2 size={14} /> },
];

// ---------------------------------------------------------------------------
// Main App
// ---------------------------------------------------------------------------

export default function App() {
  // ---- Tab -----------------------------------------------------------------
  const [activeTab, setActiveTab] = usePersistedState('activeTab', 'dashboard');

  // ---- Scanner state -------------------------------------------------------
  const [watchlist, setWatchlist] = usePersistedState('watchlist', ['RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK']);
  const [scanStatus, setScanStatus] = useState({});
  const [scanData, setScanData] = usePersistedState('scanData', {});
  const [scanning, setScanning] = useState(false);
  const [lastScan, setLastScan] = usePersistedState('lastScan', null);
  const [lastError, setLastError] = useState(null);

  // ---- Backtest state ------------------------------------------------------
  const [backtest, setBacktest] = usePersistedState('backtest', null);
  const [backtestLoading, setBacktestLoading] = useState(false);

  // ---- ML state ------------------------------------------------------------
  const [ml, setMl] = usePersistedState('ml', null);
  const [mlLoading, setMlLoading] = useState(false);

  // ---- Insights state ------------------------------------------------------
  const [activeSymbol, setActiveSymbol] = usePersistedState('activeSymbol', null);
  const [aiText, setAiText] = usePersistedState('aiText', '');
  const [aiLoading, setAiLoading] = useState(false);

  // ---- AlphaScan state -----------------------------------------------------
  const [alphaState, setAlphaState] = usePersistedState('alphaState', { status: 'idle' });
  const [alphaLog, setAlphaLog] = useState([]);
  const [backendDown, setBackendDown] = useState(false);

  // ---- Auth gate -----------------------------------------------------------
  // Do NOT initialise currentUser from localStorage directly.
  // We validate the stored token with /auth/me before showing the app so the
  // main UI (and its child components) never mounts with a stale/invalid token.
  const [currentUser, setCurrentUser] = useState(null);
  const [showWelcome, setShowWelcome] = useState(false);

  // sessionChecking=true only when there are stored credentials to verify.
  // While true we render a loading spinner instead of the main UI, which means
  // child components don't mount and don't fire API calls before the fetch
  // interceptor is set up — eliminating the useEffect ordering race.
  const [sessionChecking, setSessionChecking] = useState(
    () => !!(localStorage.getItem(TOKEN_KEY) && localStorage.getItem('quantedge_user')),
  );

  const handleLogout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('quantedge_user');
    const keys = Object.keys(localStorage).filter((k) => k.startsWith('qe_'));
    keys.forEach((k) => localStorage.removeItem(k));
    setCurrentUser(null);
    setSessionChecking(false);
  }, []);

  useEffect(() => {
    // Install the fetch interceptor first (synchronous).
    const teardown = installFetchInterceptor({ onLocked: handleLogout });

    const token    = localStorage.getItem(TOKEN_KEY);
    const userJson = localStorage.getItem('quantedge_user');
    if (!token || !userJson) {
      setSessionChecking(false);
      return teardown;
    }

    let storedUser = null;
    try { storedUser = JSON.parse(userJson); } catch { /* fall through */ }
    if (!storedUser) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('quantedge_user');
      setSessionChecking(false);
      return teardown;
    }

    // Validate the token against the live backend before showing the app.
    // apiAuthMe() goes through the patched fetch so the interceptor adds the
    // Authorization header automatically.
    apiAuthMe()
      .then(() => setCurrentUser(storedUser))
      .catch(() => {
        // 401 (invalid/expired token) or network error — clear the session.
        // The interceptor may have already called handleLogout; these
        // operations are idempotent so double-calling is harmless.
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem('quantedge_user');
      })
      .finally(() => setSessionChecking(false));

    return teardown;
  }, [handleLogout]);

  // Keep-alive: ping backend every 4 min to prevent Render sleep
  useEffect(() => {
    if (!API_BASE) return;
    fetch(`${API_BASE}/health`).catch(() => {});
    const id = setInterval(() => {
      fetch(`${API_BASE}/health`).catch(() => {});
    }, 4 * 60 * 1000);
    return () => clearInterval(id);
  }, []);

  const handleLoggedIn = useCallback((res) => {
    setCurrentUser({ user_id: res.user_id, username: res.username, is_admin: res.is_admin });
    const key = `qe_welcomed_${res.user_id}`;
    if (!sessionStorage.getItem(key)) {
      sessionStorage.setItem(key, '1');
      setShowWelcome(true);
    }
  }, []);

  // ---- WebSocket live prices (Phase 3) ------------------------------------
  // Only open a WebSocket when we have an absolute API base URL.
  // If API_BASE is '' (same-origin / single-domain deploy), relative ws:// URLs
  // are invalid and the WebSocket constructor would throw a DOMException.
  const _wsUrl = currentUser && API_BASE
    ? `${API_BASE.replace(/^http/, 'ws')}/ws/live-prices`
    : null;
  const { prices: livePrices, connected: wsConnected, subscribe, unsubscribe } = useWebSocket(_wsUrl);

  // Subscribe watchlist symbols whenever they change
  useEffect(() => {
    if (wsConnected && watchlist.length) {
      subscribe(watchlist);
    }
  }, [wsConnected, watchlist, subscribe]);

  // ---- Watchlist actions ---------------------------------------------------
  const addSymbol = useCallback((raw) => {
    const { ticker } = parseSymbol(raw);
    if (!ticker) return;
    setWatchlist((list) => (list.includes(ticker) ? list : [...list, ticker]));
  }, []);

  const removeSymbol = useCallback((sym) => {
    setWatchlist((list) => list.filter((s) => s !== sym));
    setScanData((data) => { const n = { ...data }; delete n[sym]; return n; });
    setScanStatus((s) => { const n = { ...s }; delete n[sym]; return n; });
    if (wsConnected) unsubscribe([sym]);
  }, [wsConnected, unsubscribe]);

  // ---- Scan ----------------------------------------------------------------
  const runScan = useCallback(async () => {
    if (scanning || !watchlist.length) return;
    setScanning(true);
    setLastError(null);
    const startedAt = new Date().toISOString();
    setScanStatus(Object.fromEntries(watchlist.map((s) => [s, 'loading'])));
    for (const sym of watchlist) {
      try {
        const data = await fetchStockData(sym);
        setScanData((prev) => ({ ...prev, [sym]: data }));
        setScanStatus((s) => ({ ...s, [sym]: 'ok' }));
      } catch (exc) {
        console.warn('Scan failed for', sym, exc);
        setScanStatus((s) => ({ ...s, [sym]: 'error' }));
        setLastError(exc.message || String(exc));
      }
    }
    setLastScan(startedAt);
    setScanning(false);
  }, [scanning, watchlist]);

  // ---- Backtest ------------------------------------------------------------
  const runBacktest = useCallback(async () => {
    if (backtestLoading || !watchlist.length) return;
    setBacktestLoading(true);
    try {
      const data = await fetchBacktest(watchlist);
      setBacktest(data);
    } catch (exc) {
      setLastError(exc.message || String(exc));
    } finally {
      setBacktestLoading(false);
    }
  }, [backtestLoading, watchlist]);

  // ---- ML training (Phase 2 — real backend) --------------------------------
  const runTraining = useCallback(async () => {
    if (mlLoading) return;
    setMlLoading(true);
    try {
      const data = await fetchMLTraining(watchlist.length ? watchlist : null);
      setMl(data);
    } catch (exc) {
      setLastError(exc.message || String(exc));
    } finally {
      setMlLoading(false);
    }
  }, [mlLoading, watchlist]);

  // ---- AI Insights ---------------------------------------------------------
  const openInsights = useCallback((sym) => {
    setActiveSymbol(sym);
    setAiText('');
    setActiveTab('insights');
  }, []);

  const runExplain = useCallback(async (sym) => {
    const sd = scanData[sym];
    if (!sd) return;
    setAiLoading(true);
    try {
      const setup = computeSetup(sd);
      const text = await fetchAIExplanation(sym, sd, setup);
      setAiText(text || 'No analysis returned.');
    } catch (exc) {
      setAiText(`Unable to generate analysis: ${exc.message || exc}`);
    } finally {
      setAiLoading(false);
    }
  }, [scanData]);

  // ---- AlphaScan -----------------------------------------------------------
  const runAlphaScan = useCallback(async () => {
    setAlphaState({ status: 'scanning' });
    const ts = new Date().toLocaleTimeString();
    setAlphaLog((log) => [...log, { t: ts, level: 'info', msg: 'Dispatching /scan-best-stock…' }]);
    try {
      const r = await fetch(`${API_BASE}/scan-best-stock`, { method: 'POST' });
      if (!r.ok) throw new Error(`Server responded ${r.status}`);
      const data = await r.json();
      if (data.trade_found) {
        setAlphaState({ status: 'result', result: data.result });
        setAlphaLog((log) => [
          ...log,
          { t: new Date().toLocaleTimeString(), level: 'ok', msg: `Winner: ${data.result.symbol} · score ${data.result.confidence_score}` },
        ]);
      } else {
        setAlphaState({ status: 'no-trade', noTrade: data.no_trade });
        setAlphaLog((log) => [
          ...log,
          { t: new Date().toLocaleTimeString(), level: 'info', msg: `No trade · ${data.no_trade?.stocks_scanned || 0} scanned` },
        ]);
      }
      setBackendDown(false);
    } catch (exc) {
      setAlphaState({ status: 'error', error: exc.message || String(exc) });
      setAlphaLog((log) => [...log, { t: new Date().toLocaleTimeString(), level: 'error', msg: exc.message || 'Scan failed' }]);
      setBackendDown(true);
    }
  }, []);

  // ---- Auth gate rendering -------------------------------------------------
  // Show a minimal spinner while we verify the stored session.
  if (sessionChecking) {
    return (
      <div style={{
        minHeight: '100vh', background: C.bg,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Spinner size={28} color={C.teal} />
      </div>
    );
  }
  if (!currentUser) {
    return <AuthScreen onLoggedIn={handleLoggedIn} />;
  }

  // ---- Main render ---------------------------------------------------------
  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text }}>
      <Header lastScan={lastScan} lastError={lastError} onLogout={handleLogout} wsConnected={wsConnected} currentUser={currentUser} fromCache={!!lastScan && !scanning} />
      <TopKpiStrip watchlist={watchlist} scanData={scanData} backtest={backtest} ml={ml} />

      {/* Tab nav */}
      <nav style={{
        display: 'flex', gap: 2, padding: '0.85rem 1.5rem 0',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        overflowX: 'auto',
      }}>
        {TABS.map((tab) => {
          const active = tab.id === activeTab;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                padding: '0.5rem 1rem',
                border: 'none',
                borderBottom: `2px solid ${active ? C.teal : 'transparent'}`,
                background: active ? 'rgba(0,212,168,0.06)' : 'transparent',
                borderRadius: '8px 8px 0 0',
                color: active ? C.teal : C.muted,
                fontWeight: active ? 700 : 500,
                fontSize: 13, cursor: 'pointer',
                transition: 'all 0.15s ease',
                whiteSpace: 'nowrap',
                letterSpacing: 0.1,
              }}
              onMouseEnter={(e) => { if (!active) e.currentTarget.style.color = C.sub; }}
              onMouseLeave={(e) => { if (!active) e.currentTarget.style.color = C.muted; }}
            >
              {tab.icon}
              {tab.label}
            </button>
          );
        })}
      </nav>

      <main style={{ padding: '1.25rem 1.5rem 4rem', maxWidth: 1400, margin: '0 auto' }}>
        {activeTab === 'dashboard' && <DashboardTab />}
        {activeTab === 'scanner' && (
          <ScannerTab
            watchlist={watchlist}
            addSymbol={addSymbol}
            removeSymbol={removeSymbol}
            scanStatus={scanStatus}
            scanData={scanData}
            runScan={runScan}
            scanning={scanning}
            openInsights={openInsights}
            livePrices={livePrices}
          />
        )}
        {activeTab === 'backtest' && (
          <BacktestTab backtest={backtest} loading={backtestLoading} runBacktest={runBacktest} />
        )}
        {activeTab === 'ml' && (
          <MLTab ml={ml} loading={mlLoading} runTraining={runTraining} onMLData={setMl} />
        )}
        {activeTab === 'insights' && (
          <InsightsTab
            watchlist={watchlist}
            scanData={scanData}
            activeSymbol={activeSymbol}
            setActiveSymbol={(s) => { setActiveSymbol(s); setAiText(''); }}
            aiText={aiText}
            aiLoading={aiLoading}
            runExplain={runExplain}
          />
        )}
        {activeTab === 'alphascan' && (
          <AlphaScanTab state={alphaState} log={alphaLog} runScan={runAlphaScan} backendDown={backendDown} />
        )}
        {activeTab === 'broker' && <BrokerPanel />}
      </main>

      {showWelcome && (
        <WelcomeModal
          username={currentUser.username}
          onClose={() => setShowWelcome(false)}
        />
      )}
    </div>
  );
}
