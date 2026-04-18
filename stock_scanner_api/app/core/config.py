from __future__ import annotations

from functools import lru_cache
from typing import List, Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = Field(default="stock-scanner-api", alias="APP_NAME")
    app_env: str = Field(default="production", alias="APP_ENV")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    api_prefix: str = Field(default="/api/v1", alias="API_PREFIX")

    database_url: str = Field(alias="DATABASE_URL")
    redis_url: str = Field(alias="REDIS_URL")
    redis_ttl_seconds: int = Field(default=300, alias="REDIS_TTL_SECONDS")

    scan_interval_minutes: int = Field(default=5, alias="SCAN_INTERVAL_MINUTES")
    high_confidence_threshold: int = Field(default=80, alias="HIGH_CONFIDENCE_THRESHOLD")
    midcap_threshold: float = Field(default=5_000_000_000, alias="MIDCAP_THRESHOLD")
    promoter_holding_min: float = Field(default=45.0, alias="PROMOTER_HOLDING_MIN")
    universe_symbols: str = Field(default="", alias="UNIVERSE_SYMBOLS")

    alpha_vantage_api_key: Optional[str] = Field(default=None, alias="ALPHA_VANTAGE_API_KEY")
    twelvedata_api_key: Optional[str] = Field(default=None, alias="TWELVEDATA_API_KEY")
    polygon_api_key: Optional[str] = Field(default=None, alias="POLYGON_API_KEY")

    openai_api_key: Optional[str] = Field(default=None, alias="OPENAI_API_KEY")
    openai_model: str = Field(default="gpt-4o-mini", alias="OPENAI_MODEL")

    @property
    def symbols(self) -> List[str]:
        if not self.universe_symbols.strip():
            return []
        return [item.strip().upper() for item in self.universe_symbols.split(",") if item.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
