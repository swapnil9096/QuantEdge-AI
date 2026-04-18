package com.trading.service;

import com.trading.model.Stock;
import com.trading.model.AnalysisResult.FundamentalAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FundamentalAnalysisService {

    public FundamentalAnalysis analyzeFundamentals(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("Stock data cannot be null");
        }

        FundamentalAnalysis.FundamentalAnalysisBuilder builder = FundamentalAnalysis.builder();
        List<String> signals = new ArrayList<>();

        // Set basic company information
        builder.sector(stock.getSector())
               .industry(stock.getIndustry())
               .marketCap(stock.getMarketCap())
               .peRatio(stock.getPeRatio())
               .pbRatio(stock.getPbRatio())
               .debtToEquity(stock.getDebtToEquity())
               .roe(stock.getRoe())
               .roa(stock.getRoa())
               .revenueGrowth(stock.getRevenueGrowth())
               .profitGrowth(stock.getProfitGrowth());

        // Analyze P/E Ratio
        signals.addAll(analyzePERatio(stock.getPeRatio()));

        // Analyze P/B Ratio
        signals.addAll(analyzePBRatio(stock.getPbRatio()));

        // Analyze Debt-to-Equity
        signals.addAll(analyzeDebtToEquity(stock.getDebtToEquity()));

        // Analyze ROE
        signals.addAll(analyzeROE(stock.getRoe()));

        // Analyze ROA
        signals.addAll(analyzeROA(stock.getRoa()));

        // Analyze Growth Rates
        signals.addAll(analyzeGrowthRates(stock.getRevenueGrowth(), stock.getProfitGrowth()));

        // Analyze Market Cap
        signals.addAll(analyzeMarketCap(stock.getMarketCap()));

        // Calculate fundamental score
        BigDecimal fundamentalScore = calculateFundamentalScore(stock);
        builder.fundamentalScore(fundamentalScore);
        builder.fundamentalSignals(signals);

        return builder.build();
    }

    private List<String> analyzePERatio(BigDecimal peRatio) {
        List<String> signals = new ArrayList<>();
        
        if (peRatio == null) {
            signals.add("PE_RATIO_UNAVAILABLE");
            return signals;
        }

        if (peRatio.compareTo(BigDecimal.ZERO) <= 0) {
            signals.add("PE_RATIO_NEGATIVE_OR_ZERO");
        } else if (peRatio.compareTo(new BigDecimal("15")) < 0) {
            signals.add("PE_RATIO_UNDERVALUED");
        } else if (peRatio.compareTo(new BigDecimal("25")) > 0) {
            signals.add("PE_RATIO_OVERVALUED");
        } else {
            signals.add("PE_RATIO_FAIR_VALUE");
        }

        return signals;
    }

    private List<String> analyzePBRatio(BigDecimal pbRatio) {
        List<String> signals = new ArrayList<>();
        
        if (pbRatio == null) {
            signals.add("PB_RATIO_UNAVAILABLE");
            return signals;
        }

        if (pbRatio.compareTo(BigDecimal.ZERO) <= 0) {
            signals.add("PB_RATIO_NEGATIVE_OR_ZERO");
        } else if (pbRatio.compareTo(new BigDecimal("1")) < 0) {
            signals.add("PB_RATIO_UNDERVALUED");
        } else if (pbRatio.compareTo(new BigDecimal("3")) > 0) {
            signals.add("PB_RATIO_OVERVALUED");
        } else {
            signals.add("PB_RATIO_FAIR_VALUE");
        }

        return signals;
    }

    private List<String> analyzeDebtToEquity(BigDecimal debtToEquity) {
        List<String> signals = new ArrayList<>();
        
        if (debtToEquity == null) {
            signals.add("DEBT_TO_EQUITY_UNAVAILABLE");
            return signals;
        }

        if (debtToEquity.compareTo(new BigDecimal("0.3")) < 0) {
            signals.add("LOW_DEBT_STRONG_FINANCIAL_POSITION");
        } else if (debtToEquity.compareTo(new BigDecimal("0.5")) < 0) {
            signals.add("MODERATE_DEBT_ACCEPTABLE");
        } else if (debtToEquity.compareTo(new BigDecimal("1")) < 0) {
            signals.add("HIGH_DEBT_CONCERN");
        } else {
            signals.add("VERY_HIGH_DEBT_RISKY");
        }

        return signals;
    }

    private List<String> analyzeROE(BigDecimal roe) {
        List<String> signals = new ArrayList<>();
        
        if (roe == null) {
            signals.add("ROE_UNAVAILABLE");
            return signals;
        }

        if (roe.compareTo(new BigDecimal("15")) > 0) {
            signals.add("HIGH_ROE_EXCELLENT_PROFITABILITY");
        } else if (roe.compareTo(new BigDecimal("10")) > 0) {
            signals.add("GOOD_ROE_STRONG_PROFITABILITY");
        } else if (roe.compareTo(new BigDecimal("5")) > 0) {
            signals.add("MODERATE_ROE_ACCEPTABLE");
        } else {
            signals.add("LOW_ROE_POOR_PROFITABILITY");
        }

        return signals;
    }

    private List<String> analyzeROA(BigDecimal roa) {
        List<String> signals = new ArrayList<>();
        
        if (roa == null) {
            signals.add("ROA_UNAVAILABLE");
            return signals;
        }

        if (roa.compareTo(new BigDecimal("10")) > 0) {
            signals.add("HIGH_ROA_EXCELLENT_EFFICIENCY");
        } else if (roa.compareTo(new BigDecimal("5")) > 0) {
            signals.add("GOOD_ROA_STRONG_EFFICIENCY");
        } else if (roa.compareTo(new BigDecimal("2")) > 0) {
            signals.add("MODERATE_ROA_ACCEPTABLE");
        } else {
            signals.add("LOW_ROA_POOR_EFFICIENCY");
        }

        return signals;
    }

    private List<String> analyzeGrowthRates(BigDecimal revenueGrowth, BigDecimal profitGrowth) {
        List<String> signals = new ArrayList<>();
        
        // Analyze Revenue Growth
        if (revenueGrowth != null) {
            if (revenueGrowth.compareTo(new BigDecimal("20")) > 0) {
                signals.add("HIGH_REVENUE_GROWTH_EXCELLENT");
            } else if (revenueGrowth.compareTo(new BigDecimal("10")) > 0) {
                signals.add("GOOD_REVENUE_GROWTH_STRONG");
            } else if (revenueGrowth.compareTo(new BigDecimal("5")) > 0) {
                signals.add("MODERATE_REVENUE_GROWTH_ACCEPTABLE");
            } else if (revenueGrowth.compareTo(BigDecimal.ZERO) > 0) {
                signals.add("LOW_REVENUE_GROWTH_WEAK");
            } else {
                signals.add("NEGATIVE_REVENUE_GROWTH_CONCERN");
            }
        } else {
            signals.add("REVENUE_GROWTH_UNAVAILABLE");
        }

        // Analyze Profit Growth
        if (profitGrowth != null) {
            if (profitGrowth.compareTo(new BigDecimal("25")) > 0) {
                signals.add("HIGH_PROFIT_GROWTH_EXCELLENT");
            } else if (profitGrowth.compareTo(new BigDecimal("15")) > 0) {
                signals.add("GOOD_PROFIT_GROWTH_STRONG");
            } else if (profitGrowth.compareTo(new BigDecimal("5")) > 0) {
                signals.add("MODERATE_PROFIT_GROWTH_ACCEPTABLE");
            } else if (profitGrowth.compareTo(BigDecimal.ZERO) > 0) {
                signals.add("LOW_PROFIT_GROWTH_WEAK");
            } else {
                signals.add("NEGATIVE_PROFIT_GROWTH_CONCERN");
            }
        } else {
            signals.add("PROFIT_GROWTH_UNAVAILABLE");
        }

        return signals;
    }

    private List<String> analyzeMarketCap(BigDecimal marketCap) {
        List<String> signals = new ArrayList<>();
        
        if (marketCap == null) {
            signals.add("MARKET_CAP_UNAVAILABLE");
            return signals;
        }

        // Market cap in crores (assuming market cap is in crores)
        if (marketCap.compareTo(new BigDecimal("10000")) > 0) {
            signals.add("LARGE_CAP_STABLE");
        } else if (marketCap.compareTo(new BigDecimal("2000")) > 0) {
            signals.add("MID_CAP_MODERATE_RISK");
        } else if (marketCap.compareTo(new BigDecimal("500")) > 0) {
            signals.add("SMALL_CAP_HIGH_RISK_HIGH_REWARD");
        } else {
            signals.add("MICRO_CAP_VERY_HIGH_RISK");
        }

        return signals;
    }

    private BigDecimal calculateFundamentalScore(Stock stock) {
        int score = 0;
        int maxScore = 0;

        // P/E Ratio Score (0-15 points)
        maxScore += 15;
        if (stock.getPeRatio() != null && stock.getPeRatio().compareTo(BigDecimal.ZERO) > 0) {
            if (stock.getPeRatio().compareTo(new BigDecimal("15")) < 0) {
                score += 15; // Undervalued
            } else if (stock.getPeRatio().compareTo(new BigDecimal("25")) < 0) {
                score += 10; // Fair value
            } else {
                score += 5; // Overvalued
            }
        }

        // P/B Ratio Score (0-15 points)
        maxScore += 15;
        if (stock.getPbRatio() != null && stock.getPbRatio().compareTo(BigDecimal.ZERO) > 0) {
            if (stock.getPbRatio().compareTo(new BigDecimal("1")) < 0) {
                score += 15; // Undervalued
            } else if (stock.getPbRatio().compareTo(new BigDecimal("3")) < 0) {
                score += 10; // Fair value
            } else {
                score += 5; // Overvalued
            }
        }

        // Debt-to-Equity Score (0-15 points)
        maxScore += 15;
        if (stock.getDebtToEquity() != null) {
            if (stock.getDebtToEquity().compareTo(new BigDecimal("0.3")) < 0) {
                score += 15; // Low debt
            } else if (stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) < 0) {
                score += 12; // Moderate debt
            } else if (stock.getDebtToEquity().compareTo(new BigDecimal("1")) < 0) {
                score += 8; // High debt
            } else {
                score += 3; // Very high debt
            }
        }

        // ROE Score (0-20 points)
        maxScore += 20;
        if (stock.getRoe() != null) {
            if (stock.getRoe().compareTo(new BigDecimal("15")) > 0) {
                score += 20; // Excellent
            } else if (stock.getRoe().compareTo(new BigDecimal("10")) > 0) {
                score += 15; // Good
            } else if (stock.getRoe().compareTo(new BigDecimal("5")) > 0) {
                score += 10; // Moderate
            } else {
                score += 5; // Poor
            }
        }

        // ROA Score (0-15 points)
        maxScore += 15;
        if (stock.getRoa() != null) {
            if (stock.getRoa().compareTo(new BigDecimal("10")) > 0) {
                score += 15; // Excellent
            } else if (stock.getRoa().compareTo(new BigDecimal("5")) > 0) {
                score += 12; // Good
            } else if (stock.getRoa().compareTo(new BigDecimal("2")) > 0) {
                score += 8; // Moderate
            } else {
                score += 3; // Poor
            }
        }

        // Growth Score (0-20 points)
        maxScore += 20;
        int growthScore = 0;
        
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(BigDecimal.ZERO) > 0) {
            if (stock.getRevenueGrowth().compareTo(new BigDecimal("20")) > 0) {
                growthScore += 10;
            } else if (stock.getRevenueGrowth().compareTo(new BigDecimal("10")) > 0) {
                growthScore += 8;
            } else if (stock.getRevenueGrowth().compareTo(new BigDecimal("5")) > 0) {
                growthScore += 5;
            }
        }
        
        if (stock.getProfitGrowth() != null && stock.getProfitGrowth().compareTo(BigDecimal.ZERO) > 0) {
            if (stock.getProfitGrowth().compareTo(new BigDecimal("25")) > 0) {
                growthScore += 10;
            } else if (stock.getProfitGrowth().compareTo(new BigDecimal("15")) > 0) {
                growthScore += 8;
            } else if (stock.getProfitGrowth().compareTo(new BigDecimal("5")) > 0) {
                growthScore += 5;
            }
        }
        
        score += growthScore;

        if (maxScore == 0) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(score).divide(new BigDecimal(maxScore), 4, RoundingMode.HALF_UP)
                                   .multiply(new BigDecimal("100"));
    }

    public boolean isMultibaggerCandidate(Stock stock) {
        if (stock == null) {
            return false;
        }

        // Check for multibagger characteristics
        boolean hasStrongGrowth = false;
        boolean hasGoodProfitability = false;
        boolean hasReasonableValuation = false;
        boolean hasLowDebt = false;

        // Strong growth criteria
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(new BigDecimal("15")) > 0 &&
            stock.getProfitGrowth() != null && stock.getProfitGrowth().compareTo(new BigDecimal("20")) > 0) {
            hasStrongGrowth = true;
        }

        // Good profitability criteria
        if (stock.getRoe() != null && stock.getRoe().compareTo(new BigDecimal("12")) > 0 &&
            stock.getRoa() != null && stock.getRoa().compareTo(new BigDecimal("8")) > 0) {
            hasGoodProfitability = true;
        }

        // Reasonable valuation criteria
        if (stock.getPeRatio() != null && stock.getPeRatio().compareTo(BigDecimal.ZERO) > 0 && 
            stock.getPeRatio().compareTo(new BigDecimal("30")) < 0 &&
            stock.getPbRatio() != null && stock.getPbRatio().compareTo(new BigDecimal("5")) < 0) {
            hasReasonableValuation = true;
        }

        // Low debt criteria
        if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) < 0) {
            hasLowDebt = true;
        }

        // Market cap criteria for multibagger potential
        boolean hasGrowthPotential = false;
        if (stock.getMarketCap() != null) {
            // Small to mid cap stocks have higher multibagger potential
            if (stock.getMarketCap().compareTo(new BigDecimal("10000")) < 0 && 
                stock.getMarketCap().compareTo(new BigDecimal("100")) > 0) {
                hasGrowthPotential = true;
            }
        }

        return hasStrongGrowth && hasGoodProfitability && hasReasonableValuation && 
               hasLowDebt && hasGrowthPotential;
    }
}
