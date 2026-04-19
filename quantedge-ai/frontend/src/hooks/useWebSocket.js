import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * useWebSocket — connects to a WebSocket URL and manages subscriptions.
 *
 * @param {string|null} url  Full WebSocket URL (ws:// or wss://). Pass null to
 *                           keep the hook idle (e.g. while the vault is locked).
 *
 * Protocol (server expects):
 *   Client → Server: { action: "subscribe",   symbols: ["RELIANCE", "TCS"] }
 *   Client → Server: { action: "unsubscribe", symbols: ["RELIANCE"] }
 *   Server → Client: { type: "price_update", symbol: "RELIANCE", price, change_pct, volume, ts }
 *   Server → Client: { type: "error", message: "..." }
 *
 * Returns:
 *   prices    — { [SYMBOL]: { price, change_pct, volume, ts } }
 *   connected — boolean
 *   subscribe(symbols)   — add symbols to live feed
 *   unsubscribe(symbols) — remove symbols from live feed
 */
export function useWebSocket(url) {
  const [prices, setPrices] = useState({});
  const [connected, setConnected] = useState(false);
  const wsRef = useRef(null);
  const subsRef = useRef(new Set());
  const retryRef = useRef(null);
  const retryDelay = useRef(1000);
  const mountedRef = useRef(true);

  const wsUrl = url || null;

  const send = useCallback((msg) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);

  const connect = useCallback(() => {
    if (!mountedRef.current) return;
    if (!wsUrl) return;  // idle — no URL (vault locked)
    if (wsRef.current && wsRef.current.readyState !== WebSocket.CLOSED) return;

    let ws;
    try {
      ws = new WebSocket(wsUrl);
    } catch (err) {
      // Invalid URL (e.g. relative path when API_BASE is empty) — schedule retry
      console.warn('[useWebSocket] WebSocket construction failed:', err.message);
      const delay = Math.min(retryDelay.current, 30000);
      retryDelay.current = delay * 2;
      retryRef.current = setTimeout(connect, delay);
      return;
    }
    wsRef.current = ws;

    ws.onopen = () => {
      if (!mountedRef.current) { ws.close(); return; }
      setConnected(true);
      retryDelay.current = 1000;
      // Re-subscribe to all current symbols
      if (subsRef.current.size > 0) {
        ws.send(JSON.stringify({ action: 'subscribe', symbols: [...subsRef.current] }));
      }
    };

    ws.onmessage = (event) => {
      if (!mountedRef.current) return;
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'price_update') {
          setPrices((prev) => ({
            ...prev,
            [msg.symbol]: {
              price: msg.price,
              change_pct: msg.change_pct,
              volume: msg.volume,
              ts: msg.ts,
            },
          }));
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onclose = () => {
      if (!mountedRef.current) return;
      setConnected(false);
      wsRef.current = null;
      if (!wsUrl) return;  // don't retry if URL was cleared
      // Exponential backoff reconnect (max 30s)
      const delay = Math.min(retryDelay.current, 30000);
      retryDelay.current = delay * 2;
      retryRef.current = setTimeout(connect, delay);
    };

    ws.onerror = () => {
      // onclose will fire after onerror; let it handle reconnect
      ws.close();
    };
  }, [wsUrl]);

  useEffect(() => {
    mountedRef.current = true;
    if (wsUrl) {
      retryDelay.current = 1000;
      connect();
    } else {
      // URL cleared (vault locked) — close any open connection
      clearTimeout(retryRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
        wsRef.current = null;
      }
      setConnected(false);
    }
    return () => {
      mountedRef.current = false;
      clearTimeout(retryRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null; // prevent reconnect on intentional close
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connect, wsUrl]);

  const subscribe = useCallback((symbols) => {
    const arr = Array.isArray(symbols) ? symbols : [symbols];
    arr.forEach((s) => subsRef.current.add(s));
    send({ action: 'subscribe', symbols: arr });
  }, [send]);

  const unsubscribe = useCallback((symbols) => {
    const arr = Array.isArray(symbols) ? symbols : [symbols];
    arr.forEach((s) => subsRef.current.delete(s));
    send({ action: 'unsubscribe', symbols: arr });
  }, [send]);

  return { prices, connected, subscribe, unsubscribe };
}
