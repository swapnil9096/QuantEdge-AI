import { useState, useEffect } from 'react';

const PREFIX = 'qe_';

export function usePersistedState(key, defaultValue) {
  const storageKey = PREFIX + key;
  const [state, setState] = useState(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved === null) return defaultValue;
      return JSON.parse(saved);
    } catch {
      return defaultValue;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(state));
    } catch { /* quota exceeded — silently ignore */ }
  }, [storageKey, state]);

  return [state, setState];
}
