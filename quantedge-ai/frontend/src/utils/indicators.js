export function parseSymbol(raw) {
  if (!raw) return { ticker: '', exchange: 'NSE' };
  const clean = raw.trim().toUpperCase();
  if (clean.includes('.')) {
    const [ticker, exchange] = clean.split('.');
    return { ticker, exchange: exchange || 'NSE' };
  }
  if (clean.includes(':')) {
    const [ticker, exchange] = clean.split(':');
    return { ticker, exchange: exchange || 'NSE' };
  }
  return { ticker: clean, exchange: 'NSE' };
}

export function deterministicPattern(price) {
  const patterns = [
    'Bullish Engulfing',
    'Morning Star',
    'Hammer',
    'Inside Bar Breakout',
    'Momentum Breakout',
    'Bullish Marubozu',
  ];
  const bucket = Math.floor((Number(price) || 0) * 100) % patterns.length;
  return patterns[Math.abs(bucket)];
}

export function mlScoreFromData(sd) {
  if (!sd) return 0;
  let score = 50;
  const trend = (sd.trend || '').toLowerCase();
  if (trend.includes('strong up') || trend.includes('bullish')) score += 18;
  else if (trend.includes('up')) score += 10;
  else if (trend.includes('down') || trend.includes('bearish')) score -= 12;

  const sentiment = (sd.sentiment || '').toLowerCase();
  if (sentiment.includes('positive') || sentiment.includes('bullish')) score += 10;
  else if (sentiment.includes('negative') || sentiment.includes('bearish')) score -= 10;

  const rsi = Number(sd.rsi_estimate);
  if (!Number.isNaN(rsi)) {
    if (rsi >= 55 && rsi <= 68) score += 10;
    else if (rsi < 35 || rsi > 78) score -= 8;
  }

  const vr = Number(sd.volume) / Math.max(Number(sd.avg_volume) || 1, 1);
  if (vr >= 2) score += 8;
  else if (vr >= 1.5) score += 5;
  else if (vr < 0.8) score -= 6;

  const support = Number(sd.support);
  const price = Number(sd.price);
  if (support && price && price > 0) {
    const proximity = ((price - support) / price) * 100;
    if (proximity > 0 && proximity < 3) score += 6;
    else if (proximity < 0) score -= 10;
  }
  return Math.max(0, Math.min(99, Math.round(score)));
}

export function computeSetup(sd) {
  const price = Number(sd?.price) || 0;
  const atrEst = price * 0.022;
  const entry = price;
  const stop = Math.max(entry - atrEst * 1.5, entry * 0.95);
  const target = entry + atrEst * 3.2;
  const risk = Math.max(entry - stop, 0.01);
  const reward = target - entry;
  const rr = reward / risk;
  const expectedReturn = ((target - entry) / entry) * 100;
  return {
    entry,
    stop,
    target,
    rr,
    expectedReturn,
    atr: atrEst,
    pattern: deterministicPattern(price),
  };
}
