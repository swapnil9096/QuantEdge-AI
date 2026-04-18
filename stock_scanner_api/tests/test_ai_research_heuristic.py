from app.data_providers.types import NewsItem
from app.services.ai_research import AIResearchService


def test_empty_news_returns_zero():
    import asyncio

    service = AIResearchService()
    service.settings.openai_api_key = None  # force heuristic path
    result = asyncio.run(service.analyze_news("TEST", []))
    assert result.score == 0


def test_positive_keywords_score_positive():
    news = [
        NewsItem(title="Company posts record profit and upgrade", source="x"),
        NewsItem(title="Strong revenue growth and expansion", source="x"),
    ]
    result = AIResearchService._keyword_sentiment(news)
    assert result.score >= 2
    assert result.method == "heuristic"


def test_negative_keywords_score_negative():
    news = [
        NewsItem(title="Lawsuit filed; fraud probe opens", source="x"),
        NewsItem(title="Revenue miss and management warning", source="x"),
    ]
    result = AIResearchService._keyword_sentiment(news)
    assert result.score <= -1


def test_heuristic_analysis_includes_symbol():
    evaluation = {
        "trend_direction": "Uptrend",
        "total_score": 82,
        "detected_patterns": ["Bullish Engulfing"],
        "smart_money": {"bos": {"detected": True, "direction": "bullish"}, "liquidity_grab": {}},
        "volume_strength": "High",
    }
    text = AIResearchService._build_heuristic_analysis("RELIANCE", evaluation)
    assert "RELIANCE" in text
    assert "Uptrend" in text
    assert "Bullish Engulfing" in text
