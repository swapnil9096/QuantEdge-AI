from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from dataclasses import dataclass
from typing import Any, Optional

import httpx

from app.core.config import get_settings
from app.data_providers.types import NewsItem


logger = logging.getLogger(__name__)


@dataclass
class AiSentimentResult:
    score: int
    summary: str
    method: str = "heuristic"


class AIResearchService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self._openai_semaphore = asyncio.Semaphore(1)
        self._openai_cooldown_until = 0.0
        self._last_rate_limit_log_at = 0.0

    async def analyze_news(self, symbol: str, news: list[NewsItem]) -> AiSentimentResult:
        if not news:
            return AiSentimentResult(score=0, summary="No material news available for sentiment analysis.")
        if self.settings.openai_api_key and time.time() >= self._openai_cooldown_until:
            return await self._openai_sentiment(symbol, news[:10])
        if self.settings.openai_api_key and time.time() < self._openai_cooldown_until:
            result = self._keyword_sentiment(news[:10])
            result.summary = "OpenAI rate-limited; " + result.summary
            return result
        return self._keyword_sentiment(news[:10])

    async def generate_institutional_analysis(
        self, symbol: str, evaluation: dict[str, Any]
    ) -> str:
        if not self.settings.openai_api_key or time.time() < self._openai_cooldown_until:
            return self._build_heuristic_analysis(symbol, evaluation)
        prompt = self._build_institutional_prompt(symbol, evaluation)
        async with self._openai_semaphore:
            try:
                async with httpx.AsyncClient(timeout=25) as client:
                    response = await client.post(
                        "https://api.openai.com/v1/chat/completions",
                        headers={"Authorization": f"Bearer {self.settings.openai_api_key}"},
                        json={
                            "model": self.settings.openai_model,
                            "messages": [
                                {
                                    "role": "system",
                                    "content": (
                                        "You are a senior quantitative trader analyzing institutional order flow "
                                        "and smart money concepts. Be concise (3-4 sentences max). "
                                        "Focus on actionable insight, not generic commentary."
                                    ),
                                },
                                {"role": "user", "content": prompt},
                            ],
                            "temperature": 0.2,
                            "max_tokens": 200,
                        },
                    )
                    response.raise_for_status()
                    payload = response.json()
                    return payload["choices"][0]["message"]["content"].strip()
            except httpx.HTTPStatusError as exc:
                if exc.response is not None and exc.response.status_code == 429:
                    self._handle_rate_limit(exc.response)
                return self._build_heuristic_analysis(symbol, evaluation)
            except Exception:
                return self._build_heuristic_analysis(symbol, evaluation)

    async def _openai_sentiment(self, symbol: str, news: list[NewsItem]) -> AiSentimentResult:
        headlines = [f"- {item.title}" for item in news if item.title]
        prompt = (
            f"Analyze sentiment for {symbol} using these headlines.\n"
            "Return compact JSON with keys: score (integer between -10 and 10), summary (max 2 sentences).\n"
            "Headlines:\n" + "\n".join(headlines)
        )
        async with self._openai_semaphore:
            try:
                async with httpx.AsyncClient(timeout=20) as client:
                    response = await client.post(
                        "https://api.openai.com/v1/chat/completions",
                        headers={"Authorization": f"Bearer {self.settings.openai_api_key}"},
                        json={
                            "model": self.settings.openai_model,
                            "messages": [{"role": "user", "content": prompt}],
                            "temperature": 0.1,
                        },
                    )
                    response.raise_for_status()
                    payload = response.json()
                    text = payload["choices"][0]["message"]["content"]
                    parsed = self._parse_json_block(text)
                    score = max(-10, min(10, int(parsed["score"])))
                    return AiSentimentResult(score=score, summary=str(parsed["summary"]), method="openai")
            except httpx.HTTPStatusError as exc:
                if exc.response is not None and exc.response.status_code == 429:
                    self._handle_rate_limit(exc.response)
                    return AiSentimentResult(score=0, summary="OpenAI rate-limited; heuristic sentiment used.")
                logger.warning("OpenAI sentiment HTTP error for %s: %s", symbol, exc)
                return self._keyword_sentiment(news)
            except Exception as exc:
                logger.warning("OpenAI sentiment failed for %s: %s", symbol, exc)
                return self._keyword_sentiment(news)
        return self._keyword_sentiment(news)

    def _handle_rate_limit(self, response: Optional[httpx.Response]) -> None:
        retry_after = self._retry_after_seconds(response)
        cooldown = max(min(retry_after, 600), 300)
        now = time.time()
        self._openai_cooldown_until = now + cooldown
        if now - self._last_rate_limit_log_at >= 60:
            logger.warning("OpenAI rate-limited; heuristic fallback for ~%ss", int(cooldown))
            self._last_rate_limit_log_at = now

    @staticmethod
    def _build_institutional_prompt(symbol: str, evaluation: dict[str, Any]) -> str:
        smc = evaluation.get("smart_money", {})
        conditions = evaluation.get("condition_details", {})
        return (
            f"Symbol: {symbol}\n"
            f"Price: {evaluation.get('entry_price')}\n"
            f"Trend: {evaluation.get('trend_direction')}\n"
            f"Score: {evaluation.get('total_score')}/100 "
            f"(Trend={evaluation.get('trend_score')}, Vol={evaluation.get('volume_score')}, "
            f"Pattern={evaluation.get('pattern_score')}, Inst={evaluation.get('institutional_score')}, "
            f"Mom={evaluation.get('momentum_score')})\n"
            f"Patterns: {evaluation.get('detected_patterns')}\n"
            f"BOS: {smc.get('bos', {})}\n"
            f"Liquidity: {smc.get('liquidity_grab', {})}\n"
            f"Order Block: {smc.get('order_block', {})}\n"
            f"FVG: {smc.get('fvg', {})}\n"
            f"RSI: {evaluation.get('rsi_value')}\n"
            f"Volume Strength: {evaluation.get('volume_strength')}\n"
            f"All conditions met: {evaluation.get('all_conditions_met')}\n"
            "Provide institutional-grade analysis in 3-4 sentences."
        )

    @staticmethod
    def _build_heuristic_analysis(symbol: str, evaluation: dict[str, Any]) -> str:
        parts = []
        trend = evaluation.get("trend_direction", "Unknown")
        score = evaluation.get("total_score", 0)
        patterns = evaluation.get("detected_patterns", [])
        smc = evaluation.get("smart_money", {})
        vol = evaluation.get("volume_strength", "Low")

        parts.append(f"{symbol} shows a {trend} with probability score {score}/100.")
        if patterns:
            parts.append(f"Detected patterns: {', '.join(patterns)}.")
        bos = smc.get("bos", {})
        if bos.get("detected"):
            parts.append(f"Break of structure confirmed ({bos.get('direction')}).")
        liq = smc.get("liquidity_grab", {})
        if liq.get("detected"):
            parts.append(f"Liquidity sweep detected ({liq.get('side')}) suggesting institutional activity.")
        if vol in ("High", "Very High"):
            parts.append(f"Volume is {vol}, confirming institutional participation.")
        return " ".join(parts)

    @staticmethod
    def _parse_json_block(text: str) -> dict:
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            match = re.search(r"\{[\s\S]*\}", text)
            if not match:
                raise
            return json.loads(match.group(0))

    @staticmethod
    def _keyword_sentiment(news: list[NewsItem]) -> AiSentimentResult:
        positive = {"beat", "growth", "upgrade", "strong", "profit", "expansion", "record", "surged", "rally"}
        negative = {"miss", "downgrade", "fraud", "lawsuit", "decline", "weak", "loss", "crash", "warning"}
        score = 0
        for item in news:
            title = item.title.lower()
            if any(token in title for token in positive):
                score += 1
            if any(token in title for token in negative):
                score -= 1
        score = max(-10, min(10, score))
        return AiSentimentResult(score=score, summary="Heuristic sentiment analysis completed.")

    @staticmethod
    def _retry_after_seconds(response: Optional[httpx.Response]) -> int:
        if response is None:
            return 5
        value = response.headers.get("Retry-After")
        if not value:
            return 5
        try:
            return max(int(value), 1)
        except ValueError:
            return 5
