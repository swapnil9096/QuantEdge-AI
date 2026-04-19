import React, { useState, useCallback, useEffect } from 'react';
import {
  Brain, Building2, ChevronRight, Cpu, LineChart as LineIcon,
  Rocket, Search, Sparkles, Zap,
} from 'lucide-react';
import { C, API_BASE, TOKEN_KEY } from './constants.js';
import { Spinner } from './components/shared.jsx';
import { Header } from './components/Header.jsx';
import { TopKpiStrip } from './components/TopKpiStrip.jsx';
import { ScannerTab } from './components/ScannerTab.jsx';
import { BacktestTab } from './components/BacktestTab.jsx';
import { MLTab } from './components/MLTab.jsx';
import { InsightsTab } from './components/InsightsTab.jsx';
import { AlphaScanTab } from './components/AlphaScanTab.jsx';
import { BrokerPanel } from './components/BrokerPanel.jsx';
import { LockScreen } from './components/LockScreen.jsx';
import { useWebSocket } from './hooks/useWebSocket.js';
import {
  installFetchInterceptor, apiUnlock, apiLockStatus, apiLockServer,
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

  // ---- Lock gate -----------------------------------------------------------
  const [lockStatus, setLockStatus] = useState(null);
  const [lockVersion, setLockVersion] = useState(0);

  const forceRelock = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setLockStatus((prev) => ({ ...(prev || {}), unlocked: false }));
  }, []);

  useEffect(() => {
    const teardown = installFetchInterceptor({ onLocked: forceRelock });
    return teardown;
  }, [forceRelock]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const s = await apiLockStatus();
        if (cancelled) return;
        const hasToken = !!localStorage.getItem(TOKEN_KEY);
        setLockStatus({ ...s, unlocked: s.unlocked && hasToken });
        setBackendDown(false);
      } catch {
        if (!cancelled) {
          setBackendDown(true);
          // Resolve the spinner so the UI isn't stuck on load when the
          // backend is unreachable (e.g. Render free-tier cold start).
          setLockStatus({ unlocked: false, configured: false });
        }
      }
    })();
    return () => { cancelled = true; };
  }, [lockVersion]);

  const handleUnlocked = useCallback(() => setLockVersion((v) => v + 1), []);

  const handleLock = useCallback(async () => {
    try { await apiLockServer(); } catch { /* clear token even on network failure */ }
    localStorage.removeItem(TOKEN_KEY);
    setLockStatus((prev) => ({ ...(prev || {}), unlocked: false }));
    setLockVersion((v) => v + 1);
  }, []);

  // ---- WebSocket live prices (Phase 3) ------------------------------------
  const { prices: livePrices, connected: wsConnected, subscribe, unsubscribe } = useWebSocket(
    lockStatus?.unlocked ? `${API_BASE.replace(/^http/, 'ws')}/ws/live-prices` : null,
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

  // ---- Lock gate rendering -------------------------------------------------
  if (lockStatus === null) {
    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: C.bg, color: C.muted }}>
        <Spinner />
      </div>
    );
  }
  if (!lockStatus.unlocked && !backendDown) {
    return <LockScreen initialStatus={lockStatus} onUnlocked={handleUnlocked} />;
  }

  // ---- Main render ---------------------------------------------------------
  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text }}>
      <Header lastScan={lastScan} lastError={lastError} onLock={handleLock} wsConnected={wsConnected} />
      <TopKpiStrip watchlist={watchlist} scanData={scanData} backtest={backtest} ml={ml} />

      <nav
        style={{
          display: 'flex', gap: 4, padding: '0.75rem 1.5rem 0',
          borderBottom: `1px solid ${C.border}`, overflowX: 'auto',
        }}
      >
        {TABS.map((tab) => {
          const active = tab.id === activeTab;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                padding: '0.55rem 1rem', border: 'none',
                borderBottom: `2px solid ${active ? C.teal : 'transparent'}`,
                background: 'transparent',
                color: active ? C.teal : C.muted,
                fontWeight: 600, fontSize: 13, cursor: 'pointer',
              }}
            >
              {tab.icon}
              {tab.label}
              {active && <ChevronRight size={12} />}
            </button>
          );
        })}
      </nav>

      <main style={{ padding: '1.25rem 1.5rem 3rem', maxWidth: 1400, margin: '0 auto' }}>
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
