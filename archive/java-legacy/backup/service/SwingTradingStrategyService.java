package com.trading.service;

import com.trading.model.*;
import com.trading.model.AnalysisResult.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class SwingTradingStrategyService {

    @Autowired
    private TechnicalAnalysisService technicalAnalysisService;
    
    @Autowired
    private FundamentalAnalysisService fundamentalAnalysisService;
    
    @Autowired
    private MultibaggerAnalysisService multibaggerAnalysisService;

    @Value("${trading.strategy.min-return-percentage:2.0}")
    private BigDecimal minReturnPercentage;
    
    @Value("${trading.strategy.max-holding-days:2}")
    private int maxHoldingDays;
    
    @Value("${trading.strategy.accuracy-threshold:95.0}")
    private BigDecimal accuracyThreshold;

    public TradingSignal generateSwingTradingSignal(Stock stock, List<PriceData> priceHistory) {
        if (stock == null || priceHistory == null || priceHistory.size() < 50) {
            throw new IllegalArgumentException("Insufficient data for swing trading analysis");
        }

        // Perform comprehensive analysis
        TechnicalAnalysis technicalAnalysis = technicalAnalysisService.analyzeTechnicalIndicators(priceHistory);
        FundamentalAnalysis fundamentalAnalysis = fundamentalAnalysisService.analyzeFundamentals(stock);
        MultibaggerAnalysis multibaggerAnalysis = multibaggerAnalysisService.analyzeMultibaggerPotential(stock);

        // Generate trading signal based on analysis
        return createTradingSignal(stock, technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis, priceHistory);
    }

    private TradingSignal createTradingSignal(Stock stock, TechnicalAnalysis technicalAnalysis, 
                                            FundamentalAnalysis fundamentalAnalysis, 
                                            MultibaggerAnalysis multibaggerAnalysis,
                                            List<PriceData> priceHistory) {
        
        BigDecimal currentPrice = getCurrentPrice(priceHistory);
        BigDecimal overallScore = calculateOverallScore(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis);
        
        // Determine signal type and strength
        TradingSignal.SignalType signalType = determineSignalType(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis);
        TradingSignal.SignalStrength strength = determineSignalStrength(overallScore, technicalAnalysis, fundamentalAnalysis);
        
        // Calculate entry, target, and stop loss prices
        BigDecimal entryPrice = currentPrice;
        BigDecimal targetPrice = calculateTargetPrice(currentPrice, signalType);
        BigDecimal stopLoss = calculateStopLoss(currentPrice, signalType);
        BigDecimal expectedReturn = calculateExpectedReturn(entryPrice, targetPrice);
        
        // Calculate confidence based on multiple factors
        BigDecimal confidence = calculateConfidence(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis);
        
        // Generate reasoning
        String reasoning = generateReasoning(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis, signalType);
        
        // Generate technical analysis summary
        String technicalSummary = generateTechnicalSummary(technicalAnalysis);
        
        // Generate fundamental analysis summary
        String fundamentalSummary = generateFundamentalSummary(fundamentalAnalysis);
        
        // Check if signal meets minimum return criteria
        boolean meetsReturnCriteria = expectedReturn.compareTo(minReturnPercentage) >= 0;
        
        // Check if signal meets accuracy criteria
        boolean meetsAccuracyCriteria = confidence.compareTo(accuracyThreshold) >= 0;
        
        // Only generate signal if it meets both criteria
        if (!meetsReturnCriteria || !meetsAccuracyCriteria) {
            return TradingSignal.builder()
                    .stock(stock)
                    .signalType(TradingSignal.SignalType.HOLD)
                    .strength(TradingSignal.SignalStrength.WEAK)
                    .entryPrice(entryPrice)
                    .targetPrice(targetPrice)
                    .stopLoss(stopLoss)
                    .expectedReturn(expectedReturn)
                    .confidence(confidence)
                    .signalTime(LocalDateTime.now())
                    .expiryTime(LocalDateTime.now().plusDays(maxHoldingDays))
                    .reasoning("Signal does not meet minimum return or accuracy criteria")
                    .technicalAnalysis(technicalSummary)
                    .fundamentalAnalysis(fundamentalSummary)
                    .isActive(false)
                    .isExecuted(false)
                    .build();
        }

        return TradingSignal.builder()
                .stock(stock)
                .signalType(signalType)
                .strength(strength)
                .entryPrice(entryPrice)
                .targetPrice(targetPrice)
                .stopLoss(stopLoss)
                .expectedReturn(expectedReturn)
                .confidence(confidence)
                .signalTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusDays(maxHoldingDays))
                .reasoning(reasoning)
                .technicalAnalysis(technicalSummary)
                .fundamentalAnalysis(fundamentalSummary)
                .isActive(true)
                .isExecuted(false)
                .build();
    }

    private BigDecimal calculateOverallScore(TechnicalAnalysis technicalAnalysis, 
                                           FundamentalAnalysis fundamentalAnalysis, 
                                           MultibaggerAnalysis multibaggerAnalysis) {
        BigDecimal technicalScore = technicalAnalysis.getTechnicalScore();
        BigDecimal fundamentalScore = fundamentalAnalysis.getFundamentalScore();
        BigDecimal multibaggerScore = multibaggerAnalysis.getMultibaggerScore();
        
        // Weighted average: 40% technical, 40% fundamental, 20% multibagger
        BigDecimal weightedScore = technicalScore.multiply(new BigDecimal("0.4"))
                                               .add(fundamentalScore.multiply(new BigDecimal("0.4")))
                                               .add(multibaggerScore.multiply(new BigDecimal("0.2")));
        
        return weightedScore.setScale(2, RoundingMode.HALF_UP);
    }

    private TradingSignal.SignalType determineSignalType(TechnicalAnalysis technicalAnalysis, 
                                                        FundamentalAnalysis fundamentalAnalysis, 
                                                        MultibaggerAnalysis multibaggerAnalysis) {
        int buySignals = 0;
        int sellSignals = 0;
        
        // Technical analysis signals
        if (technicalAnalysis.getRsi() != null && technicalAnalysis.getRsi().compareTo(new BigDecimal("30")) < 0) {
            buySignals++;
        } else if (technicalAnalysis.getRsi() != null && technicalAnalysis.getRsi().compareTo(new BigDecimal("70")) > 0) {
            sellSignals++;
        }
        
        if (technicalAnalysis.getMacd() != null && technicalAnalysis.getMacdSignal() != null &&
            technicalAnalysis.getMacd().compareTo(technicalAnalysis.getMacdSignal()) > 0) {
            buySignals++;
        } else if (technicalAnalysis.getMacd() != null && technicalAnalysis.getMacdSignal() != null &&
                   technicalAnalysis.getMacd().compareTo(technicalAnalysis.getMacdSignal()) < 0) {
            sellSignals++;
        }
        
        if ("UPTREND".equals(technicalAnalysis.getTrend())) {
            buySignals++;
        } else if ("DOWNTREND".equals(technicalAnalysis.getTrend())) {
            sellSignals++;
        }
        
        // Fundamental analysis signals
        if (fundamentalAnalysis.getPeRatio() != null && fundamentalAnalysis.getPeRatio().compareTo(new BigDecimal("15")) < 0) {
            buySignals++;
        } else if (fundamentalAnalysis.getPeRatio() != null && fundamentalAnalysis.getPeRatio().compareTo(new BigDecimal("25")) > 0) {
            sellSignals++;
        }
        
        if (fundamentalAnalysis.getRoe() != null && fundamentalAnalysis.getRoe().compareTo(new BigDecimal("15")) > 0) {
            buySignals++;
        }
        
        if (fundamentalAnalysis.getRevenueGrowth() != null && fundamentalAnalysis.getRevenueGrowth().compareTo(new BigDecimal("15")) > 0) {
            buySignals++;
        }
        
        // Multibagger analysis signals
        if (multibaggerAnalysis.getMultibaggerScore() != null && multibaggerAnalysis.getMultibaggerScore().compareTo(new BigDecimal("70")) > 0) {
            buySignals++;
        }
        
        if (buySignals > sellSignals && buySignals >= 3) {
            return TradingSignal.SignalType.BUY;
        } else if (sellSignals > buySignals && sellSignals >= 3) {
            return TradingSignal.SignalType.SELL;
        } else {
            return TradingSignal.SignalType.HOLD;
        }
    }

    private TradingSignal.SignalStrength determineSignalStrength(BigDecimal overallScore, 
                                                               TechnicalAnalysis technicalAnalysis, 
                                                               FundamentalAnalysis fundamentalAnalysis) {
        if (overallScore.compareTo(new BigDecimal("80")) >= 0) {
            return TradingSignal.SignalStrength.VERY_STRONG;
        } else if (overallScore.compareTo(new BigDecimal("70")) >= 0) {
            return TradingSignal.SignalStrength.STRONG;
        } else if (overallScore.compareTo(new BigDecimal("60")) >= 0) {
            return TradingSignal.SignalStrength.MODERATE;
        } else {
            return TradingSignal.SignalStrength.WEAK;
        }
    }

    private BigDecimal calculateTargetPrice(BigDecimal currentPrice, TradingSignal.SignalType signalType) {
        if (signalType == TradingSignal.SignalType.BUY) {
            // Target 3-5% gain for swing trading
            return currentPrice.multiply(new BigDecimal("1.04")); // 4% target
        } else if (signalType == TradingSignal.SignalType.SELL) {
            // Target 3-5% decline for short selling
            return currentPrice.multiply(new BigDecimal("0.96")); // 4% decline target
        } else {
            return currentPrice;
        }
    }

    private BigDecimal calculateStopLoss(BigDecimal currentPrice, TradingSignal.SignalType signalType) {
        if (signalType == TradingSignal.SignalType.BUY) {
            // 2% stop loss for long positions
            return currentPrice.multiply(new BigDecimal("0.98"));
        } else if (signalType == TradingSignal.SignalType.SELL) {
            // 2% stop loss for short positions
            return currentPrice.multiply(new BigDecimal("1.02"));
        } else {
            return currentPrice;
        }
    }

    private BigDecimal calculateExpectedReturn(BigDecimal entryPrice, BigDecimal targetPrice) {
        if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal returnValue = targetPrice.subtract(entryPrice)
                                          .divide(entryPrice, 4, RoundingMode.HALF_UP)
                                          .multiply(new BigDecimal("100"));
        
        return returnValue.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateConfidence(TechnicalAnalysis technicalAnalysis, 
                                         FundamentalAnalysis fundamentalAnalysis, 
                                         MultibaggerAnalysis multibaggerAnalysis) {
        BigDecimal technicalScore = technicalAnalysis.getTechnicalScore();
        BigDecimal fundamentalScore = fundamentalAnalysis.getFundamentalScore();
        BigDecimal multibaggerScore = multibaggerAnalysis.getMultibaggerScore();
        
        // Calculate confidence based on consistency of scores
        BigDecimal avgScore = technicalScore.add(fundamentalScore).add(multibaggerScore)
                                          .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        
        // Adjust confidence based on signal consistency
        int consistentSignals = 0;
        if (technicalScore.compareTo(new BigDecimal("60")) > 0) consistentSignals++;
        if (fundamentalScore.compareTo(new BigDecimal("60")) > 0) consistentSignals++;
        if (multibaggerScore.compareTo(new BigDecimal("60")) > 0) consistentSignals++;
        
        BigDecimal consistencyBonus = new BigDecimal(consistentSignals).multiply(new BigDecimal("5"));
        BigDecimal finalConfidence = avgScore.add(consistencyBonus);
        
        return finalConfidence.min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateReasoning(TechnicalAnalysis technicalAnalysis, 
                                   FundamentalAnalysis fundamentalAnalysis, 
                                   MultibaggerAnalysis multibaggerAnalysis,
                                   TradingSignal.SignalType signalType) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Swing Trading Signal Analysis:\n");
        reasoning.append("Signal Type: ").append(signalType).append("\n");
        reasoning.append("Technical Score: ").append(technicalAnalysis.getTechnicalScore()).append("%\n");
        reasoning.append("Fundamental Score: ").append(fundamentalAnalysis.getFundamentalScore()).append("%\n");
        reasoning.append("Multibagger Score: ").append(multibaggerAnalysis.getMultibaggerScore()).append("%\n\n");
        
        // Technical reasoning
        reasoning.append("Technical Analysis:\n");
        if (technicalAnalysis.getRsi() != null) {
            reasoning.append("- RSI: ").append(technicalAnalysis.getRsi()).append(" (");
            if (technicalAnalysis.getRsi().compareTo(new BigDecimal("30")) < 0) {
                reasoning.append("Oversold - Bullish Signal");
            } else if (technicalAnalysis.getRsi().compareTo(new BigDecimal("70")) > 0) {
                reasoning.append("Overbought - Bearish Signal");
            } else {
                reasoning.append("Neutral");
            }
            reasoning.append(")\n");
        }
        
        if (technicalAnalysis.getTrend() != null) {
            reasoning.append("- Trend: ").append(technicalAnalysis.getTrend()).append("\n");
        }
        
        if (technicalAnalysis.getPattern() != null && !"NO_PATTERN".equals(technicalAnalysis.getPattern())) {
            reasoning.append("- Pattern: ").append(technicalAnalysis.getPattern()).append("\n");
        }
        
        // Fundamental reasoning
        reasoning.append("\nFundamental Analysis:\n");
        if (fundamentalAnalysis.getPeRatio() != null) {
            reasoning.append("- P/E Ratio: ").append(fundamentalAnalysis.getPeRatio()).append(" (");
            if (fundamentalAnalysis.getPeRatio().compareTo(new BigDecimal("15")) < 0) {
                reasoning.append("Undervalued");
            } else if (fundamentalAnalysis.getPeRatio().compareTo(new BigDecimal("25")) > 0) {
                reasoning.append("Overvalued");
            } else {
                reasoning.append("Fair Value");
            }
            reasoning.append(")\n");
        }
        
        if (fundamentalAnalysis.getRoe() != null) {
            reasoning.append("- ROE: ").append(fundamentalAnalysis.getRoe()).append("%\n");
        }
        
        if (fundamentalAnalysis.getRevenueGrowth() != null) {
            reasoning.append("- Revenue Growth: ").append(fundamentalAnalysis.getRevenueGrowth()).append("%\n");
        }
        
        // Multibagger reasoning
        reasoning.append("\nMultibagger Potential:\n");
        reasoning.append("- Growth Phase: ").append(multibaggerAnalysis.getGrowthPhase()).append("\n");
        reasoning.append("- Growth Potential: ").append(multibaggerAnalysis.getGrowthPotential()).append("%\n");
        
        return reasoning.toString();
    }

    private String generateTechnicalSummary(TechnicalAnalysis technicalAnalysis) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Technical Indicators Summary:\n");
        summary.append("RSI: ").append(technicalAnalysis.getRsi()).append("\n");
        summary.append("MACD: ").append(technicalAnalysis.getMacd()).append("\n");
        summary.append("SMA 20: ").append(technicalAnalysis.getSma20()).append("\n");
        summary.append("SMA 50: ").append(technicalAnalysis.getSma50()).append("\n");
        summary.append("Trend: ").append(technicalAnalysis.getTrend()).append("\n");
        summary.append("Pattern: ").append(technicalAnalysis.getPattern()).append("\n");
        summary.append("Technical Score: ").append(technicalAnalysis.getTechnicalScore()).append("%\n");
        
        if (technicalAnalysis.getTechnicalSignals() != null && !technicalAnalysis.getTechnicalSignals().isEmpty()) {
            summary.append("Key Signals: ").append(String.join(", ", technicalAnalysis.getTechnicalSignals()));
        }
        
        return summary.toString();
    }

    private String generateFundamentalSummary(FundamentalAnalysis fundamentalAnalysis) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Fundamental Analysis Summary:\n");
        summary.append("P/E Ratio: ").append(fundamentalAnalysis.getPeRatio()).append("\n");
        summary.append("P/B Ratio: ").append(fundamentalAnalysis.getPbRatio()).append("\n");
        summary.append("ROE: ").append(fundamentalAnalysis.getRoe()).append("%\n");
        summary.append("ROA: ").append(fundamentalAnalysis.getRoa()).append("%\n");
        summary.append("Revenue Growth: ").append(fundamentalAnalysis.getRevenueGrowth()).append("%\n");
        summary.append("Profit Growth: ").append(fundamentalAnalysis.getProfitGrowth()).append("%\n");
        summary.append("Debt-to-Equity: ").append(fundamentalAnalysis.getDebtToEquity()).append("\n");
        summary.append("Market Cap: ").append(fundamentalAnalysis.getMarketCap()).append(" Cr\n");
        summary.append("Sector: ").append(fundamentalAnalysis.getSector()).append("\n");
        summary.append("Fundamental Score: ").append(fundamentalAnalysis.getFundamentalScore()).append("%\n");
        
        if (fundamentalAnalysis.getFundamentalSignals() != null && !fundamentalAnalysis.getFundamentalSignals().isEmpty()) {
            summary.append("Key Signals: ").append(String.join(", ", fundamentalAnalysis.getFundamentalSignals()));
        }
        
        return summary.toString();
    }

    private BigDecimal getCurrentPrice(List<PriceData> priceHistory) {
        if (priceHistory == null || priceHistory.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return priceHistory.get(priceHistory.size() - 1).getClose();
    }

    public boolean validateSignal(TradingSignal signal) {
        if (signal == null) {
            return false;
        }
        
        // Check if signal meets minimum return criteria
        if (signal.getExpectedReturn() == null || signal.getExpectedReturn().compareTo(minReturnPercentage) < 0) {
            return false;
        }
        
        // Check if signal meets accuracy criteria
        if (signal.getConfidence() == null || signal.getConfidence().compareTo(accuracyThreshold) < 0) {
            return false;
        }
        
        // Check if signal is still active
        if (!signal.isActive()) {
            return false;
        }
        
        // Check if signal has not expired
        if (signal.getExpiryTime() != null && signal.getExpiryTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        
        return true;
    }
}
