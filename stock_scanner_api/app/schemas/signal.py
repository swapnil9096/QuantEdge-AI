from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel


class TradingSignalResponse(BaseModel):
    id: UUID
    stock: str
    signal_time: datetime
    entry_price: float
    stop_loss: float
    target_1: float
    target_2: float
    signal_strength: int
    pattern_detected: str
    ema_status: str
    volume_confirmation: bool
    analysis_summary: str
    fundamental_score: int
    technical_score: int
    ai_sentiment_score: int
    is_high_confidence: bool
    source_data: dict[str, Any]


class ScanResponse(BaseModel):
    scanned_symbols: int
    opportunities_found: int
    opportunities: list[TradingSignalResponse]
