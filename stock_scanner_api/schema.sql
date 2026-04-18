CREATE TABLE IF NOT EXISTS trading_signals (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    signal_time TIMESTAMP NOT NULL,
    entry_price DOUBLE PRECISION NOT NULL,
    stop_loss DOUBLE PRECISION NOT NULL,
    target_1 DOUBLE PRECISION NOT NULL,
    target_2 DOUBLE PRECISION NOT NULL,
    signal_strength INTEGER NOT NULL,
    fundamental_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    ai_sentiment_score INTEGER NOT NULL DEFAULT 0,
    pattern_detected VARCHAR(100) NOT NULL,
    ema_status VARCHAR(100) NOT NULL,
    volume_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    macd_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    breakout_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    vwap_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    rsi_value DOUBLE PRECISION NOT NULL,
    analysis_summary VARCHAR(4000) NOT NULL,
    is_high_confidence BOOLEAN NOT NULL DEFAULT FALSE,
    signal_hash VARCHAR(128) NOT NULL UNIQUE,
    source_data JSON NOT NULL DEFAULT '{}'::json,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trading_signals_symbol ON trading_signals(symbol);
CREATE INDEX IF NOT EXISTS idx_trading_signals_signal_time ON trading_signals(signal_time DESC);
CREATE INDEX IF NOT EXISTS idx_trading_signals_high_conf ON trading_signals(is_high_confidence);
