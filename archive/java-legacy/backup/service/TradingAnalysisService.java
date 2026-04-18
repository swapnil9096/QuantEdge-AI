package com.trading.service;

import com.trading.model.*;
import com.trading.model.AnalysisResult.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TradingAnalysisService {

    @Autowired
    private TechnicalAnalysisService technicalAnalysisService;
    
    @Autowired
    private FundamentalAnalysisService fundamentalAnalysisService;
    
    @Autowired
    private MultibaggerAnalysisService multibaggerAnalysisService;
    
    @Autowired
    private SwingTradingStrategyService swingTradingStrategyService;

    public AnalysisResult performComprehensiveAnalysis(Stock stock, List<PriceData> priceHistory) {
        if (stock == null) {
            throw new IllegalArgumentException("Stock data cannot be null");
        }
        
        if (priceHistory == null || priceHistory.size() < 50) {
            throw new IllegalArgumentException("Insufficient price history for analysis");
        }

        log.info("Starting comprehensive analysis for stock: {}", stock.getSymbol());

        // Perform technical analysis
        TechnicalAnalysis technicalAnalysis = technicalAnalysisService.analyzeTechnicalIndicators(priceHistory);
        log.debug("Technical analysis completed for {}", stock.getSymbol());

        // Perform fundamental analysis
        FundamentalAnalysis fundamentalAnalysis = fundamentalAnalysisService.analyzeFundamentals(stock);
        log.debug("Fundamental analysis completed for {}", stock.getSymbol());

        // Perform multibagger analysis
        MultibaggerAnalysis multibaggerAnalysis = multibaggerAnalysisService.analyzeMultibaggerPotential(stock);
        log.debug("Multibagger analysis completed for {}", stock.getSymbol());

        // Generate swing trading signal
        TradingSignal tradingSignal = swingTradingStrategyService.generateSwingTradingSignal(stock, priceHistory);
        log.debug("Swing trading signal generated for {}", stock.getSymbol());

        // Calculate overall score
        BigDecimal overallScore = calculateOverallScore(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis);
        
        // Generate recommendation
        String recommendation = generateRecommendation(overallScore, tradingSignal, technicalAnalysis, fundamentalAnalysis);
        
        // Calculate confidence
        BigDecimal confidence = calculateConfidence(technicalAnalysis, fundamentalAnalysis, multibaggerAnalysis, tradingSignal);
        
        // Perform risk assessment
        RiskAssessment riskAssessment = performRiskAssessment(stock, priceHistory, technicalAnalysis, fundamentalAnalysis);

        // Build comprehensive analysis result
        AnalysisResult result = AnalysisResult.builder()
                .stockSymbol(stock.getSymbol())
                .stockName(stock.getName())
                .currentPrice(getCurrentPrice(priceHistory))
                .analysisTime(LocalDateTime.now())
                .technicalAnalysis(technicalAnalysis)
                .fundamentalAnalysis(fundamentalAnalysis)
                .multibaggerAnalysis(multibaggerAnalysis)
                .tradingSignal(tradingSignal)
                .overallScore(overallScore)
                .recommendation(recommendation)
                .confidence(confidence)
                .riskAssessment(riskAssessment)
                .build();

        log.info("Comprehensive analysis completed for stock: {} with overall score: {}", 
                stock.getSymbol(), overallScore);

        return result;
    }

    private BigDecimal calculateOverallScore(TechnicalAnalysis technicalAnalysis, 
                                           FundamentalAnalysis fundamentalAnalysis, 
                                           MultibaggerAnalysis multibaggerAnalysis) {
        BigDecimal technicalScore = technicalAnalysis.getTechnicalScore();
        BigDecimal fundamentalScore = fundamentalAnalysis.getFundamentalScore();
        BigDecimal multibaggerScore = multibaggerAnalysis.getMultibaggerScore();
        
        // Weighted scoring: 35% technical, 35% fundamental, 30% multibagger potential
        BigDecimal weightedScore = technicalScore.multiply(new BigDecimal("0.35"))
                                               .add(fundamentalScore.multiply(new BigDecimal("0.35")))
                                               .add(multibaggerScore.multiply(new BigDecimal("0.30")));
        
        return weightedScore.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateRecommendation(BigDecimal overallScore, TradingSignal tradingSignal, 
                                       TechnicalAnalysis technicalAnalysis, FundamentalAnalysis fundamentalAnalysis) {
        if (tradingSignal.getSignalType() == TradingSignal.SignalType.BUY && 
            overallScore.compareTo(new BigDecimal("75")) >= 0) {
            return "STRONG_BUY";
        } else if (tradingSignal.getSignalType() == TradingSignal.SignalType.BUY && 
                   overallScore.compareTo(new BigDecimal("65")) >= 0) {
            return "BUY";
        } else if (tradingSignal.getSignalType() == TradingSignal.SignalType.SELL && 
                   overallScore.compareTo(new BigDecimal("75")) >= 0) {
            return "STRONG_SELL";
        } else if (tradingSignal.getSignalType() == TradingSignal.SignalType.SELL && 
                   overallScore.compareTo(new BigDecimal("65")) >= 0) {
            return "SELL";
        } else if (overallScore.compareTo(new BigDecimal("50")) >= 0) {
            return "HOLD";
        } else {
            return "AVOID";
        }
    }

    private BigDecimal calculateConfidence(TechnicalAnalysis technicalAnalysis, 
                                         FundamentalAnalysis fundamentalAnalysis, 
                                         MultibaggerAnalysis multibaggerAnalysis,
                                         TradingSignal tradingSignal) {
        // Base confidence from trading signal
        BigDecimal baseConfidence = tradingSignal.getConfidence();
        
        // Adjust confidence based on consistency across analyses
        int consistentAnalyses = 0;
        if (technicalAnalysis.getTechnicalScore().compareTo(new BigDecimal("70")) > 0) consistentAnalyses++;
        if (fundamentalAnalysis.getFundamentalScore().compareTo(new BigDecimal("70")) > 0) consistentAnalyses++;
        if (multibaggerAnalysis.getMultibaggerScore().compareTo(new BigDecimal("70")) > 0) consistentAnalyses++;
        
        // Consistency bonus
        BigDecimal consistencyBonus = new BigDecimal(consistentAnalyses).multiply(new BigDecimal("5"));
        
        // Signal strength bonus
        BigDecimal strengthBonus = BigDecimal.ZERO;
        switch (tradingSignal.getStrength()) {
            case VERY_STRONG:
                strengthBonus = new BigDecimal("10");
                break;
            case STRONG:
                strengthBonus = new BigDecimal("5");
                break;
            case MODERATE:
                strengthBonus = new BigDecimal("2");
                break;
            case WEAK:
                strengthBonus = BigDecimal.ZERO;
                break;
        }
        
        BigDecimal finalConfidence = baseConfidence.add(consistencyBonus).add(strengthBonus);
        return finalConfidence.min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private RiskAssessment performRiskAssessment(Stock stock, List<PriceData> priceHistory, 
                                               TechnicalAnalysis technicalAnalysis, 
                                               FundamentalAnalysis fundamentalAnalysis) {
        // Calculate volatility
        BigDecimal volatility = calculateVolatility(priceHistory);
        
        // Calculate beta (simplified - would need market data in real implementation)
        BigDecimal beta = calculateBeta(stock, priceHistory);
        
        // Determine risk level
        String riskLevel = determineRiskLevel(volatility, beta, stock);
        
        // Identify risk factors
        List<String> riskFactors = identifyRiskFactors(stock, volatility, beta, technicalAnalysis, fundamentalAnalysis);
        
        // Calculate risk score
        BigDecimal riskScore = calculateRiskScore(volatility, beta, stock, technicalAnalysis, fundamentalAnalysis);
        
        return RiskAssessment.builder()
                .volatility(volatility)
                .beta(beta)
                .riskLevel(riskLevel)
                .riskFactors(riskFactors)
                .riskScore(riskScore)
                .build();
    }

    private BigDecimal calculateVolatility(List<PriceData> priceHistory) {
        if (priceHistory == null || priceHistory.size() < 20) {
            return BigDecimal.ZERO;
        }
        
        // Calculate daily returns
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < priceHistory.size(); i++) {
            BigDecimal currentPrice = priceHistory.get(i).getClose();
            BigDecimal previousPrice = priceHistory.get(i - 1).getClose();
            BigDecimal returnValue = currentPrice.subtract(previousPrice).divide(previousPrice, 4, RoundingMode.HALF_UP);
            returns.add(returnValue);
        }
        
        // Calculate mean return
        BigDecimal meanReturn = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(returns.size()), 4, RoundingMode.HALF_UP);
        
        // Calculate variance
        BigDecimal variance = returns.stream()
                .map(r -> r.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(returns.size()), 4, RoundingMode.HALF_UP);
        
        // Calculate standard deviation (volatility)
        double volatilityDouble = Math.sqrt(variance.doubleValue());
        return new BigDecimal(volatilityDouble).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBeta(Stock stock, List<PriceData> priceHistory) {
        // Simplified beta calculation - in real implementation, you'd need market index data
        // For now, return a default beta based on market cap
        if (stock.getMarketCap() == null) {
            return new BigDecimal("1.0");
        }
        
        if (stock.getMarketCap().compareTo(new BigDecimal("10000")) > 0) {
            return new BigDecimal("0.8"); // Large cap - lower beta
        } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) > 0) {
            return new BigDecimal("1.2"); // Mid cap - moderate beta
        } else {
            return new BigDecimal("1.5"); // Small cap - higher beta
        }
    }

    private String determineRiskLevel(BigDecimal volatility, BigDecimal beta, Stock stock) {
        int riskScore = 0;
        
        // Volatility risk
        if (volatility.compareTo(new BigDecimal("30")) > 0) {
            riskScore += 3; // High volatility
        } else if (volatility.compareTo(new BigDecimal("20")) > 0) {
            riskScore += 2; // Medium volatility
        } else {
            riskScore += 1; // Low volatility
        }
        
        // Beta risk
        if (beta.compareTo(new BigDecimal("1.5")) > 0) {
            riskScore += 3; // High beta
        } else if (beta.compareTo(new BigDecimal("1.0")) > 0) {
            riskScore += 2; // Moderate beta
        } else {
            riskScore += 1; // Low beta
        }
        
        // Market cap risk
        if (stock.getMarketCap() != null) {
            if (stock.getMarketCap().compareTo(new BigDecimal("500")) < 0) {
                riskScore += 3; // Micro cap
            } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) < 0) {
                riskScore += 2; // Small cap
            } else {
                riskScore += 1; // Mid/Large cap
            }
        }
        
        if (riskScore >= 7) {
            return "HIGH";
        } else if (riskScore >= 5) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private List<String> identifyRiskFactors(Stock stock, BigDecimal volatility, BigDecimal beta, 
                                           TechnicalAnalysis technicalAnalysis, FundamentalAnalysis fundamentalAnalysis) {
        List<String> riskFactors = new ArrayList<>();
        
        // Volatility risk
        if (volatility.compareTo(new BigDecimal("25")) > 0) {
            riskFactors.add("HIGH_VOLATILITY");
        }
        
        // Beta risk
        if (beta.compareTo(new BigDecimal("1.3")) > 0) {
            riskFactors.add("HIGH_BETA");
        }
        
        // Market cap risk
        if (stock.getMarketCap() != null && stock.getMarketCap().compareTo(new BigDecimal("1000")) < 0) {
            riskFactors.add("SMALL_CAP_RISK");
        }
        
        // Debt risk
        if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) > 0) {
            riskFactors.add("HIGH_DEBT");
        }
        
        // Technical risk
        if (technicalAnalysis.getTechnicalScore().compareTo(new BigDecimal("50")) < 0) {
            riskFactors.add("WEAK_TECHNICALS");
        }
        
        // Fundamental risk
        if (fundamentalAnalysis.getFundamentalScore().compareTo(new BigDecimal("50")) < 0) {
            riskFactors.add("WEAK_FUNDAMENTALS");
        }
        
        // Growth risk
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(BigDecimal.ZERO) < 0) {
            riskFactors.add("NEGATIVE_GROWTH");
        }
        
        return riskFactors;
    }

    private BigDecimal calculateRiskScore(BigDecimal volatility, BigDecimal beta, Stock stock, 
                                       TechnicalAnalysis technicalAnalysis, FundamentalAnalysis fundamentalAnalysis) {
        int riskScore = 0;
        
        // Volatility component (0-30 points)
        if (volatility.compareTo(new BigDecimal("30")) > 0) {
            riskScore += 30;
        } else if (volatility.compareTo(new BigDecimal("20")) > 0) {
            riskScore += 20;
        } else {
            riskScore += 10;
        }
        
        // Beta component (0-25 points)
        if (beta.compareTo(new BigDecimal("1.5")) > 0) {
            riskScore += 25;
        } else if (beta.compareTo(new BigDecimal("1.0")) > 0) {
            riskScore += 15;
        } else {
            riskScore += 5;
        }
        
        // Market cap component (0-20 points)
        if (stock.getMarketCap() != null) {
            if (stock.getMarketCap().compareTo(new BigDecimal("500")) < 0) {
                riskScore += 20;
            } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) < 0) {
                riskScore += 15;
            } else {
                riskScore += 5;
            }
        }
        
        // Debt component (0-15 points)
        if (stock.getDebtToEquity() != null) {
            if (stock.getDebtToEquity().compareTo(new BigDecimal("1")) > 0) {
                riskScore += 15;
            } else if (stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) > 0) {
                riskScore += 10;
            } else {
                riskScore += 5;
            }
        }
        
        // Analysis quality component (0-10 points)
        if (technicalAnalysis.getTechnicalScore().compareTo(new BigDecimal("50")) < 0 ||
            fundamentalAnalysis.getFundamentalScore().compareTo(new BigDecimal("50")) < 0) {
            riskScore += 10;
        } else {
            riskScore += 5;
        }
        
        return new BigDecimal(riskScore).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getCurrentPrice(List<PriceData> priceHistory) {
        if (priceHistory == null || priceHistory.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return priceHistory.get(priceHistory.size() - 1).getClose();
    }

    public boolean isMultibaggerCandidate(Stock stock) {
        return multibaggerAnalysisService.isMultibaggerCandidate(stock);
    }

    public boolean isSwingTradingCandidate(Stock stock, List<PriceData> priceHistory) {
        if (stock == null || priceHistory == null || priceHistory.size() < 50) {
            return false;
        }
        
        AnalysisResult analysis = performComprehensiveAnalysis(stock, priceHistory);
        
        // Check if it meets swing trading criteria
        return analysis.getOverallScore().compareTo(new BigDecimal("70")) >= 0 &&
               analysis.getConfidence().compareTo(new BigDecimal("80")) >= 0 &&
               analysis.getTradingSignal().getExpectedReturn().compareTo(new BigDecimal("2")) >= 0;
    }
}
