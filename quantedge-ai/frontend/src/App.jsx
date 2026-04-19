import React, { useState, useCallback, useEffect } from 'react';
import {
  Brain, Building2, Cpu, LineChart as LineIcon,
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
import { AuthScreen } from './components/AuthScreen.jsx';
import { useWebSocket } from './hooks/useWebSocket.js';
import {
  installFetchInterceptor,
  fetchStockData, fetchBacktest, fetchMLTraining, fetchAIExplanation,
} from './utils/api.js';
import { parseSymbol, computeSetup } from './utils/indicators.js';

// ---------------------------------------------------------------------------
// Tab definitions (JSX, so kept here not in constants.js)
// ---------------------------------------------------------------------------

const TABS = [
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
  const [activeTab, setActiveTab] = useState('scanner');

  // ---- Scanner state -------------------------------------------------------
  const [watchlist, setWatchlist] = useState(['RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK']);
  const [scanStatus, setScanStatus] = useState({});
  const [scanData, setScanData] = useState({});
  const [scanning, setScanning] = useState(false);
  const [lastScan, setLastScan] = useState(null);
  const [lastError, setLastError] = useState(null);

  // ---- Backtest state ------------------------------------------------------
  const [backtest, setBacktest] = useState(null);
  const [backtestLoading, setBacktestLoading] = useState(false);

  // ---- ML state ------------------------------------------------------------
  const [ml, setMl] = useState(null);
  const [mlLoading, setMlLoading] = useState(false);

  // ---- Insights state ------------------------------------------------------
  const [activeSymbol, setActiveSymbol] = useState(null);
  const [aiText, setAiText] = useState('');
  const [aiLoading, setAiLoading] = useState(false);

  // ---- AlphaScan state -----------------------------------------------------
  const [alphaState, setAlphaState] = useState({ status: 'idle' });
  const [alphaLog, setAlphaLog] = useState([]);
  const [backendDown, setBackendDown] = useState(false);

  // ---- Auth gate -----------------------------------------------------------
  const [currentUser, setCurrentUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('quantedge_user')); } catch { return null; }
  });

  const handleLogout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('quantedge_user');
    setCurrentUser(null);
  }, []);

  useEffect(() => {
    const teardown = installFetchInterceptor({ onLocked: handleLogout });
    return teardown;
  }, [handleLogout]);

  const handleLoggedIn = useCallback((res) => {
    setCurrentUser({ user_id: res.user_id, username: res.username, is_admin: res.is_admin });
  }, []);

  // ---- WebSocket live prices (Phase 3) ------------------------------------
  const { prices: livePrices, connected: wsConnected, subscribe, unsubscribe } = useWebSocket(
    currentUser ? `${API_BASE.replace(/^http/, 'ws')}/ws/live-prices` : null,
  );

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
  if (!currentUser) {
    return <AuthScreen onLoggedIn={handleLoggedIn} />;
  }

  // ---- Main render ---------------------------------------------------------
  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text }}>
      <Header lastScan={lastScan} lastError={lastError} onLogout={handleLogout} wsConnected={wsConnected} currentUser={currentUser} />
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
    </div>
  );
}
