package com.trading.service;

import com.trading.model.NseStockData;
import com.trading.service.ChartAnalysisService;
import com.trading.service.MarketStatusService;
import com.trading.service.NewsAnalysisService.NewsEvaluation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive Buy Strategy Service
 * 
 * Evaluates all fundamental metrics, technical indicators, and relevant news
 * to determine whether to buy a stock with clear explanations.
 */
@Service
@Slf4j
public class ComprehensiveBuyStrategyService {
    
    private final McpDataService mcpDataService;
    private final ChartAnalysisService chartAnalysisService;
    private final RealTimeAnalysisService realTimeAnalysisService;
    private final NewsAnalysisService newsAnalysisService;
    private final ComprehensiveNseDataService comprehensiveNseDataService;
    private final MarketStatusService marketStatusService;
    
    public ComprehensiveBuyStrategyService(
            McpDataService mcpDataService,
            ChartAnalysisService chartAnalysisService,
            RealTimeAnalysisService realTimeAnalysisService,
            NewsAnalysisService newsAnalysisService,
            ComprehensiveNseDataService comprehensiveNseDataService,
            MarketStatusService marketStatusService) {
        this.mcpDataService = mcpDataService;
        this.chartAnalysisService = chartAnalysisService;
        this.realTimeAnalysisService = realTimeAnalysisService;
        this.newsAnalysisService = newsAnalysisService;
        this.comprehensiveNseDataService = comprehensiveNseDataService;
        this.marketStatusService = marketStatusService;
    }
    
    /**
     * Comprehensive buy decision analysis for a stock
     */
    public BuyDecisionResult analyzeBuyDecision(String symbol) {
        log.info("Starting comprehensive buy analysis for symbol: {}", symbol);
        
        BuyDecisionResult result = BuyDecisionResult.builder().build();
        result.setSymbol(symbol);
        result.setAnalysisTime(LocalDateTime.now());
        result.setReasons(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        result.setStrengths(new ArrayList<>());
        
        try {
            // Check market status first
            MarketStatusService.MarketStatus marketStatus = marketStatusService.getMarketStatus();
            boolean isMarketOpen = marketStatus.isOpen();
            
            log.info("Market status for {}: {}", symbol, isMarketOpen ? "OPEN" : "CLOSED");
            if (!isMarketOpen) {
                result.getWarnings().add("Market is currently closed. Analysis based on last trading day data.");
                result.getReasons().add("Market Status: " + marketStatus.getStatusMessage());
            }
            
            // Step 1: Fetch comprehensive stock data using MCP
            // When market is open: use real-time data
            // When market is closed: use historical/last trading day data
            NseStockData stockData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            if (stockData == null) {
                throw new RuntimeException("Failed to fetch stock data for " + symbol);
            }
            
            result.setCurrentPrice(stockData.getLastPrice());
            result.setCompanyName(stockData.getCompanyName());
            result.setSector(stockData.getSector());
            result.setIndustry(stockData.getIndustry());
            
            // Step 2: Perform fundamental analysis (works with both real-time and historical data)
            FundamentalEvaluation fundamentalEval = evaluateFundamentals(stockData);
            result.setFundamentalEvaluation(fundamentalEval);
            
            // Step 3: Perform technical analysis
            // When market is open: use real-time indicators
            // When market is closed: use historical data for indicators
            TechnicalEvaluation technicalEval = evaluateTechnicalIndicators(symbol, stockData, isMarketOpen);
            result.setTechnicalEvaluation(technicalEval);
            
            // Step 4: Perform news analysis
            // When market is open: fetch latest news
            // When market is closed: fetch news from last trading day and pre-market news
            NewsEvaluation newsEval = newsAnalysisService.analyzeNews(symbol, stockData.getCompanyName(), isMarketOpen);
            result.setNewsEvaluation(newsEval);
            
            // Step 5: Calculate overall score and decision
            calculateBuyDecision(result, fundamentalEval, technicalEval, newsEval);
            
            // Step 6: Generate detailed explanation
            generateExplanation(result);
            
            log.info("Completed buy analysis for {}: Decision={}, Score={}", 
                    symbol, result.getDecision(), result.getOverallScore());
            
        } catch (Exception e) {
            log.error("Error analyzing buy decision for {}: {}", symbol, e.getMessage(), e);
            result.setDecision("ERROR");
            result.setOverallScore(BigDecimal.ZERO);
            result.setExplanation("Error during analysis: " + e.getMessage());
            result.getWarnings().add("Analysis failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Evaluate fundamental metrics
     */
    private FundamentalEvaluation evaluateFundamentals(NseStockData stockData) {
        FundamentalEvaluation eval = FundamentalEvaluation.builder().build();
        eval.setPeRatio(stockData.getPe());
        eval.setPbRatio(stockData.getPb());
        eval.setEps(stockData.getEps());
        eval.setBookValue(stockData.getBookValue());
        eval.setDividendYield(stockData.getDividendYield());
        eval.setMarketCap(stockData.getMarketCap());
        
        BigDecimal fundamentalScore = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // P/E Ratio Analysis (0-20 points)
        if (eval.getPeRatio().compareTo(BigDecimal.ZERO) > 0) {
            if (eval.getPeRatio().compareTo(new BigDecimal("15")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("20"));
                reasons.add("P/E ratio of " + eval.getPeRatio() + " indicates undervaluation (< 15)");
            } else if (eval.getPeRatio().compareTo(new BigDecimal("25")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("15"));
                reasons.add("P/E ratio of " + eval.getPeRatio() + " is reasonable (15-25)");
            } else if (eval.getPeRatio().compareTo(new BigDecimal("35")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("10"));
                warnings.add("P/E ratio of " + eval.getPeRatio() + " is high (25-35)");
            } else {
                warnings.add("P/E ratio of " + eval.getPeRatio() + " is very high (> 35), indicating overvaluation");
            }
        } else {
            warnings.add("P/E ratio not available");
        }
        
        // P/B Ratio Analysis (0-15 points)
        if (eval.getPbRatio().compareTo(BigDecimal.ZERO) > 0) {
            if (eval.getPbRatio().compareTo(new BigDecimal("1")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("15"));
                reasons.add("P/B ratio of " + eval.getPbRatio() + " indicates strong value (< 1)");
            } else if (eval.getPbRatio().compareTo(new BigDecimal("3")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("12"));
                reasons.add("P/B ratio of " + eval.getPbRatio() + " is reasonable (1-3)");
            } else if (eval.getPbRatio().compareTo(new BigDecimal("5")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("8"));
                warnings.add("P/B ratio of " + eval.getPbRatio() + " is elevated (3-5)");
            } else {
                warnings.add("P/B ratio of " + eval.getPbRatio() + " is very high (> 5)");
            }
        } else {
            warnings.add("P/B ratio not available");
        }
        
        // EPS Analysis (0-15 points)
        if (eval.getEps().compareTo(BigDecimal.ZERO) > 0) {
            if (eval.getEps().compareTo(new BigDecimal("50")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("15"));
                reasons.add("Strong EPS of " + eval.getEps() + " indicates good profitability");
            } else if (eval.getEps().compareTo(new BigDecimal("20")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("12"));
                reasons.add("EPS of " + eval.getEps() + " shows decent profitability");
            } else if (eval.getEps().compareTo(new BigDecimal("10")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("8"));
            } else {
                warnings.add("Low EPS of " + eval.getEps() + " may indicate weak profitability");
            }
        } else {
            warnings.add("EPS not available");
        }
        
        // Market Cap Analysis (0-10 points)
        if (eval.getMarketCap().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marketCapCr = eval.getMarketCap().divide(new BigDecimal("10000000"), 2, RoundingMode.HALF_UP);
            if (marketCapCr.compareTo(new BigDecimal("1000")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("10"));
                reasons.add("Large cap stock (Market Cap: " + marketCapCr + " Cr) provides stability");
            } else if (marketCapCr.compareTo(new BigDecimal("500")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("8"));
                reasons.add("Mid cap stock (Market Cap: " + marketCapCr + " Cr) offers growth potential");
            } else if (marketCapCr.compareTo(new BigDecimal("100")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("6"));
                warnings.add("Small cap stock (Market Cap: " + marketCapCr + " Cr) has higher risk");
            } else {
                warnings.add("Micro cap stock (Market Cap: " + marketCapCr + " Cr) has very high risk");
            }
        }
        
        // Dividend Yield Analysis (0-10 points)
        if (eval.getDividendYield().compareTo(BigDecimal.ZERO) > 0) {
            if (eval.getDividendYield().compareTo(new BigDecimal("3")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("10"));
                reasons.add("Attractive dividend yield of " + eval.getDividendYield() + "%");
            } else if (eval.getDividendYield().compareTo(new BigDecimal("1.5")) > 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("7"));
                reasons.add("Decent dividend yield of " + eval.getDividendYield() + "%");
            }
        }
        
        // 52-week position analysis (0-10 points)
        BigDecimal currentPrice = stockData.getLastPrice();
        BigDecimal high52Week = stockData.getHigh52Week();
        BigDecimal low52Week = stockData.getLow52Week();
        
        if (high52Week.compareTo(BigDecimal.ZERO) > 0 && low52Week.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pricePosition = currentPrice.subtract(low52Week)
                    .divide(high52Week.subtract(low52Week), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            if (pricePosition.compareTo(new BigDecimal("30")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("10"));
                reasons.add("Stock is near 52-week low (" + pricePosition.setScale(1, RoundingMode.HALF_UP) + 
                        "% from low), potential value opportunity");
            } else if (pricePosition.compareTo(new BigDecimal("70")) < 0) {
                fundamentalScore = fundamentalScore.add(new BigDecimal("7"));
                reasons.add("Stock is in middle range of 52-week high/low (" + 
                        pricePosition.setScale(1, RoundingMode.HALF_UP) + "%)");
            } else {
                warnings.add("Stock is near 52-week high (" + pricePosition.setScale(1, RoundingMode.HALF_UP) + 
                        "%), may be overbought");
            }
        }
        
        eval.setScore(fundamentalScore.min(new BigDecimal("100")));
        eval.setReasons(reasons);
        eval.setWarnings(warnings);
        
        return eval;
    }
    
    /**
     * Evaluate technical indicators
     */
    private TechnicalEvaluation evaluateTechnicalIndicators(String symbol, NseStockData stockData, boolean isMarketOpen) {
        TechnicalEvaluation eval = TechnicalEvaluation.builder().build();
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal technicalScore = BigDecimal.ZERO;
        
        // Add market status context
        if (isMarketOpen) {
            reasons.add("Using real-time technical indicators (Market is open)");
        } else {
            warnings.add("Using historical technical indicators (Market is closed)");
        }
        
        try {
            // Get real-time analysis which includes technical indicators
            RealTimeAnalysisService.RealTimeAnalysisResult realTimeAnalysis = 
                    realTimeAnalysisService.analyzeStock(symbol);
            
            if (realTimeAnalysis != null && realTimeAnalysis.getTechnicalAnalysis() != null) {
                RealTimeAnalysisService.TechnicalAnalysisResult tech = 
                        realTimeAnalysis.getTechnicalAnalysis();
                
                eval.setRsi(tech.getRsi());
                eval.setMacd(tech.getMacd());
                eval.setMacdSignal(tech.getMacdSignal());
                eval.setMacdHistogram(tech.getMacdHistogram());
                eval.setSma20(tech.getSma20());
                eval.setSma50(tech.getSma50());
                eval.setTrend(tech.getTrend());
                eval.setPattern(tech.getPattern());
                
                // RSI Analysis (0-20 points)
                if (eval.getRsi() != null) {
                    if (eval.getRsi().compareTo(new BigDecimal("30")) < 0) {
                        technicalScore = technicalScore.add(new BigDecimal("20"));
                        reasons.add("RSI of " + eval.getRsi().setScale(2, RoundingMode.HALF_UP) + 
                                " indicates oversold condition, potential buy signal");
                    } else if (eval.getRsi().compareTo(new BigDecimal("50")) < 0) {
                        technicalScore = technicalScore.add(new BigDecimal("15"));
                        reasons.add("RSI of " + eval.getRsi().setScale(2, RoundingMode.HALF_UP) + 
                                " shows bearish momentum, potential entry point");
                    } else if (eval.getRsi().compareTo(new BigDecimal("70")) < 0) {
                        technicalScore = technicalScore.add(new BigDecimal("10"));
                        reasons.add("RSI of " + eval.getRsi().setScale(2, RoundingMode.HALF_UP) + 
                                " indicates neutral momentum");
                    } else {
                        warnings.add("RSI of " + eval.getRsi().setScale(2, RoundingMode.HALF_UP) + 
                                " indicates overbought condition, wait for pullback");
                    }
                }
                
                // MACD Analysis (0-20 points)
                if (eval.getMacd() != null && eval.getMacdSignal() != null) {
                    if (eval.getMacdHistogram() != null && 
                        eval.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0) {
                        technicalScore = technicalScore.add(new BigDecimal("20"));
                        reasons.add("MACD histogram is positive, indicating bullish momentum");
                    } else if (eval.getMacd().compareTo(eval.getMacdSignal()) > 0) {
                        technicalScore = technicalScore.add(new BigDecimal("15"));
                        reasons.add("MACD above signal line, showing bullish trend");
                    } else {
                        technicalScore = technicalScore.add(new BigDecimal("5"));
                        warnings.add("MACD below signal line, bearish momentum");
                    }
                }
                
                // Moving Average Analysis (0-20 points)
                BigDecimal currentPrice = stockData.getLastPrice();
                if (eval.getSma20() != null && eval.getSma50() != null) {
                    if (currentPrice.compareTo(eval.getSma20()) > 0 && 
                        currentPrice.compareTo(eval.getSma50()) > 0 &&
                        eval.getSma20().compareTo(eval.getSma50()) > 0) {
                        technicalScore = technicalScore.add(new BigDecimal("20"));
                        reasons.add("Price above both SMA20 and SMA50 with golden cross, strong uptrend");
                    } else if (currentPrice.compareTo(eval.getSma20()) > 0) {
                        technicalScore = technicalScore.add(new BigDecimal("12"));
                        reasons.add("Price above SMA20, short-term bullish");
                    } else {
                        warnings.add("Price below SMA20, short-term bearish");
                    }
                }
                
                // Trend Analysis (0-15 points)
                if (eval.getTrend() != null) {
                    if ("UPTREND".equalsIgnoreCase(eval.getTrend())) {
                        technicalScore = technicalScore.add(new BigDecimal("15"));
                        reasons.add("Stock is in an uptrend");
                    } else if ("SIDEWAYS".equalsIgnoreCase(eval.getTrend())) {
                        technicalScore = technicalScore.add(new BigDecimal("8"));
                        reasons.add("Stock is in sideways movement");
                    } else {
                        warnings.add("Stock is in downtrend, wait for reversal");
                    }
                }
                
                // Pattern Analysis (0-15 points)
                if (eval.getPattern() != null && !eval.getPattern().isEmpty()) {
                    String pattern = eval.getPattern().toUpperCase();
                    if (pattern.contains("BULLISH") || pattern.contains("ASCENDING") || 
                        pattern.contains("CUP") || pattern.contains("MORNING")) {
                        technicalScore = technicalScore.add(new BigDecimal("15"));
                        reasons.add("Bullish chart pattern detected: " + eval.getPattern());
                    } else if (pattern.contains("BEARISH") || pattern.contains("DESCENDING") || 
                               pattern.contains("EVENING")) {
                        warnings.add("Bearish chart pattern detected: " + eval.getPattern());
                    }
                }
                
                // Volume Analysis (0-10 points)
                BigDecimal avgVolume = stockData.getTotalTradedVolume();
                if (avgVolume != null && avgVolume.compareTo(BigDecimal.ZERO) > 0) {
                    // High volume indicates interest
                    if (avgVolume.compareTo(new BigDecimal("1000000")) > 0) {
                        technicalScore = technicalScore.add(new BigDecimal("10"));
                        reasons.add("High trading volume indicates strong market interest");
                    } else {
                        technicalScore = technicalScore.add(new BigDecimal("5"));
                        warnings.add("Low trading volume may indicate lack of interest");
                    }
                }
            }
            
            // Get chart analysis for additional patterns
            try {
                com.trading.model.ChartAnalysisResult chartAnalysis = chartAnalysisService.analyzeChart(symbol);
                if (chartAnalysis != null && chartAnalysis.getAllPatternsDetected() != null) {
                    List<String> patterns = chartAnalysis.getAllPatternsDetected();
                    if (!patterns.isEmpty()) {
                        eval.setChartPatterns(patterns);
                        long bullishPatterns = patterns.stream()
                                .filter(p -> p.toUpperCase().contains("BULLISH") || 
                                           p.toUpperCase().contains("ASCENDING") ||
                                           p.toUpperCase().contains("CUP"))
                                .count();
                        if (bullishPatterns > 0) {
                            technicalScore = technicalScore.add(new BigDecimal("10"));
                            reasons.add("Multiple bullish chart patterns detected across timeframes");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch chart analysis: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error evaluating technical indicators: {}", e.getMessage(), e);
            warnings.add("Technical analysis incomplete: " + e.getMessage());
        }
        
        eval.setScore(technicalScore.min(new BigDecimal("100")));
        eval.setReasons(reasons);
        eval.setWarnings(warnings);
        
        return eval;
    }
    
    /**
     * Calculate overall buy decision
     */
    private void calculateBuyDecision(BuyDecisionResult result, 
                                     FundamentalEvaluation fundamentalEval,
                                     TechnicalEvaluation technicalEval,
                                     NewsEvaluation newsEval) {
        // Weighted scoring: Fundamentals 40%, Technicals 40%, News 20%
        BigDecimal fundamentalWeight = new BigDecimal("0.40");
        BigDecimal technicalWeight = new BigDecimal("0.40");
        BigDecimal newsWeight = new BigDecimal("0.20");
        
        BigDecimal weightedScore = fundamentalEval.getScore().multiply(fundamentalWeight)
                .add(technicalEval.getScore().multiply(technicalWeight))
                .add(newsEval.getScore().multiply(newsWeight));
        
        result.setOverallScore(weightedScore.setScale(2, RoundingMode.HALF_UP));
        result.setFundamentalScore(fundamentalEval.getScore());
        result.setTechnicalScore(technicalEval.getScore());
        result.setNewsScore(newsEval.getScore());
        
        // Decision logic
        if (weightedScore.compareTo(new BigDecimal("75")) >= 0) {
            result.setDecision("STRONG_BUY");
            result.setConfidence(new BigDecimal("90"));
        } else if (weightedScore.compareTo(new BigDecimal("65")) >= 0) {
            result.setDecision("BUY");
            result.setConfidence(new BigDecimal("75"));
        } else if (weightedScore.compareTo(new BigDecimal("55")) >= 0) {
            result.setDecision("WEAK_BUY");
            result.setConfidence(new BigDecimal("60"));
        } else if (weightedScore.compareTo(new BigDecimal("45")) >= 0) {
            result.setDecision("HOLD");
            result.setConfidence(new BigDecimal("50"));
        } else {
            result.setDecision("AVOID");
            result.setConfidence(new BigDecimal("30"));
        }
        
        // Add all reasons and warnings
        result.getReasons().addAll(fundamentalEval.getReasons());
        result.getReasons().addAll(technicalEval.getReasons());
        result.getReasons().addAll(newsEval.getReasons());
        
        result.getWarnings().addAll(fundamentalEval.getWarnings());
        result.getWarnings().addAll(technicalEval.getWarnings());
        result.getWarnings().addAll(newsEval.getWarnings());
        
        // Identify strengths
        if (fundamentalEval.getScore().compareTo(new BigDecimal("70")) >= 0) {
            result.getStrengths().add("Strong fundamental metrics");
        }
        if (technicalEval.getScore().compareTo(new BigDecimal("70")) >= 0) {
            result.getStrengths().add("Strong technical indicators");
        }
        if (newsEval.getScore().compareTo(new BigDecimal("70")) >= 0) {
            result.getStrengths().add("Positive news sentiment");
        }
    }
    
    /**
     * Generate detailed explanation
     */
    private void generateExplanation(BuyDecisionResult result) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("=== BUY DECISION ANALYSIS FOR ").append(result.getSymbol()).append(" ===\n\n");
        explanation.append("Overall Score: ").append(result.getOverallScore()).append("/100\n");
        explanation.append("Decision: ").append(result.getDecision()).append("\n");
        explanation.append("Confidence: ").append(result.getConfidence()).append("%\n\n");
        
        explanation.append("SCORE BREAKDOWN:\n");
        explanation.append("- Fundamental Score: ").append(result.getFundamentalScore()).append("/100\n");
        explanation.append("- Technical Score: ").append(result.getTechnicalScore()).append("/100\n");
        explanation.append("- News Score: ").append(result.getNewsScore()).append("/100\n\n");
        
        if (!result.getStrengths().isEmpty()) {
            explanation.append("KEY STRENGTHS:\n");
            for (String strength : result.getStrengths()) {
                explanation.append("✓ ").append(strength).append("\n");
            }
            explanation.append("\n");
        }
        
        if (!result.getReasons().isEmpty()) {
            explanation.append("REASONS FOR ").append(result.getDecision()).append(":\n");
            int index = 1;
            for (String reason : result.getReasons()) {
                explanation.append(index++).append(". ").append(reason).append("\n");
            }
            explanation.append("\n");
        }
        
        if (!result.getWarnings().isEmpty()) {
            explanation.append("WARNINGS & CONCERNS:\n");
            int index = 1;
            for (String warning : result.getWarnings()) {
                explanation.append(index++).append(". ⚠ ").append(warning).append("\n");
            }
            explanation.append("\n");
        }
        
        explanation.append("RECOMMENDATION:\n");
        if ("STRONG_BUY".equals(result.getDecision())) {
            explanation.append("This stock shows strong fundamentals, positive technical indicators, ");
            explanation.append("and favorable news sentiment. Consider buying with appropriate position sizing.\n");
        } else if ("BUY".equals(result.getDecision())) {
            explanation.append("This stock shows good potential with balanced fundamentals and technicals. ");
            explanation.append("Consider buying with moderate position sizing.\n");
        } else if ("WEAK_BUY".equals(result.getDecision())) {
            explanation.append("This stock shows some positive signals but with concerns. ");
            explanation.append("Consider a small position or wait for better entry point.\n");
        } else if ("HOLD".equals(result.getDecision())) {
            explanation.append("This stock shows mixed signals. Wait for clearer direction before buying.\n");
        } else {
            explanation.append("This stock shows multiple concerns. Avoid buying at this time.\n");
        }
        
        result.setExplanation(explanation.toString());
    }
    
    // Inner classes for evaluation results
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BuyDecisionResult {
        private String symbol;
        private String companyName;
        private String sector;
        private String industry;
        private BigDecimal currentPrice;
        private LocalDateTime analysisTime;
        private String decision; // STRONG_BUY, BUY, WEAK_BUY, HOLD, AVOID
        private BigDecimal overallScore;
        private BigDecimal fundamentalScore;
        private BigDecimal technicalScore;
        private BigDecimal newsScore;
        private BigDecimal confidence;
        private String explanation;
        private List<String> reasons;
        private List<String> warnings;
        private List<String> strengths;
        private FundamentalEvaluation fundamentalEvaluation;
        private TechnicalEvaluation technicalEvaluation;
        private NewsEvaluation newsEvaluation;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FundamentalEvaluation {
        private BigDecimal peRatio;
        private BigDecimal pbRatio;
        private BigDecimal eps;
        private BigDecimal bookValue;
        private BigDecimal dividendYield;
        private BigDecimal marketCap;
        private BigDecimal score;
        private List<String> reasons;
        private List<String> warnings;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TechnicalEvaluation {
        private BigDecimal rsi;
        private BigDecimal macd;
        private BigDecimal macdSignal;
        private BigDecimal macdHistogram;
        private BigDecimal sma20;
        private BigDecimal sma50;
        private String trend;
        private String pattern;
        private List<String> chartPatterns;
        private BigDecimal score;
        private List<String> reasons;
        private List<String> warnings;
    }
}

