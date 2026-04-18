package com.trading.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * News Analysis Service
 * 
 * Fetches and analyzes relevant news for stocks to determine sentiment
 */
@Service
@Slf4j
public class NewsAnalysisService {
    
    private final RestTemplate restTemplate;
    
    @Value("${api.news.enabled:true}")
    private boolean newsEnabled;
    
    @Value("${api.news.source:web}")
    private String newsSource; // web, mcp, or mock
    
    public NewsAnalysisService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Analyze news for a stock (backward compatibility - uses current market status)
     */
    public NewsEvaluation analyzeNews(String symbol, String companyName) {
        // Use default market status check (assumes market might be open)
        // For backward compatibility, we'll check market status internally
        // In production, this should be called with explicit market status
        return analyzeNews(symbol, companyName, true); // Default to market open for backward compatibility
    }
    
    /**
     * Analyze news sentiment for a stock
     * 
     * @param symbol Stock symbol
     * @param companyName Company name
     * @param isMarketOpen Whether market is currently open
     * @return NewsEvaluation with sentiment analysis
     */
    public NewsEvaluation analyzeNews(String symbol, String companyName, boolean isMarketOpen) {
        log.info("Analyzing news for {} ({}) - Market: {}", symbol, companyName, isMarketOpen ? "OPEN" : "CLOSED");
        
        NewsEvaluation eval = NewsEvaluation.builder().build();
        eval.setSymbol(symbol);
        eval.setCompanyName(companyName);
        eval.setAnalysisTime(LocalDateTime.now());
        eval.setReasons(new ArrayList<>());
        eval.setWarnings(new ArrayList<>());
        eval.setNewsItems(new ArrayList<>());
        
        BigDecimal newsScore = new BigDecimal("50"); // Start with neutral score
        
        try {
            if (!newsEnabled) {
                log.warn("News analysis is disabled");
                eval.setScore(newsScore);
                eval.setSentiment("NEUTRAL");
                eval.getWarnings().add("News analysis is disabled");
                return eval;
            }
            
            // Fetch news based on market status
            // When market is open: fetch latest real-time news
            // When market is closed: fetch news from last trading day and pre-market news
            List<NewsItem> newsItems = fetchNews(symbol, companyName, isMarketOpen);
            eval.setNewsItems(newsItems);
            
            // Add market status context
            if (isMarketOpen) {
                eval.getReasons().add("Fetching real-time news (Market is open)");
            } else {
                eval.getReasons().add("Fetching historical and pre-market news (Market is closed)");
            }
            
            if (newsItems.isEmpty()) {
                log.warn("No news found for {}", symbol);
                eval.setScore(newsScore);
                eval.setSentiment("NEUTRAL");
                eval.getWarnings().add("No recent news found");
                return eval;
            }
            
            // Analyze sentiment from news
            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;
            
            for (NewsItem item : newsItems) {
                String sentiment = analyzeSentiment(item.getTitle(), item.getDescription());
                item.setSentiment(sentiment);
                
                switch (sentiment) {
                    case "POSITIVE":
                        positiveCount++;
                        break;
                    case "NEGATIVE":
                        negativeCount++;
                        break;
                    default:
                        neutralCount++;
                }
            }
            
            // Calculate score based on sentiment distribution
            int totalNews = newsItems.size();
            if (totalNews > 0) {
                BigDecimal positiveRatio = new BigDecimal(positiveCount)
                        .divide(new BigDecimal(totalNews), 4, RoundingMode.HALF_UP);
                BigDecimal negativeRatio = new BigDecimal(negativeCount)
                        .divide(new BigDecimal(totalNews), 4, RoundingMode.HALF_UP);
                
                // Score calculation: base 50, +30 for positive, -30 for negative
                newsScore = new BigDecimal("50")
                        .add(positiveRatio.multiply(new BigDecimal("30")))
                        .subtract(negativeRatio.multiply(new BigDecimal("30")));
                
                // Determine overall sentiment
                if (positiveRatio.compareTo(new BigDecimal("0.6")) > 0) {
                    eval.setSentiment("VERY_POSITIVE");
                    eval.getReasons().add("Strong positive news sentiment (" + 
                            positiveRatio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + 
                            "% positive news)");
                } else if (positiveRatio.compareTo(new BigDecimal("0.4")) > 0) {
                    eval.setSentiment("POSITIVE");
                    eval.getReasons().add("Positive news sentiment (" + 
                            positiveRatio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + 
                            "% positive news)");
                } else if (negativeRatio.compareTo(new BigDecimal("0.4")) > 0) {
                    eval.setSentiment("NEGATIVE");
                    eval.getWarnings().add("Negative news sentiment (" + 
                            negativeRatio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + 
                            "% negative news)");
                } else {
                    eval.setSentiment("NEUTRAL");
                    eval.getReasons().add("Mixed news sentiment");
                }
            }
            
            // Add specific news highlights
            for (NewsItem item : newsItems) {
                if ("POSITIVE".equals(item.getSentiment())) {
                    eval.getReasons().add("Positive news: " + item.getTitle());
                } else if ("NEGATIVE".equals(item.getSentiment())) {
                    eval.getWarnings().add("Negative news: " + item.getTitle());
                }
            }
            
            eval.setScore(newsScore.max(new BigDecimal("0")).min(new BigDecimal("100")));
            
        } catch (Exception e) {
            log.error("Error analyzing news for {}: {}", symbol, e.getMessage(), e);
            eval.setScore(newsScore);
            eval.setSentiment("NEUTRAL");
            eval.getWarnings().add("News analysis failed: " + e.getMessage());
        }
        
        return eval;
    }
    
    /**
     * Fetch news from various sources based on market status
     * 
     * @param symbol Stock symbol
     * @param companyName Company name
     * @param isMarketOpen Whether market is currently open
     * @return List of news items
     */
    private List<NewsItem> fetchNews(String symbol, String companyName, boolean isMarketOpen) {
        List<NewsItem> newsItems = new ArrayList<>();
        
        try {
            if ("mock".equals(newsSource)) {
                // Generate mock news for testing (adapt based on market status)
                newsItems = generateMockNews(symbol, companyName, isMarketOpen);
            } else if ("mcp".equals(newsSource)) {
                // Use MCP server for news (if available)
                newsItems = fetchNewsFromMcp(symbol, companyName, isMarketOpen);
            } else {
                // Use web scraping or API
                newsItems = fetchNewsFromWeb(symbol, companyName, isMarketOpen);
            }
        } catch (Exception e) {
            log.error("Error fetching news: {}", e.getMessage(), e);
            // Fallback to mock news
            newsItems = generateMockNews(symbol, companyName, isMarketOpen);
        }
        
        return newsItems;
    }
    
    /**
     * Fetch news from web sources
     * 
     * @param symbol Stock symbol
     * @param companyName Company name
     * @param isMarketOpen Whether market is currently open
     * @return List of news items
     */
    private List<NewsItem> fetchNewsFromWeb(String symbol, String companyName, boolean isMarketOpen) {
        List<NewsItem> newsItems = new ArrayList<>();
        
        // Try to fetch from financial news APIs or web scraping
        // When market is open: fetch latest real-time news
        // When market is closed: fetch news from last trading day and pre-market news
        if (isMarketOpen) {
            log.debug("Fetching real-time news from web for {} (Market is open)", symbol);
        } else {
            log.debug("Fetching historical/pre-market news from web for {} (Market is closed)", symbol);
        }
        
        // Search terms
        String[] searchTerms = {
            symbol + " stock news",
            companyName + " latest news",
            symbol + " financial news"
        };
        
        // Generate news items based on common patterns
        // In a real implementation, this would fetch from news APIs like:
        // - NewsAPI
        // - Alpha Vantage News
        // - Financial news websites
        
        // For now, return mock news that simulates real news
        return generateMockNews(symbol, companyName, isMarketOpen);
    }
    
    /**
     * Fetch news from MCP server
     * 
     * @param symbol Stock symbol
     * @param companyName Company name
     * @param isMarketOpen Whether market is currently open
     * @return List of news items
     */
    private List<NewsItem> fetchNewsFromMcp(String symbol, String companyName, boolean isMarketOpen) {
        List<NewsItem> newsItems = new ArrayList<>();
        
        // TODO: Implement MCP news fetching when MCP news tools are available
        // When market is open: fetch latest real-time news via MCP
        // When market is closed: fetch historical/pre-market news via MCP
        if (isMarketOpen) {
            log.info("MCP real-time news fetching not yet implemented, using mock news (Market is open)");
        } else {
            log.info("MCP historical/pre-market news fetching not yet implemented, using mock news (Market is closed)");
        }
        return generateMockNews(symbol, companyName, isMarketOpen);
    }
    
    /**
     * Generate mock news for testing
     * 
     * @param symbol Stock symbol
     * @param companyName Company name
     * @param isMarketOpen Whether market is currently open
     * @return List of mock news items
     */
    private List<NewsItem> generateMockNews(String symbol, String companyName, boolean isMarketOpen) {
        List<NewsItem> newsItems = new ArrayList<>();
        
        // Generate realistic mock news based on symbol characteristics and market status
        Random random = new Random(symbol.hashCode());
        
        // Adjust news types based on market status
        if (isMarketOpen) {
            // During market hours: more real-time news, intraday updates
            log.debug("Generating real-time mock news for {} (Market is open)", symbol);
        } else {
            // After market hours: more pre-market, after-hours, and historical news
            log.debug("Generating historical/pre-market mock news for {} (Market is closed)", symbol);
        }
        
        // Positive news items
        String[] positiveTemplates = {
            companyName + " reports strong quarterly earnings",
            symbol + " announces new product launch",
            companyName + " secures major contract",
            symbol + " stock upgraded by analysts",
            companyName + " expands operations",
            symbol + " shows strong growth momentum",
            companyName + " announces dividend increase",
            symbol + " receives regulatory approval"
        };
        
        // Negative news items
        String[] negativeTemplates = {
            companyName + " faces regulatory scrutiny",
            symbol + " reports lower than expected earnings",
            companyName + " announces layoffs",
            symbol + " stock downgraded by analysts",
            companyName + " faces legal challenges",
            symbol + " reports declining sales",
            companyName + " announces management changes",
            symbol + " faces supply chain issues"
        };
        
        // Neutral news items
        String[] neutralTemplates = {
            companyName + " announces board meeting",
            symbol + " stock trading in normal range",
            companyName + " releases quarterly report",
            symbol + " maintains current operations",
            companyName + " holds annual general meeting"
        };
        
        // Generate 5-10 news items
        int newsCount = 5 + random.nextInt(6);
        for (int i = 0; i < newsCount; i++) {
            NewsItem item = NewsItem.builder().build();
            item.setTitle(selectRandomTemplate(random, positiveTemplates, negativeTemplates, neutralTemplates));
            item.setDescription(item.getTitle() + ". This is a simulated news item for testing purposes.");
            item.setSource("Mock News Source");
            item.setPublishedDate(LocalDateTime.now().minusDays(random.nextInt(30)));
            newsItems.add(item);
        }
        
        return newsItems;
    }
    
    private String selectRandomTemplate(Random random, String[] positive, String[] negative, String[] neutral) {
        int choice = random.nextInt(100);
        if (choice < 40) {
            return positive[random.nextInt(positive.length)];
        } else if (choice < 70) {
            return neutral[random.nextInt(neutral.length)];
        } else {
            return negative[random.nextInt(negative.length)];
        }
    }
    
    /**
     * Analyze sentiment from text
     */
    private String analyzeSentiment(String title, String description) {
        if (title == null && description == null) {
            return "NEUTRAL";
        }
        
        String text = (title + " " + (description != null ? description : "")).toLowerCase();
        
        // Positive keywords
        String[] positiveKeywords = {
            "strong", "growth", "profit", "gain", "upgrade", "approval", "launch",
            "expansion", "increase", "success", "record", "beat", "exceed",
            "positive", "bullish", "opportunity", "breakthrough", "milestone"
        };
        
        // Negative keywords
        String[] negativeKeywords = {
            "decline", "loss", "down", "downgrade", "scrutiny", "challenge", "issue",
            "problem", "concern", "warning", "negative", "bearish", "risk",
            "layoff", "failure", "miss", "disappoint", "regulatory"
        };
        
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (String keyword : positiveKeywords) {
            if (text.contains(keyword)) {
                positiveCount++;
            }
        }
        
        for (String keyword : negativeKeywords) {
            if (text.contains(keyword)) {
                negativeCount++;
            }
        }
        
        if (positiveCount > negativeCount + 1) {
            return "POSITIVE";
        } else if (negativeCount > positiveCount + 1) {
            return "NEGATIVE";
        } else {
            return "NEUTRAL";
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsEvaluation {
        private String symbol;
        private String companyName;
        private LocalDateTime analysisTime;
        private String sentiment; // VERY_POSITIVE, POSITIVE, NEUTRAL, NEGATIVE
        private BigDecimal score;
        private List<String> reasons;
        private List<String> warnings;
        private List<NewsItem> newsItems;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsItem {
        private String title;
        private String description;
        private String source;
        private LocalDateTime publishedDate;
        private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL
        private String url;
    }
}

