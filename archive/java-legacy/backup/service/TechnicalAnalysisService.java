package com.trading.service;

import com.trading.model.PriceData;
import com.trading.model.AnalysisResult.TechnicalAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;

@Service
@Slf4j
public class TechnicalAnalysisService {
    
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int SMA_SHORT = 20;
    private static final int SMA_LONG = 50;
    private static final int BOLLINGER_PERIOD = 20;
    private static final double BOLLINGER_STD_DEV = 2.0;
    private static final int STOCHASTIC_K = 14;
    private static final int WILLIAMS_R_PERIOD = 14;
    private static final int ATR_PERIOD = 14;

    public TechnicalAnalysis analyzeTechnicalIndicators(List<PriceData> priceHistory) {
        if (priceHistory == null || priceHistory.size() < 50) {
            throw new IllegalArgumentException("Insufficient price data for technical analysis");
        }

        TechnicalAnalysis.TechnicalAnalysisBuilder builder = TechnicalAnalysis.builder();
        List<String> signals = new ArrayList<>();

        // Calculate RSI
        BigDecimal rsi = calculateRSI(priceHistory);
        builder.rsi(rsi);
        signals.addAll(getRSISignals(rsi));

        // Calculate MACD
        BigDecimal[] macdValues = calculateMACD(priceHistory);
        builder.macd(macdValues[0])
               .macdSignal(macdValues[1])
               .macdHistogram(macdValues[2]);
        signals.addAll(getMACDSignals(macdValues[0], macdValues[1], macdValues[2]));

        // Calculate Moving Averages
        BigDecimal sma20 = calculateSMA(priceHistory, SMA_SHORT);
        BigDecimal sma50 = calculateSMA(priceHistory, SMA_LONG);
        builder.sma20(sma20).sma50(sma50);
        signals.addAll(getMovingAverageSignals(sma20, sma50, getCurrentPrice(priceHistory)));

        // Calculate Bollinger Bands
        BigDecimal[] bollingerBands = calculateBollingerBands(priceHistory);
        builder.bollingerUpper(bollingerBands[0])
               .bollingerMiddle(bollingerBands[1])
               .bollingerLower(bollingerBands[2]);
        signals.addAll(getBollingerSignals(bollingerBands, getCurrentPrice(priceHistory)));

        // Calculate Stochastic Oscillator
        BigDecimal[] stochastic = calculateStochastic(priceHistory);
        builder.stochasticK(stochastic[0]).stochasticD(stochastic[1]);
        signals.addAll(getStochasticSignals(stochastic[0], stochastic[1]));

        // Calculate Williams %R
        BigDecimal williamsR = calculateWilliamsR(priceHistory);
        builder.williamsR(williamsR);
        signals.addAll(getWilliamsRSignals(williamsR));

        // Calculate ATR
        BigDecimal atr = calculateATR(priceHistory);
        builder.atr(atr);

        // Determine trend
        String trend = determineTrend(priceHistory);
        builder.trend(trend);

        // Identify patterns
        String pattern = identifyPatterns(priceHistory);
        builder.pattern(pattern);

        // Calculate technical score
        BigDecimal technicalScore = calculateTechnicalScore(rsi, macdValues, sma20, sma50, 
                                                          bollingerBands, stochastic, williamsR);
        builder.technicalScore(technicalScore);
        builder.technicalSignals(signals);

        return builder.build();
    }

    private BigDecimal calculateRSI(List<PriceData> prices) {
        if (prices.size() < RSI_PERIOD + 1) {
            return BigDecimal.ZERO;
        }

        double[] gains = new double[prices.size() - 1];
        double[] losses = new double[prices.size() - 1];

        for (int i = 1; i < prices.size(); i++) {
            double change = prices.get(i).getClose().subtract(prices.get(i - 1).getClose()).doubleValue();
            if (change > 0) {
                gains[i - 1] = change;
                losses[i - 1] = 0;
            } else {
                gains[i - 1] = 0;
                losses[i - 1] = Math.abs(change);
            }
        }

        double avgGain = calculateAverage(gains, RSI_PERIOD);
        double avgLoss = calculateAverage(losses, RSI_PERIOD);

        if (avgLoss == 0) {
            return new BigDecimal("100");
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        return new BigDecimal(rsi).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal[] calculateMACD(List<PriceData> prices) {
        BigDecimal ema12 = calculateEMA(prices, MACD_FAST);
        BigDecimal ema26 = calculateEMA(prices, MACD_SLOW);
        BigDecimal macd = ema12.subtract(ema26);
        
        // For signal line, we need to calculate EMA of MACD
        // This is simplified - in practice, you'd need to store MACD values
        BigDecimal macdSignal = macd.multiply(new BigDecimal("0.9")); // Simplified
        BigDecimal macdHistogram = macd.subtract(macdSignal);
        
        return new BigDecimal[]{macd, macdSignal, macdHistogram};
    }

    private BigDecimal calculateSMA(List<PriceData> prices, int period) {
        if (prices.size() < period) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i).getClose());
        }
        
        return sum.divide(new BigDecimal(period), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEMA(List<PriceData> prices, int period) {
        if (prices.size() < period) {
            return BigDecimal.ZERO;
        }

        double multiplier = 2.0 / (period + 1);
        BigDecimal ema = prices.get(prices.size() - period).getClose();

        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            BigDecimal price = prices.get(i).getClose();
            ema = price.multiply(new BigDecimal(multiplier))
                      .add(ema.multiply(new BigDecimal(1 - multiplier)));
        }

        return ema.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal[] calculateBollingerBands(List<PriceData> prices) {
        BigDecimal sma = calculateSMA(prices, BOLLINGER_PERIOD);
        BigDecimal standardDeviation = calculateStandardDeviation(prices, BOLLINGER_PERIOD);
        
        BigDecimal upperBand = sma.add(standardDeviation.multiply(new BigDecimal(BOLLINGER_STD_DEV)));
        BigDecimal lowerBand = sma.subtract(standardDeviation.multiply(new BigDecimal(BOLLINGER_STD_DEV)));
        
        return new BigDecimal[]{upperBand, sma, lowerBand};
    }

    private BigDecimal[] calculateStochastic(List<PriceData> prices) {
        if (prices.size() < STOCHASTIC_K) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }

        BigDecimal highestHigh = BigDecimal.ZERO;
        BigDecimal lowestLow = new BigDecimal("999999");
        
        for (int i = prices.size() - STOCHASTIC_K; i < prices.size(); i++) {
            PriceData price = prices.get(i);
            if (price.getHigh().compareTo(highestHigh) > 0) {
                highestHigh = price.getHigh();
            }
            if (price.getLow().compareTo(lowestLow) < 0) {
                lowestLow = price.getLow();
            }
        }

        BigDecimal currentClose = prices.get(prices.size() - 1).getClose();
        BigDecimal k = currentClose.subtract(lowestLow)
                                 .divide(highestHigh.subtract(lowestLow), 4, RoundingMode.HALF_UP)
                                 .multiply(new BigDecimal("100"));
        
        // Simplified D calculation (3-period SMA of K)
        BigDecimal d = k.multiply(new BigDecimal("0.9")); // Simplified
        
        return new BigDecimal[]{k, d};
    }

    private BigDecimal calculateWilliamsR(List<PriceData> prices) {
        if (prices.size() < WILLIAMS_R_PERIOD) {
            return BigDecimal.ZERO;
        }

        BigDecimal highestHigh = BigDecimal.ZERO;
        BigDecimal lowestLow = new BigDecimal("999999");
        
        for (int i = prices.size() - WILLIAMS_R_PERIOD; i < prices.size(); i++) {
            PriceData price = prices.get(i);
            if (price.getHigh().compareTo(highestHigh) > 0) {
                highestHigh = price.getHigh();
            }
            if (price.getLow().compareTo(lowestLow) < 0) {
                lowestLow = price.getLow();
            }
        }

        BigDecimal currentClose = prices.get(prices.size() - 1).getClose();
        return highestHigh.subtract(currentClose)
                         .divide(highestHigh.subtract(lowestLow), 4, RoundingMode.HALF_UP)
                         .multiply(new BigDecimal("-100"));
    }

    private BigDecimal calculateATR(List<PriceData> prices) {
        if (prices.size() < ATR_PERIOD + 1) {
            return BigDecimal.ZERO;
        }

        double[] trueRanges = new double[ATR_PERIOD];
        
        for (int i = prices.size() - ATR_PERIOD; i < prices.size(); i++) {
            PriceData current = prices.get(i);
            PriceData previous = prices.get(i - 1);
            
            double highLow = current.getHigh().subtract(current.getLow()).doubleValue();
            double highClose = Math.abs(current.getHigh().subtract(previous.getClose()).doubleValue());
            double lowClose = Math.abs(current.getLow().subtract(previous.getClose()).doubleValue());
            
            trueRanges[i - (prices.size() - ATR_PERIOD)] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        double atr = calculateAverage(trueRanges, ATR_PERIOD);
        return new BigDecimal(atr).setScale(2, RoundingMode.HALF_UP);
    }

    private String determineTrend(List<PriceData> prices) {
        BigDecimal sma20 = calculateSMA(prices, 20);
        BigDecimal sma50 = calculateSMA(prices, 50);
        BigDecimal currentPrice = getCurrentPrice(prices);
        
        if (currentPrice.compareTo(sma20) > 0 && sma20.compareTo(sma50) > 0) {
            return "UPTREND";
        } else if (currentPrice.compareTo(sma20) < 0 && sma20.compareTo(sma50) < 0) {
            return "DOWNTREND";
        } else {
            return "SIDEWAYS";
        }
    }

    private String identifyPatterns(List<PriceData> prices) {
        // Simplified pattern recognition
        if (prices.size() < 10) {
            return "INSUFFICIENT_DATA";
        }
        
        // Check for double bottom pattern
        if (isDoubleBottom(prices)) {
            return "DOUBLE_BOTTOM";
        }
        
        // Check for double top pattern
        if (isDoubleTop(prices)) {
            return "DOUBLE_TOP";
        }
        
        // Check for ascending triangle
        if (isAscendingTriangle(prices)) {
            return "ASCENDING_TRIANGLE";
        }
        
        return "NO_PATTERN";
    }

    private boolean isDoubleBottom(List<PriceData> prices) {
        // Simplified double bottom detection
        if (prices.size() < 20) return false;
        
        BigDecimal lowest1 = findLowest(prices, prices.size() - 20, prices.size() - 10);
        BigDecimal lowest2 = findLowest(prices, prices.size() - 10, prices.size());
        
        BigDecimal difference = lowest1.subtract(lowest2).abs();
        return difference.divide(lowest1, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.05")) < 0;
    }

    private boolean isDoubleTop(List<PriceData> prices) {
        // Simplified double top detection
        if (prices.size() < 20) return false;
        
        BigDecimal highest1 = findHighest(prices, prices.size() - 20, prices.size() - 10);
        BigDecimal highest2 = findHighest(prices, prices.size() - 10, prices.size());
        
        BigDecimal difference = highest1.subtract(highest2).abs();
        return difference.divide(highest1, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.05")) < 0;
    }

    private boolean isAscendingTriangle(List<PriceData> prices) {
        // Simplified ascending triangle detection
        if (prices.size() < 15) return false;
        
        // Check if lows are increasing while highs remain relatively flat
        BigDecimal low1 = findLowest(prices, prices.size() - 15, prices.size() - 10);
        BigDecimal low2 = findLowest(prices, prices.size() - 10, prices.size() - 5);
        BigDecimal low3 = findLowest(prices, prices.size() - 5, prices.size());
        
        return low1.compareTo(low2) < 0 && low2.compareTo(low3) < 0;
    }

    private BigDecimal findLowest(List<PriceData> prices, int start, int end) {
        BigDecimal lowest = prices.get(start).getLow();
        for (int i = start + 1; i < end && i < prices.size(); i++) {
            if (prices.get(i).getLow().compareTo(lowest) < 0) {
                lowest = prices.get(i).getLow();
            }
        }
        return lowest;
    }

    private BigDecimal findHighest(List<PriceData> prices, int start, int end) {
        BigDecimal highest = prices.get(start).getHigh();
        for (int i = start + 1; i < end && i < prices.size(); i++) {
            if (prices.get(i).getHigh().compareTo(highest) > 0) {
                highest = prices.get(i).getHigh();
            }
        }
        return highest;
    }

    private BigDecimal calculateTechnicalScore(BigDecimal rsi, BigDecimal[] macd, BigDecimal sma20, 
                                             BigDecimal sma50, BigDecimal[] bollinger, 
                                             BigDecimal[] stochastic, BigDecimal williamsR) {
        int score = 0;
        int maxScore = 0;

        // RSI Score (0-20 points)
        maxScore += 20;
        if (rsi.compareTo(new BigDecimal("30")) < 0) {
            score += 20; // Oversold - good for buying
        } else if (rsi.compareTo(new BigDecimal("70")) > 0) {
            score += 0; // Overbought - bad for buying
        } else {
            score += 10; // Neutral
        }

        // MACD Score (0-20 points)
        maxScore += 20;
        if (macd[0].compareTo(macd[1]) > 0 && macd[2].compareTo(BigDecimal.ZERO) > 0) {
            score += 20; // Bullish MACD
        } else if (macd[0].compareTo(macd[1]) < 0 && macd[2].compareTo(BigDecimal.ZERO) < 0) {
            score += 0; // Bearish MACD
        } else {
            score += 10; // Neutral
        }

        // Moving Average Score (0-20 points)
        maxScore += 20;
        if (sma20.compareTo(sma50) > 0) {
            score += 20; // Golden cross
        } else {
            score += 5; // Death cross or neutral
        }

        // Bollinger Bands Score (0-20 points)
        maxScore += 20;
        BigDecimal currentPrice = sma20; // Simplified
        if (currentPrice.compareTo(bollinger[2]) < 0) {
            score += 20; // Price near lower band - oversold
        } else if (currentPrice.compareTo(bollinger[0]) > 0) {
            score += 0; // Price near upper band - overbought
        } else {
            score += 10; // Price in middle
        }

        // Stochastic Score (0-20 points)
        maxScore += 20;
        if (stochastic[0].compareTo(new BigDecimal("20")) < 0) {
            score += 20; // Oversold
        } else if (stochastic[0].compareTo(new BigDecimal("80")) > 0) {
            score += 0; // Overbought
        } else {
            score += 10; // Neutral
        }

        return new BigDecimal(score).divide(new BigDecimal(maxScore), 4, RoundingMode.HALF_UP)
                                  .multiply(new BigDecimal("100"));
    }

    // Signal generation methods
    private List<String> getRSISignals(BigDecimal rsi) {
        List<String> signals = new ArrayList<>();
        if (rsi.compareTo(new BigDecimal("30")) < 0) {
            signals.add("RSI_OVERSOLD_BUY_SIGNAL");
        } else if (rsi.compareTo(new BigDecimal("70")) > 0) {
            signals.add("RSI_OVERBOUGHT_SELL_SIGNAL");
        }
        return signals;
    }

    private List<String> getMACDSignals(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {
        List<String> signals = new ArrayList<>();
        if (macd.compareTo(signal) > 0 && histogram.compareTo(BigDecimal.ZERO) > 0) {
            signals.add("MACD_BULLISH_CROSSOVER");
        } else if (macd.compareTo(signal) < 0 && histogram.compareTo(BigDecimal.ZERO) < 0) {
            signals.add("MACD_BEARISH_CROSSOVER");
        }
        return signals;
    }

    private List<String> getMovingAverageSignals(BigDecimal sma20, BigDecimal sma50, BigDecimal currentPrice) {
        List<String> signals = new ArrayList<>();
        if (sma20.compareTo(sma50) > 0 && currentPrice.compareTo(sma20) > 0) {
            signals.add("GOLDEN_CROSS_BULLISH");
        } else if (sma20.compareTo(sma50) < 0 && currentPrice.compareTo(sma20) < 0) {
            signals.add("DEATH_CROSS_BEARISH");
        }
        return signals;
    }

    private List<String> getBollingerSignals(BigDecimal[] bands, BigDecimal currentPrice) {
        List<String> signals = new ArrayList<>();
        if (currentPrice.compareTo(bands[2]) < 0) {
            signals.add("BOLLINGER_OVERSOLD");
        } else if (currentPrice.compareTo(bands[0]) > 0) {
            signals.add("BOLLINGER_OVERBOUGHT");
        }
        return signals;
    }

    private List<String> getStochasticSignals(BigDecimal k, BigDecimal d) {
        List<String> signals = new ArrayList<>();
        if (k.compareTo(new BigDecimal("20")) < 0 && k.compareTo(d) > 0) {
            signals.add("STOCHASTIC_OVERSOLD_BULLISH");
        } else if (k.compareTo(new BigDecimal("80")) > 0 && k.compareTo(d) < 0) {
            signals.add("STOCHASTIC_OVERBOUGHT_BEARISH");
        }
        return signals;
    }

    private List<String> getWilliamsRSignals(BigDecimal williamsR) {
        List<String> signals = new ArrayList<>();
        if (williamsR.compareTo(new BigDecimal("-80")) < 0) {
            signals.add("WILLIAMS_R_OVERSOLD");
        } else if (williamsR.compareTo(new BigDecimal("-20")) > 0) {
            signals.add("WILLIAMS_R_OVERBOUGHT");
        }
        return signals;
    }

    // Utility methods
    private double calculateAverage(double[] values, int period) {
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += values[i];
        }
        return sum / period;
    }

    private BigDecimal calculateStandardDeviation(List<PriceData> prices, int period) {
        BigDecimal sma = calculateSMA(prices, period);
        BigDecimal sum = BigDecimal.ZERO;
        
        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).getClose().subtract(sma);
            sum = sum.add(diff.multiply(diff));
        }
        
        BigDecimal variance = sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
        return new BigDecimal(Math.sqrt(variance.doubleValue())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getCurrentPrice(List<PriceData> prices) {
        return prices.get(prices.size() - 1).getClose();
    }
}
