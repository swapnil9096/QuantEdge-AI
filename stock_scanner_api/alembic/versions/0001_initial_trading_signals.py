"""initial trading_signals table

Revision ID: 0001_initial_trading_signals
Revises:
Create Date: 2026-04-18 00:00:00.000000

"""
from __future__ import annotations

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import UUID as PG_UUID


revision: str = "0001_initial_trading_signals"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _uuid_type():
    """Use native Postgres UUID where available, fall back to CHAR(36) elsewhere."""
    bind = op.get_bind()
    if bind.dialect.name == "postgresql":
        return PG_UUID(as_uuid=True)
    return sa.CHAR(36)


def upgrade() -> None:
    op.create_table(
        "trading_signals",
        sa.Column("id", _uuid_type(), primary_key=True),
        sa.Column("symbol", sa.String(length=20), nullable=False),
        sa.Column("signal_time", sa.DateTime(timezone=False), nullable=False),
        sa.Column("entry_price", sa.Float(), nullable=False),
        sa.Column("stop_loss", sa.Float(), nullable=False),
        sa.Column("target_1", sa.Float(), nullable=False),
        sa.Column("target_2", sa.Float(), nullable=False),
        sa.Column("signal_strength", sa.Integer(), nullable=False),
        sa.Column("fundamental_score", sa.Integer(), nullable=False),
        sa.Column("technical_score", sa.Integer(), nullable=False),
        sa.Column("ai_sentiment_score", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("pattern_detected", sa.String(length=100), nullable=False),
        sa.Column("ema_status", sa.String(length=100), nullable=False),
        sa.Column("volume_confirmation", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("macd_confirmation", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("breakout_confirmation", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("vwap_confirmation", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("rsi_value", sa.Float(), nullable=False),
        sa.Column("analysis_summary", sa.String(length=4000), nullable=False),
        sa.Column("is_high_confidence", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("signal_hash", sa.String(length=128), nullable=False),
        sa.Column("source_data", sa.JSON(), nullable=False, server_default=sa.text("'{}'")),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=False),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.UniqueConstraint("signal_hash", name="uq_signal_hash"),
    )
    op.create_index("ix_trading_signals_symbol", "trading_signals", ["symbol"])
    op.create_index("ix_trading_signals_signal_time", "trading_signals", ["signal_time"])
    op.create_index("ix_trading_signals_signal_strength", "trading_signals", ["signal_strength"])
    op.create_index("ix_trading_signals_is_high_confidence", "trading_signals", ["is_high_confidence"])


def downgrade() -> None:
    op.drop_index("ix_trading_signals_is_high_confidence", table_name="trading_signals")
    op.drop_index("ix_trading_signals_signal_strength", table_name="trading_signals")
    op.drop_index("ix_trading_signals_signal_time", table_name="trading_signals")
    op.drop_index("ix_trading_signals_symbol", table_name="trading_signals")
    op.drop_table("trading_signals")
