from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.analytics.metrics import compute_backtest_results, get_model_performance
from app.db.session import get_db
from app.schemas.signal import ScanResponse, TradingSignalResponse
from app.services.scanner_service import ScannerService, to_signal_response


router = APIRouter()
scanner = ScannerService()


@router.get("/scan-stocks")
async def scan_stocks(
    symbols: str | None = Query(default=None, description="Comma separated symbols (omit to scan universe)"),
    max_symbols: int = Query(default=50, ge=1, le=500, description="When not using 'symbols', cap universe scan at this many (default 50 to avoid long runs)"),
    db: AsyncSession = Depends(get_db),
) -> dict:
    if symbols:
        symbol_list = [item.strip().upper() for item in symbols.split(",") if item.strip()]
        opportunities = await scanner.scan_market(db, symbols=symbol_list)
        return {
            "scanned_symbols": len(symbol_list),
            "high_probability_count": len(opportunities),
            "high_probability": [
                {
                    "symbol": sig.symbol,
                    "entry_price": sig.entry_price,
                    "stop_loss": sig.stop_loss,
                    "target": sig.target_1,
                    "target_2": sig.target_2,
                    "probability_score": int(sig.signal_strength),
                    "model_prediction": (sig.source_data or {}).get("model_prediction", "N/A"),
                    "pattern": sig.pattern_detected,
                    "trend": sig.ema_status,
                    "ai_analysis": sig.analysis_summary,
                }
                for sig in opportunities
            ],
        }
    result = await scanner.auto_scan(db, max_symbols=max_symbols)
    result["max_symbols_cap"] = max_symbols
    return result


@router.get("/signals", response_model=list[TradingSignalResponse])
async def get_signals(limit: int = 100, db: AsyncSession = Depends(get_db)) -> list[TradingSignalResponse]:
    rows = await scanner.list_signals(db, limit=limit)
    return [to_signal_response(item) for item in rows]


@router.get("/signals/high-confidence", response_model=list[TradingSignalResponse])
async def get_high_confidence_signals(limit: int = 100, db: AsyncSession = Depends(get_db)) -> list[TradingSignalResponse]:
    rows = await scanner.list_signals(db, limit=limit, high_confidence_only=True)
    return [to_signal_response(item) for item in rows]


@router.get("/top-trades")
async def get_top_trades(limit: int = 10, db: AsyncSession = Depends(get_db)) -> list[dict]:
    signals = await scanner.get_top_trades(db, limit=limit)
    return [
        {
            "symbol": sig.symbol,
            "entry_price": sig.entry_price,
            "stop_loss": sig.stop_loss,
            "target": sig.target_1,
            "target_1": sig.target_1,
            "target_2": sig.target_2,
            "probability_score": int(sig.signal_strength),
            "model_prediction": (sig.source_data or {}).get("model_prediction", "N/A"),
            "pattern": sig.pattern_detected,
            "trend": sig.ema_status,
            "volume_strength": "High" if sig.volume_confirmation else "Low",
            "ai_analysis": sig.analysis_summary,
            "signal_time": sig.signal_time.isoformat(),
        }
        for sig in signals
    ]


@router.get("/stock-analysis/{symbol}")
async def get_stock_analysis(symbol: str, db: AsyncSession = Depends(get_db)) -> dict:
    symbol = symbol.upper()
    analysis = await scanner.analyze_symbol_live(symbol)
    if analysis is None:
        return {
            "symbol": symbol,
            "message": "Unable to fetch data for this symbol. Check if the symbol is valid.",
        }

    if analysis.get("is_high_probability"):
        await scanner.analyze_symbol_and_store(db, symbol)
        await db.commit()

    return analysis


@router.get("/backtest-results")
async def backtest_results(use_cache: bool = True) -> dict:
    """Return backtest metrics: win rate, profit factor, avg return, max drawdown, Sharpe, risk/reward, equity curve."""
    return compute_backtest_results(use_cache=use_cache)


@router.get("/model-performance")
async def model_performance() -> dict:
    """Return best model name and validation/test metrics (accuracy, precision, recall, F1, ROC-AUC)."""
    return get_model_performance()
