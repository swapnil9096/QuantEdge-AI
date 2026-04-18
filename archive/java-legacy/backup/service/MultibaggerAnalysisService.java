package com.trading.service;

import com.trading.model.Stock;
import com.trading.model.AnalysisResult.MultibaggerAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MultibaggerAnalysisService {

    public MultibaggerAnalysis analyzeMultibaggerPotential(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("Stock data cannot be null");
        }

        MultibaggerAnalysis.MultibaggerAnalysisBuilder builder = MultibaggerAnalysis.builder();
        List<String> factors = new ArrayList<>();

        // Calculate growth potential
        BigDecimal growthPotential = calculateGrowthPotential(stock);
        builder.growthPotential(growthPotential);

        // Calculate market cap growth potential
        BigDecimal marketCapGrowth = calculateMarketCapGrowthPotential(stock);
        builder.marketCapGrowth(marketCapGrowth);

        // Set revenue and profit growth
        builder.revenueGrowth(stock.getRevenueGrowth())
               .profitGrowth(stock.getProfitGrowth());

        // Determine growth phase
        String growthPhase = determineGrowthPhase(stock);
        builder.growthPhase(growthPhase);

        // Calculate multibagger score
        BigDecimal multibaggerScore = calculateMultibaggerScore(stock);
        builder.multibaggerScore(multibaggerScore);

        // Identify multibagger factors
        factors.addAll(identifyMultibaggerFactors(stock));
        builder.multibaggerFactors(factors);

        return builder.build();
    }

    private BigDecimal calculateGrowthPotential(Stock stock) {
        BigDecimal score = BigDecimal.ZERO;
        int factors = 0;

        // Revenue growth factor (0-30 points)
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(BigDecimal.ZERO) > 0) {
            factors++;
            if (stock.getRevenueGrowth().compareTo(new BigDecimal("30")) > 0) {
                score = score.add(new BigDecimal("30"));
            } else if (stock.getRevenueGrowth().compareTo(new BigDecimal("20")) > 0) {
                score = score.add(new BigDecimal("25"));
            } else if (stock.getRevenueGrowth().compareTo(new BigDecimal("15")) > 0) {
                score = score.add(new BigDecimal("20"));
            } else if (stock.getRevenueGrowth().compareTo(new BigDecimal("10")) > 0) {
                score = score.add(new BigDecimal("15"));
            } else {
                score = score.add(new BigDecimal("10"));
            }
        }

        // Profit growth factor (0-30 points)
        if (stock.getProfitGrowth() != null && stock.getProfitGrowth().compareTo(BigDecimal.ZERO) > 0) {
            factors++;
            if (stock.getProfitGrowth().compareTo(new BigDecimal("40")) > 0) {
                score = score.add(new BigDecimal("30"));
            } else if (stock.getProfitGrowth().compareTo(new BigDecimal("30")) > 0) {
                score = score.add(new BigDecimal("25"));
            } else if (stock.getProfitGrowth().compareTo(new BigDecimal("20")) > 0) {
                score = score.add(new BigDecimal("20"));
            } else if (stock.getProfitGrowth().compareTo(new BigDecimal("10")) > 0) {
                score = score.add(new BigDecimal("15"));
            } else {
                score = score.add(new BigDecimal("10"));
            }
        }

        // ROE factor (0-20 points)
        if (stock.getRoe() != null && stock.getRoe().compareTo(BigDecimal.ZERO) > 0) {
            factors++;
            if (stock.getRoe().compareTo(new BigDecimal("20")) > 0) {
                score = score.add(new BigDecimal("20"));
            } else if (stock.getRoe().compareTo(new BigDecimal("15")) > 0) {
                score = score.add(new BigDecimal("15"));
            } else if (stock.getRoe().compareTo(new BigDecimal("10")) > 0) {
                score = score.add(new BigDecimal("10"));
            } else {
                score = score.add(new BigDecimal("5"));
            }
        }

        // Market cap factor (0-20 points) - smaller companies have higher growth potential
        if (stock.getMarketCap() != null && stock.getMarketCap().compareTo(BigDecimal.ZERO) > 0) {
            factors++;
            if (stock.getMarketCap().compareTo(new BigDecimal("500")) < 0) {
                score = score.add(new BigDecimal("20")); // Micro cap
            } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) < 0) {
                score = score.add(new BigDecimal("15")); // Small cap
            } else if (stock.getMarketCap().compareTo(new BigDecimal("10000")) < 0) {
                score = score.add(new BigDecimal("10")); // Mid cap
            } else {
                score = score.add(new BigDecimal("5")); // Large cap
            }
        }

        if (factors == 0) {
            return BigDecimal.ZERO;
        }

        return score.divide(new BigDecimal(factors), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMarketCapGrowthPotential(Stock stock) {
        if (stock.getMarketCap() == null || stock.getMarketCap().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate potential market cap based on growth rates
        BigDecimal potentialMarketCap = stock.getMarketCap();
        
        // Apply revenue growth for 3 years
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growthMultiplier = BigDecimal.ONE.add(stock.getRevenueGrowth().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            potentialMarketCap = potentialMarketCap.multiply(growthMultiplier.pow(3));
        }

        // Calculate growth percentage
        BigDecimal growthPercentage = potentialMarketCap.subtract(stock.getMarketCap())
                                                      .divide(stock.getMarketCap(), 4, RoundingMode.HALF_UP)
                                                      .multiply(new BigDecimal("100"));

        return growthPercentage.setScale(2, RoundingMode.HALF_UP);
    }

    private String determineGrowthPhase(Stock stock) {
        if (stock.getRevenueGrowth() == null || stock.getProfitGrowth() == null) {
            return "UNKNOWN";
        }

        BigDecimal revenueGrowth = stock.getRevenueGrowth();
        BigDecimal profitGrowth = stock.getProfitGrowth();

        // Early growth phase
        if (revenueGrowth.compareTo(new BigDecimal("25")) > 0 && profitGrowth.compareTo(new BigDecimal("30")) > 0) {
            return "EARLY_GROWTH";
        }
        
        // Accelerated growth phase
        if (revenueGrowth.compareTo(new BigDecimal("15")) > 0 && profitGrowth.compareTo(new BigDecimal("20")) > 0) {
            return "ACCELERATED_GROWTH";
        }
        
        // Mature growth phase
        if (revenueGrowth.compareTo(new BigDecimal("8")) > 0 && profitGrowth.compareTo(new BigDecimal("10")) > 0) {
            return "MATURE_GROWTH";
        }
        
        // Slow growth phase
        if (revenueGrowth.compareTo(new BigDecimal("5")) > 0 && profitGrowth.compareTo(new BigDecimal("5")) > 0) {
            return "SLOW_GROWTH";
        }
        
        // Stagnant or declining
        return "STAGNANT_OR_DECLINING";
    }

    private BigDecimal calculateMultibaggerScore(Stock stock) {
        int score = 0;
        int maxScore = 0;

        // Growth consistency (0-25 points)
        maxScore += 25;
        if (stock.getRevenueGrowth() != null && stock.getProfitGrowth() != null &&
            stock.getRevenueGrowth().compareTo(new BigDecimal("15")) > 0 &&
            stock.getProfitGrowth().compareTo(new BigDecimal("20")) > 0) {
            score += 25;
        } else if (stock.getRevenueGrowth() != null && stock.getProfitGrowth() != null &&
                   stock.getRevenueGrowth().compareTo(new BigDecimal("10")) > 0 &&
                   stock.getProfitGrowth().compareTo(new BigDecimal("15")) > 0) {
            score += 20;
        } else if (stock.getRevenueGrowth() != null && stock.getProfitGrowth() != null &&
                   stock.getRevenueGrowth().compareTo(new BigDecimal("5")) > 0 &&
                   stock.getProfitGrowth().compareTo(new BigDecimal("10")) > 0) {
            score += 15;
        } else {
            score += 5;
        }

        // Profitability (0-25 points)
        maxScore += 25;
        if (stock.getRoe() != null && stock.getRoa() != null &&
            stock.getRoe().compareTo(new BigDecimal("15")) > 0 &&
            stock.getRoa() != null && stock.getRoa().compareTo(new BigDecimal("10")) > 0) {
            score += 25;
        } else if (stock.getRoe() != null && stock.getRoe().compareTo(new BigDecimal("12")) > 0) {
            score += 20;
        } else if (stock.getRoe() != null && stock.getRoe().compareTo(new BigDecimal("8")) > 0) {
            score += 15;
        } else {
            score += 5;
        }

        // Market position (0-20 points)
        maxScore += 20;
        if (stock.getMarketCap() != null) {
            if (stock.getMarketCap().compareTo(new BigDecimal("500")) < 0) {
                score += 20; // High potential for micro caps
            } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) < 0) {
                score += 15; // Good potential for small caps
            } else if (stock.getMarketCap().compareTo(new BigDecimal("10000")) < 0) {
                score += 10; // Moderate potential for mid caps
            } else {
                score += 5; // Lower potential for large caps
            }
        }

        // Financial health (0-15 points)
        maxScore += 15;
        if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.3")) < 0) {
            score += 15; // Low debt
        } else if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) < 0) {
            score += 12; // Moderate debt
        } else if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("1")) < 0) {
            score += 8; // High debt
        } else {
            score += 3; // Very high debt
        }

        // Valuation attractiveness (0-15 points)
        maxScore += 15;
        if (stock.getPeRatio() != null && stock.getPeRatio().compareTo(BigDecimal.ZERO) > 0 &&
            stock.getPeRatio().compareTo(new BigDecimal("20")) < 0 &&
            stock.getPbRatio() != null && stock.getPbRatio().compareTo(new BigDecimal("3")) < 0) {
            score += 15; // Attractive valuation
        } else if (stock.getPeRatio() != null && stock.getPeRatio().compareTo(new BigDecimal("30")) < 0) {
            score += 10; // Fair valuation
        } else {
            score += 5; // Expensive valuation
        }

        if (maxScore == 0) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(score).divide(new BigDecimal(maxScore), 4, RoundingMode.HALF_UP)
                                   .multiply(new BigDecimal("100"));
    }

    private List<String> identifyMultibaggerFactors(Stock stock) {
        List<String> factors = new ArrayList<>();

        // Growth factors
        if (stock.getRevenueGrowth() != null && stock.getRevenueGrowth().compareTo(new BigDecimal("20")) > 0) {
            factors.add("HIGH_REVENUE_GROWTH");
        }
        
        if (stock.getProfitGrowth() != null && stock.getProfitGrowth().compareTo(new BigDecimal("25")) > 0) {
            factors.add("HIGH_PROFIT_GROWTH");
        }

        // Profitability factors
        if (stock.getRoe() != null && stock.getRoe().compareTo(new BigDecimal("15")) > 0) {
            factors.add("HIGH_ROE");
        }
        
        if (stock.getRoa() != null && stock.getRoa().compareTo(new BigDecimal("10")) > 0) {
            factors.add("HIGH_ROA");
        }

        // Market cap factors
        if (stock.getMarketCap() != null) {
            if (stock.getMarketCap().compareTo(new BigDecimal("500")) < 0) {
                factors.add("MICRO_CAP_HIGH_POTENTIAL");
            } else if (stock.getMarketCap().compareTo(new BigDecimal("2000")) < 0) {
                factors.add("SMALL_CAP_GOOD_POTENTIAL");
            }
        }

        // Financial health factors
        if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.3")) < 0) {
            factors.add("LOW_DEBT_STRONG_BALANCE_SHEET");
        }

        // Valuation factors
        if (stock.getPeRatio() != null && stock.getPeRatio().compareTo(new BigDecimal("15")) < 0) {
            factors.add("UNDERVALUED_PE_RATIO");
        }
        
        if (stock.getPbRatio() != null && stock.getPbRatio().compareTo(new BigDecimal("2")) < 0) {
            factors.add("UNDERVALUED_PB_RATIO");
        }

        // Sector and industry factors
        if (stock.getSector() != null) {
            String sector = stock.getSector().toLowerCase();
            if (sector.contains("technology") || sector.contains("pharma") || 
                sector.contains("chemical") || sector.contains("auto")) {
                factors.add("GROWTH_SECTOR");
            }
        }

        return factors;
    }

    public boolean isMultibaggerCandidate(Stock stock) {
        if (stock == null) {
            return false;
        }

        // Check essential criteria for multibagger potential
        boolean hasStrongGrowth = false;
        boolean hasGoodProfitability = false;
        boolean hasReasonableValuation = false;
        boolean hasLowDebt = false;
        boolean hasGrowthPotential = false;

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
            stock.getPeRatio().compareTo(new BigDecimal("25")) < 0 &&
            stock.getPbRatio() != null && stock.getPbRatio().compareTo(new BigDecimal("4")) < 0) {
            hasReasonableValuation = true;
        }

        // Low debt criteria
        if (stock.getDebtToEquity() != null && stock.getDebtToEquity().compareTo(new BigDecimal("0.5")) < 0) {
            hasLowDebt = true;
        }

        // Market cap criteria for growth potential
        if (stock.getMarketCap() != null) {
            if (stock.getMarketCap().compareTo(new BigDecimal("10000")) < 0 && 
                stock.getMarketCap().compareTo(new BigDecimal("100")) > 0) {
                hasGrowthPotential = true;
            }
        }

        return hasStrongGrowth && hasGoodProfitability && hasReasonableValuation && 
               hasLowDebt && hasGrowthPotential;
    }
}
