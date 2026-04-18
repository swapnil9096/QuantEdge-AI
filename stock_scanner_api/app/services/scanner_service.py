from __future__ import annotations

import hashlib
import logging
from datetime import datetime
from typing import Any

from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.cache.redis_client import cache_set_json
from app.core.config import get_settings
from app.data_providers.orchestrator import DataProviderOrchestrator
from app.models.signal import TradingSignal
from app.schemas.signal import TradingSignalResponse
from app.ml.inference import model_available, predict_proba_for_ohlcv
from app.services.ai_research import AIResearchService
from app.services.fundamental_engine import FundamentalEngine
from app.services.technical_engine import TechnicalEngine
from app.services.universe import fetch_nifty_universe


logger = logging.getLogger(__name__)
ML_PROBABILITY_THRESHOLD = 0.80


class ScannerService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.providers = DataProviderOrchestrator()
        self.fundamental_engine = FundamentalEngine()
        self.technical_engine = TechnicalEngine()
        self.ai_research = AIResearchService()

    async def auto_scan(self, db: AsyncSession, max_symbols: int | None = 50) -> dict[str, Any]:
        """Fetch full NIFTY universe, pre-screen with technicals, deep-analyze candidates.
        max_symbols caps how many symbols to scan (default 50) to avoid very long runs."""
        full_universe = await fetch_nifty_universe()
        universe = full_universe[:max_symbols] if (max_symbols is not None and max_symbols > 0) else full_universe
        logger.info("Auto-scan starting on %d symbols (of %d in universe)", len(universe), len(full_universe))

        high_probability: list[dict] = []
        watchlist: list[dict] = []
        scanned = 0
        errors = 0

        for symbol in universe:
            scanned += 1
            try:
                quick = await self._quick_screen(symbol)
                if quick is None:
                    continue
                if quick["pass_prescreen"]:
                    full = await self._evaluate_symbol(symbol)
                    if full is None:
                        continue
                    if full.get("is_high_probability"):
                        high_probability.append(full)
                        await self._persist_signal(db, full)
                    elif full.get("probability_score", 0) >= 50:
                        watchlist.append(full)
            except Exception as exc:
                errors += 1
                logger.debug("Scan error for %s: %s", symbol, exc)

        await db.commit()
        high_probability.sort(key=lambda x: x.get("probability_score", 0), reverse=True)
        watchlist.sort(key=lambda x: x.get("probability_score", 0), reverse=True)

        return {
            "scanned_symbols": scanned,
            "universe_size": len(full_universe),
            "scanned_cap": max_symbols,
            "high_probability_count": len(high_probability),
            "watchlist_count": len(watchlist),
            "errors": errors,
            "high_probability": high_probability,
            "watchlist": watchlist[:20],
        }

    async def scan_market(self, db: AsyncSession, symbols: list[str] | None = None) -> list[TradingSignal]:
        """Scan provided symbols or full universe; persist high-probability signals."""
        if symbols is None:
            result = await self.auto_scan(db)
            return []
        opportunities: list[TradingSignal] = []
        for symbol in symbols:
            try:
                signal = await self.analyze_symbol_and_store(db, symbol)
                if signal:
                    opportunities.append(signal)
            except Exception as exc:
                logger.exception("Scan failed for symbol %s: %s", symbol, exc)
        await db.commit()
        return opportunities

    async def _quick_screen(self, symbol: str) -> dict[str, Any] | None:
        """Phase 1: fetch only OHLCV and check basic technical conditions.
        Avoids expensive fundamentals/news calls for clearly non-qualifying stocks."""
        ohlcv, source = await self.providers._fetch_ohlcv(symbol)
        if ohlcv is None or ohlcv.empty or len(ohlcv) < 220:
            return None

        try:
            tech = self.technical_engine.evaluate(ohlcv)
        except ValueError:
            return None

        passes = (
            tech.condition_details.get("price_above_200ema", False)
            and tech.condition_details.get("price_above_50ema", False)
            and (tech.total_score >= 25 or tech.condition_details.get("crossed_above_200ema", False))
        )

        return {
            "symbol": symbol,
            "pass_prescreen": passes,
            "total_score": tech.total_score,
            "trend": tech.trend_direction,
        }

    async def analyze_symbol_and_store(self, db: AsyncSession, symbol: str) -> TradingSignal | None:
        evaluation = await self._evaluate_symbol(symbol)
        if evaluation is None or not evaluation.get("is_high_probability"):
            return None
        return await self._persist_signal(db, evaluation)

    async def _persist_signal(self, db: AsyncSession, evaluation: dict[str, Any]) -> TradingSignal | None:
        symbol = evaluation["symbol"]
        signal_hash = self._build_signal_hash(symbol, evaluation["entry_price"], evaluation["stop_loss"], evaluation)
        existing = await db.execute(select(TradingSignal).where(TradingSignal.signal_hash == signal_hash))
        if existing.scalar_one_or_none():
            return None

        signal = TradingSignal(
            symbol=symbol,
            signal_time=datetime.utcnow(),
            entry_price=evaluation["entry_price"],
            stop_loss=evaluation["stop_loss"],
            target_1=evaluation["target_1"],
            target_2=evaluation["target_2"],
            signal_strength=evaluation["probability_score"],
            fundamental_score=evaluation["fundamental_score"],
            technical_score=evaluation["total_score"],
            ai_sentiment_score=evaluation["ai_sentiment_score"],
            pattern_detected=", ".join(evaluation["detected_patterns"]) or "None",
            ema_status=evaluation["trend"],
            volume_confirmation=evaluation["volume_strength"] in ("High", "Very High"),
            macd_confirmation=evaluation["indicators"].get("macd_bullish_cross", False),
            breakout_confirmation=evaluation["condition_details"].get("break_of_structure", False),
            vwap_confirmation=evaluation["indicators"].get("price_above_vwap", False),
            rsi_value=evaluation["rsi_value"],
            analysis_summary=evaluation["ai_analysis"],
            is_high_confidence=evaluation["is_high_probability"],
            signal_hash=signal_hash,
            source_data={
                "condition_details": evaluation["condition_details"],
                "smart_money": evaluation["smart_money"],
                "providers_used": evaluation["providers_used"],
                "ml_probability": evaluation.get("ml_probability"),
                "model_prediction": evaluation.get("model_prediction", "N/A"),
            },
        )
        db.add(signal)
        await db.flush()

        await cache_set_json(f"analysis:{symbol}", {
            "symbol": symbol,
            "probability_score": evaluation["probability_score"],
            "entry_price": evaluation["entry_price"],
            "stop_loss": evaluation["stop_loss"],
            "target_1": evaluation["target_1"],
            "target_2": evaluation["target_2"],
            "pattern": evaluation["pattern"],
            "trend": evaluation["trend"],
            "ai_analysis": evaluation["ai_analysis"],
        })
        return signal

    async def analyze_symbol_live(self, symbol: str) -> dict[str, Any] | None:
        return await self._evaluate_symbol(symbol)

    async def get_top_trades(self, db: AsyncSession, limit: int = 10) -> list[TradingSignal]:
        result = await db.execute(
            select(TradingSignal)
            .where(TradingSignal.is_high_confidence.is_(True))
            .order_by(desc(TradingSignal.signal_strength), desc(TradingSignal.signal_time))
            .limit(limit)
        )
        return list(result.scalars().all())

    async def _evaluate_symbol(self, symbol: str) -> dict[str, Any] | None:
        bundle = await self.providers.fetch_research_bundle(symbol)
        if bundle is None:
            return None

        fundamental = self.fundamental_engine.evaluate(bundle.fundamentals)

        try:
            tech = self.technical_engine.evaluate(bundle.ohlcv)
        except ValueError as exc:
            return self._build_insufficient_data_response(symbol, bundle, fundamental, str(exc))

        ai_result = await self.ai_research.analyze_news(symbol, bundle.news)
        probability_score = min(tech.total_score + max(0, ai_result.score), 100)
        ml_probability: float | None = None
        model_prediction = "N/A"
        if bundle.ohlcv is not None and len(bundle.ohlcv) >= 250:
            try:
                ml_probability = predict_proba_for_ohlcv(bundle.ohlcv)
            except Exception as exc:
                logger.debug("ML inference for %s: %s", symbol, exc)
        if model_available() and ml_probability is not None:
            probability_score = round(ml_probability * 100)
            if ml_probability >= ML_PROBABILITY_THRESHOLD:
                model_prediction = "High Probability"
            elif ml_probability >= 0.5:
                model_prediction = "Medium"
            else:
                model_prediction = "Low"
            is_high_ml = ml_probability > ML_PROBABILITY_THRESHOLD
        else:
            is_high_ml = True  # when no model, rely only on technical/fundamental

        eval_data = {
            "symbol": symbol,
            "entry_price": tech.entry_price,
            "stop_loss": tech.stop_loss,
            "target_1": tech.target_1,
            "target_2": tech.target_2,
            "probability_score": probability_score,
            "ml_probability": ml_probability,
            "model_prediction": model_prediction,
            "pattern": ", ".join(tech.detected_patterns) if tech.detected_patterns else "None",
            "trend": tech.trend_direction,
            "volume_strength": tech.volume_strength,
            "is_high_probability": (tech.is_high_probability and fundamental.passed) and is_high_ml,
            "all_conditions_met": tech.all_conditions_met,
            "total_score": tech.total_score,
            "trend_score": tech.trend_score,
            "volume_score": tech.volume_score,
            "pattern_score": tech.pattern_score,
            "institutional_score": tech.institutional_score,
            "momentum_score": tech.momentum_score,
            "fundamental_score": fundamental.score,
            "fundamental_passed": fundamental.passed,
            "ai_sentiment_score": max(0, ai_result.score),
            "ai_sentiment_method": ai_result.method,
            "rsi_value": round(tech.rsi_value, 2),
            "detected_patterns": tech.detected_patterns,
            "indicators": tech.indicators,
            "smart_money": tech.smart_money,
            "condition_details": tech.condition_details,
            "fundamental_checks": fundamental.details,
            "providers_used": {
                "ohlcv_provider": bundle.ohlcv_source,
                "fundamental_providers": bundle.fundamental_sources,
                "news_providers": bundle.news_sources,
                "ai_provider": "openai" if self.settings.openai_api_key else "heuristic",
                "ai_model": self.settings.openai_model if self.settings.openai_api_key else None,
                "alpha_vantage_enabled": bool(self.settings.alpha_vantage_api_key),
                "twelvedata_enabled": bool(self.settings.twelvedata_api_key),
                "polygon_enabled": bool(self.settings.polygon_api_key),
            },
            "data_timestamp": str(bundle.ohlcv.index[-1]),
        }

        if probability_score >= 65:
            recommendation = "HIGH_PROBABILITY_BUY" if tech.is_high_probability and fundamental.passed else "WATCHLIST"
        else:
            recommendation = "NO_SETUP"
        eval_data["recommendation"] = recommendation

        ai_explanation = await self.ai_research.generate_institutional_analysis(symbol, eval_data)
        eval_data["ai_analysis"] = ai_explanation

        return eval_data

    def _build_insufficient_data_response(
        self, symbol: str, bundle: Any, fundamental: Any, error: str
    ) -> dict[str, Any]:
        close_series = bundle.ohlcv["close"]
        if hasattr(close_series, "iloc"):
            current_price = float(close_series.iloc[-1])
        else:
            current_price = float(close_series)
        return {
            "symbol": symbol,
            "entry_price": current_price,
            "stop_loss": current_price * 0.97,
            "target_1": current_price * 1.02,
            "target_2": current_price * 1.03,
            "probability_score": fundamental.score,
            "pattern": "Insufficient history",
            "trend": "Unknown",
            "volume_strength": "Unknown",
            "is_high_probability": False,
            "all_conditions_met": False,
            "total_score": 0,
            "trend_score": 0,
            "volume_score": 0,
            "pattern_score": 0,
            "institutional_score": 0,
            "momentum_score": 0,
            "fundamental_score": fundamental.score,
            "fundamental_passed": fundamental.passed,
            "ai_sentiment_score": 0,
            "ai_sentiment_method": "none",
            "rsi_value": 0.0,
            "detected_patterns": [],
            "indicators": {},
            "smart_money": {},
            "condition_details": {"error": error},
            "fundamental_checks": fundamental.details,
            "recommendation": "INSUFFICIENT_DATA",
            "ai_analysis": f"{symbol} has insufficient price history for full analysis ({len(bundle.ohlcv)} candles).",
            "providers_used": {
                "ohlcv_provider": bundle.ohlcv_source,
                "fundamental_providers": bundle.fundamental_sources,
                "news_providers": bundle.news_sources,
                "ai_provider": "none",
                "ai_model": None,
                "alpha_vantage_enabled": bool(self.settings.alpha_vantage_api_key),
                "twelvedata_enabled": bool(self.settings.twelvedata_api_key),
                "polygon_enabled": bool(self.settings.polygon_api_key),
            },
            "data_timestamp": str(bundle.ohlcv.index[-1]),
        }

    @staticmethod
    def _build_signal_hash(symbol: str, entry_price: float, stop_loss: float, evaluation: dict) -> str:
        patterns = evaluation.get("detected_patterns", [])
        base = f"{symbol}|{round(entry_price, 2)}|{round(stop_loss, 2)}|{','.join(patterns)}"
        return hashlib.sha256(base.encode("utf-8")).hexdigest()

    async def list_signals(self, db: AsyncSession, limit: int = 100, high_confidence_only: bool = False) -> list[TradingSignal]:
        query = select(TradingSignal)
        if high_confidence_only:
            query = query.where(TradingSignal.is_high_confidence.is_(True))
        query = query.order_by(desc(TradingSignal.signal_time)).limit(limit)
        result = await db.execute(query)
        return list(result.scalars().all())

    async def latest_signal_for_symbol(self, db: AsyncSession, symbol: str) -> TradingSignal | None:
        result = await db.execute(
            select(TradingSignal)
            .where(TradingSignal.symbol == symbol.upper())
            .order_by(desc(TradingSignal.signal_time))
            .limit(1)
        )
        return result.scalar_one_or_none()


def to_signal_response(signal: TradingSignal) -> TradingSignalResponse:
    return TradingSignalResponse(
        id=signal.id,
        stock=signal.symbol,
        signal_time=signal.signal_time,
        entry_price=signal.entry_price,
        stop_loss=signal.stop_loss,
        target_1=signal.target_1,
        target_2=signal.target_2,
        signal_strength=signal.signal_strength,
        pattern_detected=signal.pattern_detected,
        ema_status=signal.ema_status,
        volume_confirmation=signal.volume_confirmation,
        analysis_summary=signal.analysis_summary,
        fundamental_score=signal.fundamental_score,
        technical_score=signal.technical_score,
        ai_sentiment_score=signal.ai_sentiment_score,
        is_high_confidence=signal.is_high_confidence,
        source_data=signal.source_data,
    )
