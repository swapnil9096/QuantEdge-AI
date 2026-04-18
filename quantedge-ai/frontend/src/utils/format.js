export function fmt(value, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const abs = Math.abs(value);
  if (abs >= 1_00_000) return value.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  return Number(value).toFixed(digits);
}

export function fmtPct(value, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const sign = value >= 0 ? '+' : '';
  return `${sign}${Number(value).toFixed(digits)}%`;
}

export function formatCrore(value, currency = 'INR') {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  const n = Number(value);
  if (currency === 'INR') {
    const cr = n / 10_000_000;
    if (cr >= 1_000) return `₹${(cr / 1_000).toFixed(2)}K Cr`;
    return `₹${cr.toFixed(0)} Cr`;
  }
  if (n >= 1e12) return `$${(n / 1e12).toFixed(2)}T`;
  if (n >= 1e9) return `$${(n / 1e9).toFixed(2)}B`;
  if (n >= 1e6) return `$${(n / 1e6).toFixed(2)}M`;
  return `$${n.toFixed(0)}`;
}

export function fmtMoney(n, currency = '₹') {
  if (n === null || n === undefined || Number.isNaN(Number(n))) return '—';
  const v = Number(n);
  const abs = Math.abs(v);
  const sign = v < 0 ? '-' : '';
  if (abs >= 1e7) return `${sign}${currency}${(abs / 1e7).toFixed(2)}Cr`;
  if (abs >= 1e5) return `${sign}${currency}${(abs / 1e5).toFixed(2)}L`;
  if (abs >= 1000) return `${sign}${currency}${(abs / 1000).toFixed(1)}K`;
  return `${sign}${currency}${abs.toFixed(0)}`;
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}
