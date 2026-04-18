import uuid
from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, Float, Integer, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class TradingSignal(Base):
    __tablename__ = "trading_signals"
    __table_args__ = (UniqueConstraint("signal_hash", name="uq_signal_hash"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    symbol: Mapped[str] = mapped_column(String(20), index=True)
    signal_time: Mapped[datetime] = mapped_column(DateTime(timezone=False), index=True)

    entry_price: Mapped[float] = mapped_column(Float)
    stop_loss: Mapped[float] = mapped_column(Float)
    target_1: Mapped[float] = mapped_column(Float)
    target_2: Mapped[float] = mapped_column(Float)

    signal_strength: Mapped[int] = mapped_column(Integer, index=True)
    fundamental_score: Mapped[int] = mapped_column(Integer)
    technical_score: Mapped[int] = mapped_column(Integer)
    ai_sentiment_score: Mapped[int] = mapped_column(Integer, default=0)

    pattern_detected: Mapped[str] = mapped_column(String(100))
    ema_status: Mapped[str] = mapped_column(String(100))
    volume_confirmation: Mapped[bool] = mapped_column(Boolean, default=False)
    macd_confirmation: Mapped[bool] = mapped_column(Boolean, default=False)
    breakout_confirmation: Mapped[bool] = mapped_column(Boolean, default=False)
    vwap_confirmation: Mapped[bool] = mapped_column(Boolean, default=False)
    rsi_value: Mapped[float] = mapped_column(Float)

    analysis_summary: Mapped[str] = mapped_column(String(4000))
    is_high_confidence: Mapped[bool] = mapped_column(Boolean, index=True)
    signal_hash: Mapped[str] = mapped_column(String(128))
    source_data: Mapped[dict] = mapped_column(JSON, default=dict)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=False), default=datetime.utcnow)
