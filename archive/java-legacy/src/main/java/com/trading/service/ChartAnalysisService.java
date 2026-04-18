package com.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for comprehensive chart analysis across multiple timeframes
 * 
 * Detects 30+ chart patterns including:
 * - Gap Patterns: Fair Value Gaps
 * - Order Blocks: Bullish/Bearish Order Blocks
 * - Engulfing Patterns: Bullish/Bearish Engulfing (daily/weekly only)
 * - Single Candlestick: Hammer, Shooting Star, Hanging Man, Inverted Hammer, Doji
 * - Two-Candle: Harami, Piercing Pattern, Dark Cloud Cover
 * - Three-Candle: Morning Star, Evening Star, Three White Soldiers, Three Black Crows
 * - Reversal Patterns: W Pattern, M Pattern, Head & Shoulders, Inverse Head & Shoulders
 * - Continuation Patterns: Triangles, Flags, Pennants, Wedges
 * - Other Patterns: Cup and Handle, Rounding Bottom/Top
 * - Trend Indicators: Above/Below 200 EMA
 */
@Service
public class ChartAnalysisService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NseSessionManager sessionManager;
    private final ComprehensiveNseDataService comprehensiveNseDataService;
    private final RealTimeNseDataService realTimeNseDataService;
    
    @Value("${api.nse.enabled:true}")
    private boolean nseEnabled;
    
    public ChartAnalysisService(NseSessionManager sessionManager, 
                               ComprehensiveNseDataService comprehensiveNseDataService,
                               RealTimeNseDataService realTimeNseDataService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.sessionManager = sessionManager;
        this.comprehensiveNseDataService = comprehensiveNseDataService;
        this.realTimeNseDataService = realTimeNseDataService;
    }
    
    /**
     * Perform comprehensive chart analysis for a stock symbol
     */
    public ChartAnalysisResult analyzeChart(String symbol) {
        // Normalize symbol (remove .NS, .BO if present)
        String normalizedSymbol = normalizeSymbol(symbol);
        
        ChartAnalysisResult result = new ChartAnalysisResult(normalizedSymbol);
        
        try {
            // Analyze each timeframe
            Map<String, TimeframeAnalysis> timeframeAnalyses = new HashMap<>();
            
            // Analyze 15-minute timeframe
            TimeframeAnalysis tf15m = analyzeTimeframe(normalizedSymbol, "15m", 200);
            if (tf15m != null) timeframeAnalyses.put("15m", tf15m);
            
            // Analyze 1-hour timeframe
            TimeframeAnalysis tf1h = analyzeTimeframe(normalizedSymbol, "1h", 200);
            if (tf1h != null) timeframeAnalyses.put("1h", tf1h);
            
            // Analyze 4-hour timeframe
            TimeframeAnalysis tf4h = analyzeTimeframe(normalizedSymbol, "4h", 200);
            if (tf4h != null) timeframeAnalyses.put("4h", tf4h);
            
            // Analyze daily timeframe
            TimeframeAnalysis tf1d = analyzeTimeframe(normalizedSymbol, "1d", 200);
            if (tf1d != null) timeframeAnalyses.put("1d", tf1d);
            
            // Analyze weekly timeframe
            TimeframeAnalysis tf1w = analyzeTimeframe(normalizedSymbol, "1w", 200);
            if (tf1w != null) timeframeAnalyses.put("1w", tf1w);
            
            // Analyze monthly timeframe
            TimeframeAnalysis tf1M = analyzeTimeframe(normalizedSymbol, "1M", 200);
            if (tf1M != null) timeframeAnalyses.put("1M", tf1M);
            
            result.setTimeframes(timeframeAnalyses);
            
            // Calculate overall trend and confidence
            calculateOverallTrend(result);
            
            // Analyze and categorize patterns
            analyzePatterns(result);
            
            // Generate summary
            generateSummary(result);
            
        } catch (Exception e) {
            System.err.println("Error analyzing chart for " + normalizedSymbol + ": " + e.getMessage());
            e.printStackTrace();
            
            // Ensure result has valid default values
            if (result.getTrend() == null) {
                result.setTrend("NEUTRAL");
            }
            if (result.getConfidence() == null) {
                result.setConfidence(BigDecimal.ZERO);
            }
            if (result.getSummary() == null) {
                result.setSummary("Analysis failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
            if (result.getTimeframes() == null) {
                result.setTimeframes(new HashMap<>());
            }
            if (result.getAllPatternsDetected() == null) {
                result.setAllPatternsDetected(new ArrayList<>());
            }
            if (result.getPrimaryPatterns() == null) {
                result.setPrimaryPatterns(new ArrayList<>());
            }
            if (result.getPatternsByTimeframe() == null) {
                result.setPatternsByTimeframe(new HashMap<>());
            }
            if (result.getPatternClassification() == null) {
                Map<String, String> defaultClassification = new HashMap<>();
                defaultClassification.put("mainCategory", "No Clear Pattern");
                defaultClassification.put("reversalPattern", "None");
                defaultClassification.put("continuationPattern", "None");
                defaultClassification.put("trendIndicator", "None");
                result.setPatternClassification(defaultClassification);
            }
        }
        
        return result;
    }
    
    /**
     * Normalize symbol (remove exchange suffixes)
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null) return "";
        symbol = symbol.toUpperCase().trim();
        if (symbol.endsWith(".NS") || symbol.endsWith(".BO") || symbol.endsWith(".NSE")) {
            return symbol.substring(0, symbol.lastIndexOf('.'));
        }
        return symbol;
    }
    
    /**
     * Analyze a specific timeframe
     * IMPORTANT: Pattern detection uses STABLE historical data to ensure consistent results
     */
    private TimeframeAnalysis analyzeTimeframe(String symbol, String timeframe, int periods) throws Exception {
        // Validate inputs
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (timeframe == null || timeframe.trim().isEmpty()) {
            throw new IllegalArgumentException("Timeframe cannot be null or empty");
        }
        if (periods < 1) {
            throw new IllegalArgumentException("Periods must be greater than 0");
        }
        
        List<CandleData> candles = fetchHistoricalData(symbol, timeframe, periods);
        
        if (candles == null || candles.isEmpty()) {
            System.err.println("No data available for " + symbol + " on " + timeframe);
            return null;
        }
        
        // Create a STABLE copy for pattern detection (before live data updates)
        // This ensures patterns are detected consistently based on completed candles
        List<CandleData> stableCandles = new ArrayList<>();
        for (CandleData candle : candles) {
            // Create a deep copy to avoid reference issues
            CandleData copy = new CandleData(
                candle.getTimestamp(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
            );
            stableCandles.add(copy);
        }
        
        // Enhance with live market data (for current price and EMA only)
        boolean[] liveDataUsed = new boolean[1];
        candles = enhanceWithLiveData(symbol, candles, timeframe, liveDataUsed);
        
        TimeframeAnalysis analysis = new TimeframeAnalysis(timeframe);
        analysis.setUsesLiveData(liveDataUsed[0]);
        
        // Get current price from latest candle (which may include live data)
        CandleData latest = candles.get(candles.size() - 1);
        analysis.setCurrentPrice(latest.getClose());
        
        // Calculate 200 EMA using candles with live data (for accurate current EMA)
        BigDecimal ema200 = calculateEMA(candles, 200);
        analysis.setEma200(ema200);
        
        // Store ema200 in a variable accessible to pattern detection
        final BigDecimal ema200Final = ema200;
        
        // Determine EMA position using live price
        if (latest.getClose() != null && ema200 != null) {
            BigDecimal diff = latest.getClose().subtract(ema200);
            BigDecimal percentDiff = ema200.compareTo(BigDecimal.ZERO) > 0 ? 
                diff.divide(ema200, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : 
                BigDecimal.ZERO;
            
            if (percentDiff.compareTo(BigDecimal.valueOf(2)) > 0) {
                analysis.setEmaPosition("ABOVE");
                analysis.addPattern(new ChartPattern(ChartPattern.PatternType.ABOVE_200_EMA, 
                    "Price above 200 EMA", BigDecimal.valueOf(85)));
                analysis.addSignal("Price above 200 EMA");
            } else if (percentDiff.compareTo(BigDecimal.valueOf(-2)) < 0) {
                analysis.setEmaPosition("BELOW");
                analysis.addPattern(new ChartPattern(ChartPattern.PatternType.BELOW_200_EMA, 
                    "Price below 200 EMA", BigDecimal.valueOf(85)));
                analysis.addSignal("Price below 200 EMA");
            } else {
                analysis.setEmaPosition("NEAR");
            }
        }
        
        // CRITICAL: Use STABLE candles for pattern detection (not live-updated ones)
        // This ensures patterns are detected consistently based on completed candles only
        // Patterns should not change based on live price movements
        CandleData stableLatest = stableCandles.get(stableCandles.size() - 1);
        
        // Detect patterns using STABLE historical data
        // Order: Gaps -> Order Blocks -> Single Candles -> Two Candles -> Three Candles -> Reversal -> Continuation
        
        // 1. Gap Patterns
        detectFairValueGaps(stableCandles, analysis);
        
        // 2. Order Blocks
        detectOrderBlocks(stableCandles, analysis);
        
        // 3. Single Candlestick Patterns (work on all timeframes)
        detectSingleCandlestickPatterns(stableCandles, analysis);
        
        // 4. Two-Candle Patterns (work on all timeframes)
        detectTwoCandlePatterns(stableCandles, analysis);
        
        // 5. Three-Candle Patterns (work on all timeframes)
        detectThreeCandlePatterns(stableCandles, analysis);
        
        // 6. Engulfing patterns only on daily and weekly timeframes (more reliable)
        if (timeframe.equals("1d") || timeframe.equals("1w")) {
            detectEngulfingPatterns(stableCandles, analysis, ema200Final, stableLatest);
        }
        
        // 7. Reversal Patterns (Double Top/Bottom, Head & Shoulders)
        detectWPattern(stableCandles, analysis);
        detectMPattern(stableCandles, analysis);
        detectHeadAndShoulders(stableCandles, analysis);
        detectInverseHeadAndShoulders(stableCandles, analysis);
        
        // 8. Continuation Patterns (Triangles, Flags, Pennants, Wedges)
        detectTrianglePatterns(stableCandles, analysis);
        detectFlagPatterns(stableCandles, analysis);
        detectPennantPatterns(stableCandles, analysis);
        detectWedgePatterns(stableCandles, analysis);
        
        // 9. Other Patterns (Cup and Handle, Rounding patterns)
        detectCupAndHandle(stableCandles, analysis);
        detectRoundingPatterns(stableCandles, analysis);
        
        // Deduplicate signals
        deduplicateSignals(analysis);
        
        // Determine trend using STABLE candles (but use live price for EMA comparison)
        determineTimeframeTrend(stableCandles, analysis, ema200);
        
        return analysis;
    }
    
    /**
     * Fetch historical data for a symbol and timeframe
     */
    private List<CandleData> fetchHistoricalData(String symbol, String timeframe, int periods) throws Exception {
        List<CandleData> candles = new ArrayList<>();
        
        // Calculate date range based on timeframe
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, timeframe, periods);
        
        // Fetch from NSE historical API
        DateTimeFormatter nseDateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String fromDate = startDate.format(nseDateFormat);
        String toDate = endDate.format(nseDateFormat);
        
        String url = String.format("https://www.nseindia.com/api/historical/equity/%s?from=%s&to=%s", 
            symbol, fromDate, toDate);
        
        System.out.println("ChartAnalysisService - Fetching " + timeframe + " data from: " + url);
        
        HttpHeaders headers = sessionManager.getNseHeaders(symbol);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode day : root.get("data")) {
                        CandleData candle = parseCandleData(day);
                        if (candle != null) {
                            candles.add(candle);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching historical data: " + e.getMessage());
            // Try alternative: use current quote data and simulate historical
            return generateSimulatedCandles(symbol, timeframe, periods);
        }
        
        // If we got daily data but need intraday, aggregate it
        // NOTE: This is a limitation - NSE doesn't provide intraday historical data easily
        // Aggregating daily data into intraday creates artificial patterns
        if (timeframe.equals("15m") || timeframe.equals("1h") || timeframe.equals("4h")) {
            System.out.println("WARNING: " + timeframe + " timeframe using aggregated daily data - patterns may not be accurate");
            candles = aggregateToTimeframe(candles, timeframe);
        }
        
        // Validate data quality - need at least 20 candles for meaningful analysis
        if (candles.size() < 20) {
            System.err.println("WARNING: Insufficient data for " + timeframe + " - only " + candles.size() + " candles available");
        }
        
        return candles;
    }
    
    /**
     * Parse candle data from NSE JSON response
     */
    private CandleData parseCandleData(JsonNode node) {
        try {
            // NSE historical data fields
            String dateStr = node.has("CH_TIMESTAMP") ? node.get("CH_TIMESTAMP").asText() : 
                           (node.has("TIMESTAMP") ? node.get("TIMESTAMP").asText() : null);
            
            BigDecimal open = parseBigDecimal(node, "CH_OPENING_PRICE", "OPEN");
            BigDecimal high = parseBigDecimal(node, "CH_TRADE_HIGH_PRICE", "HIGH");
            BigDecimal low = parseBigDecimal(node, "CH_TRADE_LOW_PRICE", "LOW");
            BigDecimal close = parseBigDecimal(node, "CH_CLOSING_PRICE", "CLOSE");
            BigDecimal volume = parseBigDecimal(node, "CH_TOT_TRADED_QTY", "VOLUME");
            
            if (open == null || high == null || low == null || close == null) {
                return null;
            }
            
            LocalDateTime timestamp = parseTimestamp(dateStr);
            
            return new CandleData(timestamp, open, high, low, close, volume != null ? volume : BigDecimal.ZERO);
        } catch (Exception e) {
            System.err.println("Error parsing candle data: " + e.getMessage());
            return null;
        }
    }
    
    private BigDecimal parseBigDecimal(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                try {
                    String value = node.get(field).asText();
                    if (value != null && !value.isEmpty() && !value.equals("null")) {
                        return new BigDecimal(value);
                    }
                } catch (Exception e) {
                    // Continue to next field
                }
            }
        }
        return null;
    }
    
    private LocalDateTime parseTimestamp(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            // Try different date formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
            };
            
            for (DateTimeFormatter formatter : formatters) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    return date.atStartOfDay();
                } catch (Exception e) {
                    // Try next format
                }
            }
        } catch (Exception e) {
            // Use current time as fallback
        }
        return LocalDateTime.now();
    }
    
    /**
     * Calculate start date based on timeframe and periods needed
     */
    private LocalDate calculateStartDate(LocalDate endDate, String timeframe, int periods) {
        switch (timeframe) {
            case "15m":
                return endDate.minusDays(periods / 96); // ~96 15-min periods per day
            case "1h":
                return endDate.minusDays(periods / 24); // 24 hours per day
            case "4h":
                return endDate.minusDays(periods / 6); // 6 four-hour periods per day
            case "1d":
                return endDate.minusDays(periods);
            case "1w":
                return endDate.minusWeeks(periods);
            case "1M":
                return endDate.minusMonths(periods);
            default:
                return endDate.minusDays(periods);
        }
    }
    
    /**
     * Calculate EMA (Exponential Moving Average)
     */
    private BigDecimal calculateEMA(List<CandleData> candles, int period) {
        if (candles.size() < period) {
            // Use SMA if not enough data
            return calculateSMA(candles, candles.size());
        }
        
        // Start with SMA
        BigDecimal sma = calculateSMA(candles.subList(0, period), period);
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        
        BigDecimal ema = sma;
        
        // Calculate EMA for remaining candles
        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).getClose();
            if (close != null) {
                // EMA = (Close - Previous EMA) * Multiplier + Previous EMA
                ema = close.subtract(ema).multiply(multiplier).add(ema);
            }
        }
        
        return ema.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate SMA (Simple Moving Average)
     */
    private BigDecimal calculateSMA(List<CandleData> candles, int period) {
        if (candles.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        
        int start = Math.max(0, candles.size() - period);
        for (int i = start; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).getClose();
            if (close != null) {
                sum = sum.add(close);
                count++;
            }
        }
        
        return count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
    
    /**
     * Detect Fair Value Gaps (FVG) - Only detect recent significant gaps
     */
    private void detectFairValueGaps(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 3) return;
        
        // Only check last 20 candles for FVG to avoid too many detections
        int startIndex = Math.max(1, candles.size() - 20);
        int bullishFVGCount = 0;
        int bearishFVGCount = 0;
        ChartPattern latestBullishFVG = null;
        ChartPattern latestBearishFVG = null;
        
        for (int i = startIndex; i < candles.size() - 1; i++) {
            CandleData prev = candles.get(i - 1);
            CandleData next = candles.get(i + 1);
            
            // Bullish FVG: gap between prev high and next low
            if (prev.getHigh() != null && next.getLow() != null && 
                prev.getHigh().compareTo(next.getLow()) < 0) {
                BigDecimal gap = next.getLow().subtract(prev.getHigh());
                BigDecimal gapPercent = prev.getHigh().compareTo(BigDecimal.ZERO) > 0 ?
                    gap.divide(prev.getHigh(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Increase threshold to 1% to avoid noise
                if (gapPercent.compareTo(BigDecimal.valueOf(1.0)) > 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.FAIR_VALUE_GAP,
                        "Bullish Fair Value Gap detected",
                        BigDecimal.valueOf(75)
                    );
                    pattern.setPriceLevel(prev.getHigh().add(next.getLow()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
                    latestBullishFVG = pattern;
                    bullishFVGCount++;
                }
            }
            
            // Bearish FVG: gap between prev low and next high
            if (prev.getLow() != null && next.getHigh() != null && 
                prev.getLow().compareTo(next.getHigh()) > 0) {
                BigDecimal gap = prev.getLow().subtract(next.getHigh());
                BigDecimal gapPercent = prev.getLow().compareTo(BigDecimal.ZERO) > 0 ?
                    gap.divide(prev.getLow(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Increase threshold to 1% to avoid noise
                if (gapPercent.compareTo(BigDecimal.valueOf(1.0)) > 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.FAIR_VALUE_GAP,
                        "Bearish Fair Value Gap detected",
                        BigDecimal.valueOf(75)
                    );
                    pattern.setPriceLevel(prev.getLow().add(next.getHigh()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
                    latestBearishFVG = pattern;
                    bearishFVGCount++;
                }
            }
        }
        
        // Only add the most recent FVG of each type, and only if significant
        if (latestBullishFVG != null && bullishFVGCount <= 3) {
            analysis.addPattern(latestBullishFVG);
            analysis.addSignal("Bullish FVG");
        }
        if (latestBearishFVG != null && bearishFVGCount <= 3) {
            analysis.addPattern(latestBearishFVG);
            analysis.addSignal("Bearish FVG");
        }
    }
    
    /**
     * Detect Order Blocks (Demand/Supply zones)
     */
    private void detectOrderBlocks(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 5) return;
        
        // Look for strong bullish/bearish candles followed by consolidation
        for (int i = 2; i < candles.size() - 2; i++) {
            CandleData candle = candles.get(i);
            
            // Bullish Order Block: Strong bullish candle followed by bullish movement
            if (candle.isBullish() && candle.getBodySize().compareTo(candle.getHigh().subtract(candle.getLow()).multiply(BigDecimal.valueOf(0.7))) > 0) {
                // Check if followed by bullish movement
                boolean followedByBullish = true;
                for (int j = i + 1; j < Math.min(i + 3, candles.size()); j++) {
                    if (candles.get(j).getClose().compareTo(candle.getClose()) < 0) {
                        followedByBullish = false;
                        break;
                    }
                }
                
                if (followedByBullish) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.ORDER_BLOCK_BULLISH,
                        "Bullish Order Block (Demand Zone)",
                        BigDecimal.valueOf(70)
                    );
                    pattern.setPriceLevel(candle.getLow());
                    analysis.addPattern(pattern);
                    analysis.addSignal("Bullish Order Block");
                }
            }
            
            // Bearish Order Block: Strong bearish candle followed by bearish movement
            if (candle.isBearish() && candle.getBodySize().compareTo(candle.getHigh().subtract(candle.getLow()).multiply(BigDecimal.valueOf(0.7))) > 0) {
                // Check if followed by bearish movement
                boolean followedByBearish = true;
                for (int j = i + 1; j < Math.min(i + 3, candles.size()); j++) {
                    if (candles.get(j).getClose().compareTo(candle.getClose()) > 0) {
                        followedByBearish = false;
                        break;
                    }
                }
                
                if (followedByBearish) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.ORDER_BLOCK_BEARISH,
                        "Bearish Order Block (Supply Zone)",
                        BigDecimal.valueOf(70)
                    );
                    pattern.setPriceLevel(candle.getHigh());
                    analysis.addPattern(pattern);
                    analysis.addSignal("Bearish Order Block");
                }
            }
        }
    }
    
    /**
     * Detect Engulfing patterns - Only on Daily and Weekly timeframes
     * 
     * IMPORTANT: Engulfing patterns are only detected on daily ("1d") and weekly ("1w") timeframes
     * as these are more reliable and significant. Intraday engulfing patterns are too noisy.
     * 
     * STRICT CRITERIA:
     * Bullish Engulfing: Current bullish candle must completely engulf previous bearish candle's body
     *   - Current Open <= Previous Close (opens at or below previous close)
     *   - Current Close >= Previous Open (closes at or above previous open)
     *   - Current body must be at least 1.5x larger than previous body
     * 
     * Bearish Engulfing: Current bearish candle must completely engulf previous bullish candle's body
     *   - Current Open >= Previous Close (opens at or above previous close)
     *   - Current Close <= Previous Open (closes at or below previous open)
     *   - Current body must be at least 1.5x larger than previous body
     */
    private void detectEngulfingPatterns(List<CandleData> candles, TimeframeAnalysis analysis, 
                                        BigDecimal ema200, CandleData latestCandle) {
        // Only detect on daily and weekly timeframes
        String timeframe = analysis.getTimeframe();
        if (!timeframe.equals("1d") && !timeframe.equals("1w")) {
            return; // Skip engulfing detection for other timeframes
        }
        if (candles.size() < 2) return;
        
        // Only check last 10 candles for engulfing patterns
        int startIndex = Math.max(1, candles.size() - 10);
        ChartPattern latestBullishEngulfing = null;
        ChartPattern latestBearishEngulfing = null;
        
        for (int i = startIndex; i < candles.size(); i++) {
            CandleData prev = candles.get(i - 1);
            CandleData curr = candles.get(i);
            
            // Validate both candles have valid data
            if (prev.getOpen() == null || prev.getClose() == null || 
                curr.getOpen() == null || curr.getClose() == null) {
                continue;
            }
            
            // Bullish Engulfing: Bearish candle followed by larger bullish candle
            // STRICT: Current open must be <= previous close AND current close must be >= previous open
            if (prev.isBearish() && curr.isBullish()) {
                BigDecimal prevOpen = prev.getOpen();
                BigDecimal prevClose = prev.getClose();
                BigDecimal currOpen = curr.getOpen();
                BigDecimal currClose = curr.getClose();
                
                // Check complete body engulfment
                boolean openEngulfs = currOpen.compareTo(prevClose) <= 0; // Current open <= Previous close
                boolean closeEngulfs = currClose.compareTo(prevOpen) >= 0; // Current close >= Previous open
                
                // Check body size requirement (at least 1.5x larger)
                BigDecimal prevBodySize = prevOpen.subtract(prevClose).abs(); // Bearish: open - close
                BigDecimal currBodySize = currClose.subtract(currOpen).abs(); // Bullish: close - open
                boolean bodySizeValid = currBodySize.compareTo(prevBodySize.multiply(BigDecimal.valueOf(1.5))) > 0;
                
                if (openEngulfs && closeEngulfs && bodySizeValid) {
                    // Debug logging
                    System.out.println("DEBUG: Bullish Engulfing detected at " + curr.getTimestamp());
                    System.out.println("  Prev: Open=" + prevOpen + ", Close=" + prevClose + ", BodySize=" + prevBodySize);
                    System.out.println("  Curr: Open=" + currOpen + ", Close=" + currClose + ", BodySize=" + currBodySize);
                    System.out.println("  Engulfment: Open<=" + prevClose + "? " + openEngulfs + ", Close>=" + prevOpen + "? " + closeEngulfs);
                    
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.BULLISH_ENGULFING,
                        "Bullish Engulfing Pattern",
                        BigDecimal.valueOf(80)
                    );
                    latestBullishEngulfing = pattern;
                }
            }
            
            // Bearish Engulfing: Bullish candle followed by larger bearish candle
            // STRICT: Current open must be >= previous close AND current close must be <= previous open
            if (prev.isBullish() && curr.isBearish()) {
                BigDecimal prevOpen = prev.getOpen();
                BigDecimal prevClose = prev.getClose();
                BigDecimal currOpen = curr.getOpen();
                BigDecimal currClose = curr.getClose();
                
                // Check complete body engulfment
                boolean openEngulfs = currOpen.compareTo(prevClose) >= 0; // Current open >= Previous close
                boolean closeEngulfs = currClose.compareTo(prevOpen) <= 0; // Current close <= Previous open
                
                // Check body size requirement (at least 1.5x larger)
                BigDecimal prevBodySize = prevClose.subtract(prevOpen).abs(); // Bullish: close - open
                BigDecimal currBodySize = currOpen.subtract(currClose).abs(); // Bearish: open - close
                boolean bodySizeValid = currBodySize.compareTo(prevBodySize.multiply(BigDecimal.valueOf(1.5))) > 0;
                
                if (openEngulfs && closeEngulfs && bodySizeValid) {
                    // Debug logging
                    System.out.println("DEBUG: Bearish Engulfing detected at " + curr.getTimestamp());
                    System.out.println("  Prev: Open=" + prevOpen + ", Close=" + prevClose + ", BodySize=" + prevBodySize);
                    System.out.println("  Curr: Open=" + currOpen + ", Close=" + currClose + ", BodySize=" + currBodySize);
                    System.out.println("  Engulfment: Open>=" + prevClose + "? " + openEngulfs + ", Close<=" + prevOpen + "? " + closeEngulfs);
                    
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.BEARISH_ENGULFING,
                        "Bearish Engulfing Pattern",
                        BigDecimal.valueOf(80)
                    );
                    latestBearishEngulfing = pattern;
                }
            }
        }
        
        // Only add the most recent engulfing pattern
        // IMPORTANT: If both bullish and bearish engulfing are detected in the same timeframe,
        // this indicates data quality issues (likely from aggregated daily data for intraday timeframes)
        // In such cases, we should NOT add either pattern as they conflict
        if (latestBullishEngulfing != null && latestBearishEngulfing != null) {
            // Both patterns detected - this is suspicious, likely data quality issue
            System.err.println("WARNING: Both Bullish and Bearish Engulfing detected in " + analysis.getTimeframe() + 
                " - This indicates conflicting signals, likely due to data aggregation. Skipping both patterns.");
            // Don't add either pattern - conflicting signals are not reliable
        } else if (latestBullishEngulfing != null) {
            analysis.addPattern(latestBullishEngulfing);
            analysis.addSignal("Bullish Engulfing");
        } else if (latestBearishEngulfing != null) {
            analysis.addPattern(latestBearishEngulfing);
            analysis.addSignal("Bearish Engulfing");
        }
    }
    
    /**
     * Detect W Pattern (Double Bottom - Bullish Reversal)
     */
    private void detectWPattern(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 10) return;
        
        // Look for two similar lows with a peak in between
        for (int i = 5; i < candles.size() - 5; i++) {
            CandleData leftLow = candles.get(i);
            CandleData rightLow = candles.get(i + 5);
            
            // Find peak between the two lows
            CandleData peak = candles.get(i + 2);
            for (int j = i + 1; j < i + 5; j++) {
                if (candles.get(j).getHigh().compareTo(peak.getHigh()) > 0) {
                    peak = candles.get(j);
                }
            }
            
            // Check if it's a W pattern
            if (leftLow.getLow() != null && rightLow.getLow() != null && peak.getHigh() != null) {
                BigDecimal lowDiff = leftLow.getLow().subtract(rightLow.getLow()).abs();
                BigDecimal avgLow = leftLow.getLow().add(rightLow.getLow()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal diffPercent = avgLow.compareTo(BigDecimal.ZERO) > 0 ?
                    lowDiff.divide(avgLow, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Lows should be similar (within 3%) and peak should be significantly higher
                if (diffPercent.compareTo(BigDecimal.valueOf(3)) < 0 &&
                    peak.getHigh().compareTo(avgLow.multiply(BigDecimal.valueOf(1.05))) > 0) {
                    
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.W_PATTERN,
                        "W Pattern (Double Bottom) - Bullish Reversal",
                        BigDecimal.valueOf(75)
                    );
                    pattern.setPriceLevel(avgLow);
                    analysis.addPattern(pattern);
                    analysis.addSignal("W Pattern (Double Bottom)");
                    break; // Only detect one W pattern
                }
            }
        }
    }
    
    /**
     * Detect M Pattern (Double Top - Bearish Reversal)
     */
    private void detectMPattern(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 10) return;
        
        // Look for two similar highs with a trough in between
        for (int i = 5; i < candles.size() - 5; i++) {
            CandleData leftHigh = candles.get(i);
            CandleData rightHigh = candles.get(i + 5);
            
            // Find trough between the two highs
            CandleData trough = candles.get(i + 2);
            for (int j = i + 1; j < i + 5; j++) {
                if (candles.get(j).getLow().compareTo(trough.getLow()) < 0) {
                    trough = candles.get(j);
                }
            }
            
            // Check if it's an M pattern
            if (leftHigh.getHigh() != null && rightHigh.getHigh() != null && trough.getLow() != null) {
                BigDecimal highDiff = leftHigh.getHigh().subtract(rightHigh.getHigh()).abs();
                BigDecimal avgHigh = leftHigh.getHigh().add(rightHigh.getHigh()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal diffPercent = avgHigh.compareTo(BigDecimal.ZERO) > 0 ?
                    highDiff.divide(avgHigh, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Highs should be similar (within 3%) and trough should be significantly lower
                if (diffPercent.compareTo(BigDecimal.valueOf(3)) < 0 &&
                    trough.getLow().compareTo(avgHigh.multiply(BigDecimal.valueOf(0.95))) < 0) {
                    
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.M_PATTERN,
                        "M Pattern (Double Top) - Bearish Reversal",
                        BigDecimal.valueOf(75)
                    );
                    pattern.setPriceLevel(avgHigh);
                    analysis.addPattern(pattern);
                    analysis.addSignal("M Pattern (Double Top)");
                    break; // Only detect one M pattern
                }
            }
        }
    }
    
    /**
     * Detect Single Candlestick Patterns (Hammer, Shooting Star, Doji, etc.)
     * These patterns work on all timeframes but are more reliable on higher timeframes
     */
    private void detectSingleCandlestickPatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 1) return;
        
        // Check last 5 candles for single candlestick patterns
        int startIndex = Math.max(0, candles.size() - 5);
        
        for (int i = startIndex; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            if (candle.getOpen() == null || candle.getClose() == null || 
                candle.getHigh() == null || candle.getLow() == null) {
                continue;
            }
            
            BigDecimal bodySize = candle.getBodySize();
            BigDecimal totalRange = candle.getHigh().subtract(candle.getLow());
            BigDecimal upperWick = candle.getUpperWick();
            BigDecimal lowerWick = candle.getLowerWick();
            
            if (totalRange.compareTo(BigDecimal.ZERO) == 0) continue;
            
            // Calculate ratios
            BigDecimal bodyRatio = totalRange.compareTo(BigDecimal.ZERO) > 0 ? 
                bodySize.divide(totalRange, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal lowerWickRatio = totalRange.compareTo(BigDecimal.ZERO) > 0 ? 
                lowerWick.divide(totalRange, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal upperWickRatio = totalRange.compareTo(BigDecimal.ZERO) > 0 ? 
                upperWick.divide(totalRange, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            
            // HAMMER: Small body at top, long lower wick (at least 2x body), small upper wick
            // Bullish reversal pattern, typically at bottom of downtrend
            if (bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0 && // Small body
                lowerWickRatio.compareTo(BigDecimal.valueOf(0.6)) > 0 && // Long lower wick
                upperWickRatio.compareTo(BigDecimal.valueOf(0.1)) < 0 && // Small upper wick
                lowerWick.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0) { // Lower wick at least 2x body
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.HAMMER,
                    "Hammer - Bullish Reversal Pattern",
                    BigDecimal.valueOf(75)
                );
                pattern.setPriceLevel(candle.getLow());
                analysis.addPattern(pattern);
                analysis.addSignal("Hammer");
            }
            
            // SHOOTING STAR: Small body at bottom, long upper wick (at least 2x body), small lower wick
            // Bearish reversal pattern, typically at top of uptrend
            if (bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0 && // Small body
                upperWickRatio.compareTo(BigDecimal.valueOf(0.6)) > 0 && // Long upper wick
                lowerWickRatio.compareTo(BigDecimal.valueOf(0.1)) < 0 && // Small lower wick
                upperWick.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0) { // Upper wick at least 2x body
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.SHOOTING_STAR,
                    "Shooting Star - Bearish Reversal Pattern",
                    BigDecimal.valueOf(75)
                );
                pattern.setPriceLevel(candle.getHigh());
                analysis.addPattern(pattern);
                analysis.addSignal("Shooting Star");
            }
            
            // HANGING MAN: Similar to Hammer but appears after uptrend (bearish)
            // Small body, long lower wick, appears at top
            if (i > 0 && candles.get(i-1).isBullish() && // Previous candle was bullish
                bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0 &&
                lowerWickRatio.compareTo(BigDecimal.valueOf(0.6)) > 0 &&
                upperWickRatio.compareTo(BigDecimal.valueOf(0.1)) < 0) {
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.HANGING_MAN,
                    "Hanging Man - Bearish Reversal Pattern",
                    BigDecimal.valueOf(70)
                );
                pattern.setPriceLevel(candle.getLow());
                analysis.addPattern(pattern);
                analysis.addSignal("Hanging Man");
            }
            
            // INVERTED HAMMER: Small body at bottom, long upper wick, small lower wick
            // Bullish reversal, appears at bottom
            if (i > 0 && candles.get(i-1).isBearish() && // Previous candle was bearish
                bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0 &&
                upperWickRatio.compareTo(BigDecimal.valueOf(0.6)) > 0 &&
                lowerWickRatio.compareTo(BigDecimal.valueOf(0.1)) < 0) {
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.INVERTED_HAMMER,
                    "Inverted Hammer - Bullish Reversal Pattern",
                    BigDecimal.valueOf(70)
                );
                pattern.setPriceLevel(candle.getLow());
                analysis.addPattern(pattern);
                analysis.addSignal("Inverted Hammer");
            }
            
            // DOJI: Open and close are very close (indecision)
            // Body should be less than 5% of total range
            if (bodyRatio.compareTo(BigDecimal.valueOf(0.05)) < 0) {
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.DOJI,
                    "Doji - Indecision Pattern",
                    BigDecimal.valueOf(60)
                );
                pattern.setPriceLevel(candle.getClose());
                analysis.addPattern(pattern);
                analysis.addSignal("Doji");
            }
        }
    }
    
    /**
     * Detect Two-Candle Patterns (Harami, Piercing Pattern, Dark Cloud Cover)
     */
    private void detectTwoCandlePatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 2) return;
        
        // Check last 10 candles
        int startIndex = Math.max(1, candles.size() - 10);
        
        for (int i = startIndex; i < candles.size(); i++) {
            CandleData first = candles.get(i - 1);
            CandleData second = candles.get(i);
            
            if (first.getOpen() == null || first.getClose() == null ||
                second.getOpen() == null || second.getClose() == null) {
                continue;
            }
            
            BigDecimal firstBodySize = first.getBodySize();
            BigDecimal secondBodySize = second.getBodySize();
            
            // BULLISH HARAMI: Large bearish candle followed by small bullish candle inside
            if (first.isBearish() && second.isBullish() &&
                second.getOpen().compareTo(first.getClose()) > 0 && // Second opens above first close
                second.getClose().compareTo(first.getOpen()) < 0 && // Second closes below first open
                secondBodySize.compareTo(firstBodySize.multiply(BigDecimal.valueOf(0.5))) < 0) { // Second body < 50% of first
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.BULLISH_HARAMI,
                    "Bullish Harami - Bullish Reversal Pattern",
                    BigDecimal.valueOf(70)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Bullish Harami");
            }
            
            // BEARISH HARAMI: Large bullish candle followed by small bearish candle inside
            if (first.isBullish() && second.isBearish() &&
                second.getOpen().compareTo(first.getClose()) < 0 && // Second opens below first close
                second.getClose().compareTo(first.getOpen()) > 0 && // Second closes above first open
                secondBodySize.compareTo(firstBodySize.multiply(BigDecimal.valueOf(0.5))) < 0) { // Second body < 50% of first
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.BEARISH_HARAMI,
                    "Bearish Harami - Bearish Reversal Pattern",
                    BigDecimal.valueOf(70)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Bearish Harami");
            }
            
            // PIERCING PATTERN: Bearish candle followed by bullish candle that opens below but closes above midpoint
            if (first.isBearish() && second.isBullish() &&
                second.getOpen().compareTo(first.getClose()) < 0 && // Opens below previous close
                second.getClose().compareTo(first.getOpen().add(first.getClose()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)) > 0) { // Closes above midpoint
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.PIERCING_PATTERN,
                    "Piercing Pattern - Bullish Reversal",
                    BigDecimal.valueOf(75)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Piercing Pattern");
            }
            
            // DARK CLOUD COVER: Bullish candle followed by bearish candle that opens above but closes below midpoint
            if (first.isBullish() && second.isBearish() &&
                second.getOpen().compareTo(first.getClose()) > 0 && // Opens above previous close
                second.getClose().compareTo(first.getOpen().add(first.getClose()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)) < 0) { // Closes below midpoint
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.DARK_CLOUD_COVER,
                    "Dark Cloud Cover - Bearish Reversal",
                    BigDecimal.valueOf(75)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Dark Cloud Cover");
            }
        }
    }
    
    /**
     * Detect Three-Candle Patterns (Morning Star, Evening Star, Three White Soldiers, Three Black Crows)
     */
    private void detectThreeCandlePatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 3) return;
        
        // Check last 10 candles
        int startIndex = Math.max(2, candles.size() - 10);
        
        for (int i = startIndex; i < candles.size(); i++) {
            CandleData first = candles.get(i - 2);
            CandleData second = candles.get(i - 1);
            CandleData third = candles.get(i);
            
            if (first.getOpen() == null || first.getClose() == null ||
                second.getOpen() == null || second.getClose() == null ||
                third.getOpen() == null || third.getClose() == null) {
                continue;
            }
            
            BigDecimal firstBodySize = first.getBodySize();
            BigDecimal secondBodySize = second.getBodySize();
            BigDecimal thirdBodySize = third.getBodySize();
            
            // MORNING STAR: Bearish candle, small body (gap down), bullish candle (gap up)
            // Bullish reversal pattern
            if (first.isBearish() && third.isBullish() &&
                secondBodySize.compareTo(firstBodySize.multiply(BigDecimal.valueOf(0.3))) < 0 && // Small middle body
                second.getClose().compareTo(first.getClose()) < 0 && // Gap down from first
                third.getOpen().compareTo(second.getClose()) > 0 && // Gap up to third
                third.getClose().compareTo(first.getOpen().add(first.getClose()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)) > 0) { // Third closes above first midpoint
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.MORNING_STAR,
                    "Morning Star - Strong Bullish Reversal",
                    BigDecimal.valueOf(80)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Morning Star");
            }
            
            // EVENING STAR: Bullish candle, small body (gap up), bearish candle (gap down)
            // Bearish reversal pattern
            if (first.isBullish() && third.isBearish() &&
                secondBodySize.compareTo(firstBodySize.multiply(BigDecimal.valueOf(0.3))) < 0 && // Small middle body
                second.getClose().compareTo(first.getClose()) > 0 && // Gap up from first
                third.getOpen().compareTo(second.getClose()) < 0 && // Gap down to third
                third.getClose().compareTo(first.getOpen().add(first.getClose()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)) < 0) { // Third closes below first midpoint
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.EVENING_STAR,
                    "Evening Star - Strong Bearish Reversal",
                    BigDecimal.valueOf(80)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Evening Star");
            }
            
            // THREE WHITE SOLDIERS: Three consecutive bullish candles with increasing closes
            // Strong bullish continuation
            if (first.isBullish() && second.isBullish() && third.isBullish() &&
                second.getClose().compareTo(first.getClose()) > 0 &&
                third.getClose().compareTo(second.getClose()) > 0 &&
                secondBodySize.compareTo(firstBodySize) > 0 &&
                thirdBodySize.compareTo(secondBodySize) > 0) { // Each candle larger than previous
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.THREE_WHITE_SOLDIERS,
                    "Three White Soldiers - Strong Bullish Continuation",
                    BigDecimal.valueOf(85)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Three White Soldiers");
            }
            
            // THREE BLACK CROWS: Three consecutive bearish candles with decreasing closes
            // Strong bearish continuation
            if (first.isBearish() && second.isBearish() && third.isBearish() &&
                second.getClose().compareTo(first.getClose()) < 0 &&
                third.getClose().compareTo(second.getClose()) < 0 &&
                secondBodySize.compareTo(firstBodySize) > 0 &&
                thirdBodySize.compareTo(secondBodySize) > 0) { // Each candle larger than previous
                
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.THREE_BLACK_CROWS,
                    "Three Black Crows - Strong Bearish Continuation",
                    BigDecimal.valueOf(85)
                );
                analysis.addPattern(pattern);
                analysis.addSignal("Three Black Crows");
            }
        }
    }
    
    /**
     * Detect Head and Shoulders Pattern (Bearish Reversal)
     * Requires: Left shoulder, head (higher), right shoulder (similar to left)
     */
    private void detectHeadAndShoulders(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 15) return; // Need enough candles for H&S pattern
        
        // Look for pattern in last 30 candles
        int startIndex = Math.max(5, candles.size() - 30);
        
        for (int i = startIndex; i < candles.size() - 10; i++) {
            // Find left shoulder (peak)
            CandleData leftShoulder = candles.get(i);
            for (int j = i + 1; j < Math.min(i + 5, candles.size() - 5); j++) {
                if (candles.get(j).getHigh().compareTo(leftShoulder.getHigh()) > 0) {
                    leftShoulder = candles.get(j);
                }
            }
            
            // Find head (highest point)
            int headIndex = i + 3;
            CandleData head = candles.get(headIndex);
            for (int j = i + 3; j < Math.min(i + 8, candles.size() - 3); j++) {
                if (candles.get(j).getHigh().compareTo(head.getHigh()) > 0) {
                    head = candles.get(j);
                    headIndex = j;
                }
            }
            
            // Find right shoulder (similar height to left)
            CandleData rightShoulder = candles.get(headIndex + 3);
            for (int j = headIndex + 3; j < Math.min(headIndex + 8, candles.size()); j++) {
                if (candles.get(j).getHigh().compareTo(rightShoulder.getHigh()) > 0) {
                    rightShoulder = candles.get(j);
                }
            }
            
            // Validate H&S pattern
            if (head.getHigh() != null && leftShoulder.getHigh() != null && rightShoulder.getHigh() != null) {
                // Head must be higher than both shoulders
                boolean headIsHighest = head.getHigh().compareTo(leftShoulder.getHigh()) > 0 &&
                                      head.getHigh().compareTo(rightShoulder.getHigh()) > 0;
                
                // Shoulders should be similar height (within 5%)
                BigDecimal shoulderDiff = leftShoulder.getHigh().subtract(rightShoulder.getHigh()).abs();
                BigDecimal avgShoulder = leftShoulder.getHigh().add(rightShoulder.getHigh()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal shoulderDiffPercent = avgShoulder.compareTo(BigDecimal.ZERO) > 0 ?
                    shoulderDiff.divide(avgShoulder, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.valueOf(100);
                
                if (headIsHighest && shoulderDiffPercent.compareTo(BigDecimal.valueOf(5)) < 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.HEAD_AND_SHOULDERS,
                        "Head and Shoulders - Bearish Reversal",
                        BigDecimal.valueOf(80)
                    );
                    pattern.setPriceLevel(head.getHigh());
                    analysis.addPattern(pattern);
                    analysis.addSignal("Head and Shoulders");
                    break; // Only detect one H&S pattern
                }
            }
        }
    }
    
    /**
     * Detect Inverse Head and Shoulders Pattern (Bullish Reversal)
     * Requires: Left shoulder (low), head (lower), right shoulder (similar to left)
     */
    private void detectInverseHeadAndShoulders(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 15) return;
        
        int startIndex = Math.max(5, candles.size() - 30);
        
        for (int i = startIndex; i < candles.size() - 10; i++) {
            // Find left shoulder (trough)
            CandleData leftShoulder = candles.get(i);
            for (int j = i + 1; j < Math.min(i + 5, candles.size() - 5); j++) {
                if (candles.get(j).getLow().compareTo(leftShoulder.getLow()) < 0) {
                    leftShoulder = candles.get(j);
                }
            }
            
            // Find head (lowest point)
            int headIndex = i + 3;
            CandleData head = candles.get(headIndex);
            for (int j = i + 3; j < Math.min(i + 8, candles.size() - 3); j++) {
                if (candles.get(j).getLow().compareTo(head.getLow()) < 0) {
                    head = candles.get(j);
                    headIndex = j;
                }
            }
            
            // Find right shoulder (similar to left)
            CandleData rightShoulder = candles.get(headIndex + 3);
            for (int j = headIndex + 3; j < Math.min(headIndex + 8, candles.size()); j++) {
                if (candles.get(j).getLow().compareTo(rightShoulder.getLow()) < 0) {
                    rightShoulder = candles.get(j);
                }
            }
            
            // Validate Inverse H&S pattern
            if (head.getLow() != null && leftShoulder.getLow() != null && rightShoulder.getLow() != null) {
                // Head must be lower than both shoulders
                boolean headIsLowest = head.getLow().compareTo(leftShoulder.getLow()) < 0 &&
                                      head.getLow().compareTo(rightShoulder.getLow()) < 0;
                
                // Shoulders should be similar (within 5%)
                BigDecimal shoulderDiff = leftShoulder.getLow().subtract(rightShoulder.getLow()).abs();
                BigDecimal avgShoulder = leftShoulder.getLow().add(rightShoulder.getLow()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal shoulderDiffPercent = avgShoulder.compareTo(BigDecimal.ZERO) > 0 ?
                    shoulderDiff.divide(avgShoulder, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.valueOf(100);
                
                if (headIsLowest && shoulderDiffPercent.compareTo(BigDecimal.valueOf(5)) < 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.INVERSE_HEAD_AND_SHOULDERS,
                        "Inverse Head and Shoulders - Bullish Reversal",
                        BigDecimal.valueOf(80)
                    );
                    pattern.setPriceLevel(head.getLow());
                    analysis.addPattern(pattern);
                    analysis.addSignal("Inverse Head and Shoulders");
                    break; // Only detect one pattern
                }
            }
        }
    }
    
    /**
     * Detect Triangle Patterns (Ascending, Descending, Symmetrical)
     */
    private void detectTrianglePatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 10) return;
        
        // Check last 20 candles for triangle formation
        int startIndex = Math.max(0, candles.size() - 20);
        
        // Find highest and lowest points in the range
        BigDecimal highestHigh = candles.get(startIndex).getHigh();
        BigDecimal lowestLow = candles.get(startIndex).getLow();
        BigDecimal recentHigh = candles.get(candles.size() - 1).getHigh();
        BigDecimal recentLow = candles.get(candles.size() - 1).getLow();
        
        for (int i = startIndex; i < candles.size(); i++) {
            if (candles.get(i).getHigh().compareTo(highestHigh) > 0) {
                highestHigh = candles.get(i).getHigh();
            }
            if (candles.get(i).getLow().compareTo(lowestLow) < 0) {
                lowestLow = candles.get(i).getLow();
            }
        }
        
        // Calculate range compression
        BigDecimal totalRange = highestHigh.subtract(lowestLow);
        BigDecimal recentRange = recentHigh.subtract(recentLow);
        
        if (totalRange.compareTo(BigDecimal.ZERO) == 0) return;
        
        BigDecimal compressionRatio = recentRange.divide(totalRange, 4, RoundingMode.HALF_UP);
        
        // Check for ascending triangle (higher lows, similar highs)
        BigDecimal firstLow = candles.get(startIndex).getLow();
        BigDecimal lastLow = candles.get(candles.size() - 1).getLow();
        
        if (lastLow.compareTo(firstLow) > 0 && // Higher lows
            compressionRatio.compareTo(BigDecimal.valueOf(0.7)) < 0) { // Range compressing
            ChartPattern pattern = new ChartPattern(
                ChartPattern.PatternType.ASCENDING_TRIANGLE,
                "Ascending Triangle - Bullish Continuation",
                BigDecimal.valueOf(70)
            );
            analysis.addPattern(pattern);
            analysis.addSignal("Ascending Triangle");
            return; // Only detect one triangle type
        }
        
        // Check for descending triangle (lower highs, similar lows)
        BigDecimal firstHigh = candles.get(startIndex).getHigh();
        BigDecimal lastHigh = candles.get(candles.size() - 1).getHigh();
        
        if (lastHigh.compareTo(firstHigh) < 0 && // Lower highs
            compressionRatio.compareTo(BigDecimal.valueOf(0.7)) < 0) { // Range compressing
            ChartPattern pattern = new ChartPattern(
                ChartPattern.PatternType.DESCENDING_TRIANGLE,
                "Descending Triangle - Bearish Continuation",
                BigDecimal.valueOf(70)
            );
            analysis.addPattern(pattern);
            analysis.addSignal("Descending Triangle");
            return;
        }
        
        // Check for symmetrical triangle (both highs and lows converging)
        if (compressionRatio.compareTo(BigDecimal.valueOf(0.6)) < 0 &&
            lastHigh.compareTo(firstHigh) < 0 &&
            lastLow.compareTo(firstLow) > 0) {
            ChartPattern pattern = new ChartPattern(
                ChartPattern.PatternType.SYMMETRICAL_TRIANGLE,
                "Symmetrical Triangle - Continuation Pattern",
                BigDecimal.valueOf(65)
            );
            analysis.addPattern(pattern);
            analysis.addSignal("Symmetrical Triangle");
        }
    }
    
    /**
     * Detect Flag Patterns (Bullish/Bearish Flags)
     */
    private void detectFlagPatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 8) return;
        
        // Flags require: Strong move (pole) followed by consolidation (flag)
        int startIndex = Math.max(0, candles.size() - 15);
        
        // Check for bullish flag: Strong up move followed by slight downward consolidation
        if (candles.size() >= 8) {
            CandleData poleStart = candles.get(startIndex);
            CandleData poleEnd = candles.get(startIndex + 4);
            CandleData flagStart = candles.get(startIndex + 4);
            CandleData flagEnd = candles.get(candles.size() - 1);
            
            if (poleStart.getClose() != null && poleEnd.getClose() != null &&
                flagStart.getClose() != null && flagEnd.getClose() != null) {
                
                // Strong upward move (pole)
                BigDecimal poleMove = poleEnd.getClose().subtract(poleStart.getClose());
                BigDecimal poleMovePercent = poleStart.getClose().compareTo(BigDecimal.ZERO) > 0 ?
                    poleMove.divide(poleStart.getClose(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Slight downward consolidation (flag)
                BigDecimal flagMove = flagEnd.getClose().subtract(flagStart.getClose());
                BigDecimal flagMovePercent = flagStart.getClose().compareTo(BigDecimal.ZERO) > 0 ?
                    flagMove.divide(flagStart.getClose(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Bullish flag: Strong up move followed by small down consolidation
                if (poleMovePercent.compareTo(BigDecimal.valueOf(3)) > 0 && // Strong up move
                    flagMovePercent.compareTo(BigDecimal.valueOf(-5)) > 0 && // Small down move
                    flagMovePercent.compareTo(BigDecimal.valueOf(-1)) < 0) { // But still down
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.BULLISH_FLAG,
                        "Bullish Flag - Continuation Pattern",
                        BigDecimal.valueOf(75)
                    );
                    analysis.addPattern(pattern);
                    analysis.addSignal("Bullish Flag");
                    return;
                }
                
                // Bearish flag: Strong down move followed by small up consolidation
                if (poleMovePercent.compareTo(BigDecimal.valueOf(-3)) < 0 && // Strong down move
                    flagMovePercent.compareTo(BigDecimal.valueOf(1)) > 0 && // Small up move
                    flagMovePercent.compareTo(BigDecimal.valueOf(5)) < 0) { // But still small
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.BEARISH_FLAG,
                        "Bearish Flag - Continuation Pattern",
                        BigDecimal.valueOf(75)
                    );
                    analysis.addPattern(pattern);
                    analysis.addSignal("Bearish Flag");
                }
            }
        }
    }
    
    /**
     * Detect Pennant Patterns (Bullish/Bearish Pennants)
     */
    private void detectPennantPatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 8) return;
        
        // Pennants are similar to flags but with converging trendlines
        int startIndex = Math.max(0, candles.size() - 15);
        
        if (candles.size() >= 8) {
            CandleData poleStart = candles.get(startIndex);
            CandleData poleEnd = candles.get(startIndex + 4);
            CandleData pennantStart = candles.get(startIndex + 4);
            
            if (poleStart.getClose() != null && poleEnd.getClose() != null) {
                BigDecimal poleMove = poleEnd.getClose().subtract(poleStart.getClose());
                BigDecimal poleMovePercent = poleStart.getClose().compareTo(BigDecimal.ZERO) > 0 ?
                    poleMove.divide(poleStart.getClose(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                // Check for range compression in pennant area
                BigDecimal pennantHigh = pennantStart.getHigh();
                BigDecimal pennantLow = pennantStart.getLow();
                for (int i = startIndex + 4; i < candles.size(); i++) {
                    if (candles.get(i).getHigh().compareTo(pennantHigh) > 0) pennantHigh = candles.get(i).getHigh();
                    if (candles.get(i).getLow().compareTo(pennantLow) < 0) pennantLow = candles.get(i).getLow();
                }
                BigDecimal pennantRange = pennantHigh.subtract(pennantLow);
                BigDecimal initialRange = poleEnd.getHigh().subtract(poleEnd.getLow());
                
                if (initialRange.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal compression = pennantRange.divide(initialRange, 4, RoundingMode.HALF_UP);
                    
                    // Bullish pennant: Strong up move with converging consolidation
                    if (poleMovePercent.compareTo(BigDecimal.valueOf(3)) > 0 &&
                        compression.compareTo(BigDecimal.valueOf(0.7)) < 0) {
                        ChartPattern pattern = new ChartPattern(
                            ChartPattern.PatternType.BULLISH_PENNANT,
                            "Bullish Pennant - Continuation Pattern",
                            BigDecimal.valueOf(75)
                        );
                        analysis.addPattern(pattern);
                        analysis.addSignal("Bullish Pennant");
                        return;
                    }
                    
                    // Bearish pennant: Strong down move with converging consolidation
                    if (poleMovePercent.compareTo(BigDecimal.valueOf(-3)) < 0 &&
                        compression.compareTo(BigDecimal.valueOf(0.7)) < 0) {
                        ChartPattern pattern = new ChartPattern(
                            ChartPattern.PatternType.BEARISH_PENNANT,
                            "Bearish Pennant - Continuation Pattern",
                            BigDecimal.valueOf(75)
                        );
                        analysis.addPattern(pattern);
                        analysis.addSignal("Bearish Pennant");
                    }
                }
            }
        }
    }
    
    /**
     * Detect Wedge Patterns (Rising Wedge - Bearish, Falling Wedge - Bullish)
     */
    private void detectWedgePatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 10) return;
        
        int startIndex = Math.max(0, candles.size() - 20);
        
        // Calculate trend of highs and lows
        BigDecimal firstHigh = candles.get(startIndex).getHigh();
        BigDecimal lastHigh = candles.get(candles.size() - 1).getHigh();
        BigDecimal firstLow = candles.get(startIndex).getLow();
        BigDecimal lastLow = candles.get(candles.size() - 1).getLow();
        
        // RISING WEDGE: Both highs and lows rising, but highs rising faster (bearish)
        if (lastHigh.compareTo(firstHigh) > 0 && lastLow.compareTo(firstLow) > 0) {
            BigDecimal highMove = lastHigh.subtract(firstHigh);
            BigDecimal lowMove = lastLow.subtract(firstLow);
            
            if (firstHigh.compareTo(BigDecimal.ZERO) > 0 && firstLow.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal highMovePercent = highMove.divide(firstHigh, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                BigDecimal lowMovePercent = lowMove.divide(firstLow, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                
                // Highs rising faster than lows (converging upward)
                if (highMovePercent.compareTo(lowMovePercent.multiply(BigDecimal.valueOf(1.2))) > 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.RISING_WEDGE,
                        "Rising Wedge - Bearish Reversal",
                        BigDecimal.valueOf(70)
                    );
                    analysis.addPattern(pattern);
                    analysis.addSignal("Rising Wedge");
                    return;
                }
            }
        }
        
        // FALLING WEDGE: Both highs and lows falling, but lows falling faster (bullish)
        if (lastHigh.compareTo(firstHigh) < 0 && lastLow.compareTo(firstLow) < 0) {
            BigDecimal highMove = firstHigh.subtract(lastHigh);
            BigDecimal lowMove = firstLow.subtract(lastLow);
            
            if (firstHigh.compareTo(BigDecimal.ZERO) > 0 && firstLow.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal highMovePercent = highMove.divide(firstHigh, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                BigDecimal lowMovePercent = lowMove.divide(firstLow, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                
                // Lows falling faster than highs (converging downward)
                if (lowMovePercent.compareTo(highMovePercent.multiply(BigDecimal.valueOf(1.2))) > 0) {
                    ChartPattern pattern = new ChartPattern(
                        ChartPattern.PatternType.FALLING_WEDGE,
                        "Falling Wedge - Bullish Reversal",
                        BigDecimal.valueOf(70)
                    );
                    analysis.addPattern(pattern);
                    analysis.addSignal("Falling Wedge");
                }
            }
        }
    }
    
    /**
     * Detect Cup and Handle Pattern (Bullish Continuation)
     */
    private void detectCupAndHandle(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 20) return; // Need enough candles for cup formation
        
        int startIndex = Math.max(0, candles.size() - 40);
        
        // Find the cup (U-shaped bottom)
        BigDecimal leftRim = candles.get(startIndex).getHigh();
        BigDecimal cupBottom = candles.get(startIndex).getLow();
        BigDecimal rightRim = BigDecimal.ZERO;
        int rightRimIndex = -1;
        
        // Find left rim (high point)
        for (int i = startIndex; i < startIndex + 20; i++) {
            if (candles.get(i).getHigh().compareTo(leftRim) > 0) {
                leftRim = candles.get(i).getHigh();
            }
        }
        
        // Find cup bottom (lowest point)
        for (int i = startIndex; i < startIndex + 20; i++) {
            if (candles.get(i).getLow().compareTo(cupBottom) < 0) {
                cupBottom = candles.get(i).getLow();
            }
        }
        
        // Find right rim (similar height to left rim)
        for (int i = startIndex + 20; i < candles.size() - 5; i++) {
            if (candles.get(i).getHigh().compareTo(rightRim) > 0) {
                rightRim = candles.get(i).getHigh();
                rightRimIndex = i;
            }
        }
        
        // Validate cup: Rims should be similar height (within 5%)
        if (rightRimIndex > 0 && leftRim.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rimDiff = leftRim.subtract(rightRim).abs();
            BigDecimal avgRim = leftRim.add(rightRim).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal rimDiffPercent = avgRim.compareTo(BigDecimal.ZERO) > 0 ?
                rimDiff.divide(avgRim, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.valueOf(100);
            
            // Check for handle (small pullback after right rim)
            if (rimDiffPercent.compareTo(BigDecimal.valueOf(5)) < 0 && rightRimIndex < candles.size() - 3) {
                CandleData handleStart = candles.get(rightRimIndex);
                CandleData handleEnd = candles.get(candles.size() - 1);
                
                // Handle should be a small pullback (5-15% of cup depth)
                BigDecimal cupDepth = avgRim.subtract(cupBottom);
                BigDecimal handleDepth = handleStart.getHigh().subtract(handleEnd.getLow());
                
                if (cupDepth.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal handleRatio = handleDepth.divide(cupDepth, 4, RoundingMode.HALF_UP);
                    
                    if (handleRatio.compareTo(BigDecimal.valueOf(0.05)) > 0 &&
                        handleRatio.compareTo(BigDecimal.valueOf(0.15)) < 0) {
                        ChartPattern pattern = new ChartPattern(
                            ChartPattern.PatternType.CUP_AND_HANDLE,
                            "Cup and Handle - Bullish Continuation",
                            BigDecimal.valueOf(75)
                        );
                        pattern.setPriceLevel(rightRim);
                        analysis.addPattern(pattern);
                        analysis.addSignal("Cup and Handle");
                    }
                }
            }
        }
    }
    
    /**
     * Detect Rounding Patterns (Rounding Bottom - Bullish, Rounding Top - Bearish)
     */
    private void detectRoundingPatterns(List<CandleData> candles, TimeframeAnalysis analysis) {
        if (candles.size() < 15) return;
        
        int startIndex = Math.max(0, candles.size() - 30);
        
        // Calculate average price for each third of the period
        int thirdSize = (candles.size() - startIndex) / 3;
        if (thirdSize < 3) return;
        
        BigDecimal firstThirdAvg = BigDecimal.ZERO;
        BigDecimal secondThirdAvg = BigDecimal.ZERO;
        BigDecimal thirdThirdAvg = BigDecimal.ZERO;
        
        int count1 = 0, count2 = 0, count3 = 0;
        
        for (int i = startIndex; i < startIndex + thirdSize; i++) {
            if (candles.get(i).getClose() != null) {
                firstThirdAvg = firstThirdAvg.add(candles.get(i).getClose());
                count1++;
            }
        }
        if (count1 > 0) firstThirdAvg = firstThirdAvg.divide(BigDecimal.valueOf(count1), 2, RoundingMode.HALF_UP);
        
        for (int i = startIndex + thirdSize; i < startIndex + 2 * thirdSize; i++) {
            if (candles.get(i).getClose() != null) {
                secondThirdAvg = secondThirdAvg.add(candles.get(i).getClose());
                count2++;
            }
        }
        if (count2 > 0) secondThirdAvg = secondThirdAvg.divide(BigDecimal.valueOf(count2), 2, RoundingMode.HALF_UP);
        
        for (int i = startIndex + 2 * thirdSize; i < candles.size(); i++) {
            if (candles.get(i).getClose() != null) {
                thirdThirdAvg = thirdThirdAvg.add(candles.get(i).getClose());
                count3++;
            }
        }
        if (count3 > 0) thirdThirdAvg = thirdThirdAvg.divide(BigDecimal.valueOf(count3), 2, RoundingMode.HALF_UP);
        
        // ROUNDING BOTTOM: First third high, second third low, third third high (U-shape)
        if (firstThirdAvg.compareTo(BigDecimal.ZERO) > 0 && secondThirdAvg.compareTo(BigDecimal.ZERO) > 0 &&
            thirdThirdAvg.compareTo(BigDecimal.ZERO) > 0) {
            
            BigDecimal firstToSecond = firstThirdAvg.subtract(secondThirdAvg);
            BigDecimal secondToThird = thirdThirdAvg.subtract(secondThirdAvg);
            
            BigDecimal firstToSecondPercent = firstThirdAvg.compareTo(BigDecimal.ZERO) > 0 ?
                firstToSecond.divide(firstThirdAvg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            BigDecimal secondToThirdPercent = secondThirdAvg.compareTo(BigDecimal.ZERO) > 0 ?
                secondToThird.divide(secondThirdAvg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            
            // Both moves should be significant (at least 2%)
            if (firstToSecondPercent.compareTo(BigDecimal.valueOf(2)) > 0 &&
                secondToThirdPercent.compareTo(BigDecimal.valueOf(2)) > 0 &&
                thirdThirdAvg.compareTo(firstThirdAvg) >= 0) { // Third should be at least as high as first
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.ROUNDING_BOTTOM,
                    "Rounding Bottom - Bullish Reversal",
                    BigDecimal.valueOf(70)
                );
                pattern.setPriceLevel(secondThirdAvg);
                analysis.addPattern(pattern);
                analysis.addSignal("Rounding Bottom");
                return;
            }
            
            // ROUNDING TOP: First third low, second third high, third third low (inverted U-shape)
            BigDecimal secondToFirst = secondThirdAvg.subtract(firstThirdAvg);
            BigDecimal secondToThirdDown = secondThirdAvg.subtract(thirdThirdAvg);
            
            BigDecimal secondToFirstPercent = firstThirdAvg.compareTo(BigDecimal.ZERO) > 0 ?
                secondToFirst.divide(firstThirdAvg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            BigDecimal secondToThirdDownPercent = secondThirdAvg.compareTo(BigDecimal.ZERO) > 0 ?
                secondToThirdDown.divide(secondThirdAvg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            
            if (secondToFirstPercent.compareTo(BigDecimal.valueOf(2)) > 0 &&
                secondToThirdDownPercent.compareTo(BigDecimal.valueOf(2)) > 0 &&
                thirdThirdAvg.compareTo(firstThirdAvg) <= 0) { // Third should be at least as low as first
                ChartPattern pattern = new ChartPattern(
                    ChartPattern.PatternType.ROUNDING_TOP,
                    "Rounding Top - Bearish Reversal",
                    BigDecimal.valueOf(70)
                );
                pattern.setPriceLevel(secondThirdAvg);
                analysis.addPattern(pattern);
                analysis.addSignal("Rounding Top");
            }
        }
    }
    
    /**
     * Deduplicate signals to avoid repeating the same signal
     */
    private void deduplicateSignals(TimeframeAnalysis analysis) {
        List<String> signals = analysis.getSignals();
        if (signals == null || signals.isEmpty()) return;
        
        // Remove duplicates while preserving order
        List<String> uniqueSignals = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        for (String signal : signals) {
            if (!seen.contains(signal)) {
                uniqueSignals.add(signal);
                seen.add(signal);
            }
        }
        
        analysis.setSignals(uniqueSignals);
    }
    
    /**
     * Determine trend for a timeframe
     */
    private void determineTimeframeTrend(List<CandleData> candles, TimeframeAnalysis analysis, BigDecimal ema200) {
        // Validate inputs
        if (analysis == null) {
            return;
        }
        
        if (candles == null || candles.isEmpty()) {
            analysis.setTrend("NEUTRAL");
            analysis.setTrendStrength(BigDecimal.ZERO);
            return;
        }
        
        int bullishSignals = 0;
        int bearishSignals = 0;
        
        // Count bullish and bearish patterns with null safety
        if (analysis.getPatterns() != null) {
            for (ChartPattern pattern : analysis.getPatterns()) {
                if (pattern == null || pattern.getType() == null) {
                    continue;
                }
                ChartPattern.PatternType type = pattern.getType();
                
                // Bullish patterns
                if (type == ChartPattern.PatternType.BULLISH_ENGULFING ||
                    type == ChartPattern.PatternType.ORDER_BLOCK_BULLISH ||
                    type == ChartPattern.PatternType.W_PATTERN ||
                    type == ChartPattern.PatternType.INVERSE_HEAD_AND_SHOULDERS ||
                    type == ChartPattern.PatternType.HAMMER ||
                    type == ChartPattern.PatternType.INVERTED_HAMMER ||
                    type == ChartPattern.PatternType.BULLISH_HARAMI ||
                    type == ChartPattern.PatternType.PIERCING_PATTERN ||
                    type == ChartPattern.PatternType.MORNING_STAR ||
                    type == ChartPattern.PatternType.THREE_WHITE_SOLDIERS ||
                    type == ChartPattern.PatternType.ASCENDING_TRIANGLE ||
                    type == ChartPattern.PatternType.BULLISH_FLAG ||
                    type == ChartPattern.PatternType.BULLISH_PENNANT ||
                    type == ChartPattern.PatternType.FALLING_WEDGE ||
                    type == ChartPattern.PatternType.CUP_AND_HANDLE ||
                    type == ChartPattern.PatternType.ROUNDING_BOTTOM ||
                    type == ChartPattern.PatternType.ABOVE_200_EMA) {
                    bullishSignals++;
                } 
                // Bearish patterns
                else if (type == ChartPattern.PatternType.BEARISH_ENGULFING ||
                         type == ChartPattern.PatternType.ORDER_BLOCK_BEARISH ||
                         type == ChartPattern.PatternType.M_PATTERN ||
                         type == ChartPattern.PatternType.HEAD_AND_SHOULDERS ||
                         type == ChartPattern.PatternType.SHOOTING_STAR ||
                         type == ChartPattern.PatternType.HANGING_MAN ||
                         type == ChartPattern.PatternType.BEARISH_HARAMI ||
                         type == ChartPattern.PatternType.DARK_CLOUD_COVER ||
                         type == ChartPattern.PatternType.EVENING_STAR ||
                         type == ChartPattern.PatternType.THREE_BLACK_CROWS ||
                         type == ChartPattern.PatternType.DESCENDING_TRIANGLE ||
                         type == ChartPattern.PatternType.BEARISH_FLAG ||
                         type == ChartPattern.PatternType.BEARISH_PENNANT ||
                         type == ChartPattern.PatternType.RISING_WEDGE ||
                         type == ChartPattern.PatternType.ROUNDING_TOP ||
                         type == ChartPattern.PatternType.BELOW_200_EMA) {
                    bearishSignals++;
                }
                // Neutral patterns (Doji, Symmetrical Triangle) don't add to either side
            }
        }
        
        // Check price action relative to EMA
        CandleData latest = candles.get(candles.size() - 1);
        if (latest != null && latest.getClose() != null && ema200 != null) {
            if (latest.getClose().compareTo(ema200) > 0) {
                bullishSignals++;
            } else if (latest.getClose().compareTo(ema200) < 0) {
                bearishSignals++;
            }
            // If price equals EMA, don't add to either side (neutral)
        }
        
        // Determine trend
        if (bullishSignals > bearishSignals) {
            analysis.setTrend("BULLISH");
            analysis.setTrendStrength(BigDecimal.valueOf(Math.min(90, 50 + (bullishSignals - bearishSignals) * 10)));
        } else if (bearishSignals > bullishSignals) {
            analysis.setTrend("BEARISH");
            analysis.setTrendStrength(BigDecimal.valueOf(Math.min(90, 50 + (bearishSignals - bullishSignals) * 10)));
        } else {
            analysis.setTrend("NEUTRAL");
            analysis.setTrendStrength(BigDecimal.valueOf(50));
        }
    }
    
    /**
     * Calculate overall trend across all timeframes
     */
    private void calculateOverallTrend(ChartAnalysisResult result) {
        if (result == null) {
            return;
        }
        
        Map<String, TimeframeAnalysis> timeframes = result.getTimeframes();
        if (timeframes == null || timeframes.isEmpty()) {
            result.setTrend("NEUTRAL");
            result.setConfidence(BigDecimal.ZERO);
            return;
        }
        
        int bullishCount = 0;
        int bearishCount = 0;
        BigDecimal totalConfidence = BigDecimal.ZERO;
        int count = 0;
        
        // Weight higher timeframes more
        Map<String, Integer> timeframeWeights = new HashMap<>();
        timeframeWeights.put("15m", 1);
        timeframeWeights.put("1h", 2);
        timeframeWeights.put("4h", 3);
        timeframeWeights.put("1d", 5);
        timeframeWeights.put("1w", 8);
        timeframeWeights.put("1M", 10);
        
        for (Map.Entry<String, TimeframeAnalysis> entry : timeframes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            
            TimeframeAnalysis tf = entry.getValue();
            int weight = timeframeWeights.getOrDefault(entry.getKey(), 1);
            
            String trend = tf.getTrend();
            BigDecimal trendStrength = tf.getTrendStrength();
            
            if (trend != null && trendStrength != null) {
                if ("BULLISH".equals(trend)) {
                    bullishCount += weight;
                    totalConfidence = totalConfidence.add(trendStrength.multiply(BigDecimal.valueOf(weight)));
                } else if ("BEARISH".equals(trend)) {
                    bearishCount += weight;
                    totalConfidence = totalConfidence.add(trendStrength.multiply(BigDecimal.valueOf(weight)));
                }
                count += weight;
            }
        }
        
        // Determine overall trend
        if (bullishCount > bearishCount) {
            result.setTrend("BULLISH");
        } else if (bearishCount > bullishCount) {
            result.setTrend("BEARISH");
        } else {
            result.setTrend("NEUTRAL");
        }
        
        // Calculate confidence
        if (count > 0) {
            BigDecimal avgConfidence = totalConfidence.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            result.setConfidence(avgConfidence);
        } else {
            result.setConfidence(BigDecimal.valueOf(50));
        }
    }
    
    /**
     * Analyze and categorize all detected patterns
     */
    private void analyzePatterns(ChartAnalysisResult result) {
        // Validate result
        if (result == null) {
            return;
        }
        
        Map<String, TimeframeAnalysis> timeframes = result.getTimeframes();
        if (timeframes == null) {
            timeframes = new HashMap<>();
            result.setTimeframes(timeframes);
        }
        
        Set<String> allPatternsSet = new LinkedHashSet<>();
        Map<String, List<String>> patternsByTf = new HashMap<>();
        Map<String, Integer> patternCounts = new HashMap<>();
        
        // Collect all patterns from all timeframes with null safety
        for (Map.Entry<String, TimeframeAnalysis> entry : timeframes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            
            String tf = entry.getKey();
            TimeframeAnalysis analysis = entry.getValue();
            
            // Ensure patterns list exists
            if (analysis.getPatterns() == null) {
                continue;
            }
            
            List<String> tfPatterns = new ArrayList<>();
            
            for (ChartPattern pattern : analysis.getPatterns()) {
                if (pattern == null || pattern.getType() == null) {
                    continue;
                }
                
                String patternName = pattern.getType().name();
                String patternDisplay = formatPatternName(patternName);
                
                if (patternDisplay != null && !patternDisplay.isEmpty()) {
                    allPatternsSet.add(patternDisplay);
                    tfPatterns.add(patternDisplay);
                    
                    // Count pattern occurrences
                    patternCounts.put(patternDisplay, patternCounts.getOrDefault(patternDisplay, 0) + 1);
                }
            }
            
            if (!tfPatterns.isEmpty()) {
                patternsByTf.put(tf, tfPatterns);
            }
        }
        
        result.setAllPatternsDetected(new ArrayList<>(allPatternsSet));
        result.setPatternsByTimeframe(patternsByTf);
        
        // Identify primary patterns (most significant/recent, highest confidence)
        List<String> primaryPatterns = identifyPrimaryPatterns(timeframes, patternCounts);
        result.setPrimaryPatterns(primaryPatterns != null ? primaryPatterns : new ArrayList<>());
        
        // Classify stock under pattern categories (use overall trend for better classification)
        String overallTrend = result.getTrend() != null ? result.getTrend() : "NEUTRAL";
        Map<String, String> classification = classifyStockPatterns(timeframes, primaryPatterns, overallTrend);
        result.setPatternClassification(classification != null ? classification : new HashMap<>());
    }
    
    /**
     * Format pattern name for display
     * Maps all pattern enum names to readable display names
     */
    private String formatPatternName(String patternType) {
        if (patternType == null) return "Unknown Pattern";
        
        // Map enum names to readable display names
        Map<String, String> patternNames = new HashMap<>();
        patternNames.put("FAIR_VALUE_GAP", "Fair Value Gap");
        patternNames.put("ORDER_BLOCK_BULLISH", "Bullish Order Block");
        patternNames.put("ORDER_BLOCK_BEARISH", "Bearish Order Block");
        patternNames.put("BULLISH_ENGULFING", "Bullish Engulfing");
        patternNames.put("BEARISH_ENGULFING", "Bearish Engulfing");
        patternNames.put("W_PATTERN", "W Pattern");
        patternNames.put("M_PATTERN", "M Pattern");
        patternNames.put("HEAD_AND_SHOULDERS", "Head and Shoulders");
        patternNames.put("INVERSE_HEAD_AND_SHOULDERS", "Inverse Head and Shoulders");
        patternNames.put("HAMMER", "Hammer");
        patternNames.put("SHOOTING_STAR", "Shooting Star");
        patternNames.put("HANGING_MAN", "Hanging Man");
        patternNames.put("INVERTED_HAMMER", "Inverted Hammer");
        patternNames.put("DOJI", "Doji");
        patternNames.put("BULLISH_HARAMI", "Bullish Harami");
        patternNames.put("BEARISH_HARAMI", "Bearish Harami");
        patternNames.put("PIERCING_PATTERN", "Piercing Pattern");
        patternNames.put("DARK_CLOUD_COVER", "Dark Cloud Cover");
        patternNames.put("MORNING_STAR", "Morning Star");
        patternNames.put("EVENING_STAR", "Evening Star");
        patternNames.put("THREE_WHITE_SOLDIERS", "Three White Soldiers");
        patternNames.put("THREE_BLACK_CROWS", "Three Black Crows");
        patternNames.put("ASCENDING_TRIANGLE", "Ascending Triangle");
        patternNames.put("DESCENDING_TRIANGLE", "Descending Triangle");
        patternNames.put("SYMMETRICAL_TRIANGLE", "Symmetrical Triangle");
        patternNames.put("BULLISH_FLAG", "Bullish Flag");
        patternNames.put("BEARISH_FLAG", "Bearish Flag");
        patternNames.put("BULLISH_PENNANT", "Bullish Pennant");
        patternNames.put("BEARISH_PENNANT", "Bearish Pennant");
        patternNames.put("RISING_WEDGE", "Rising Wedge");
        patternNames.put("FALLING_WEDGE", "Falling Wedge");
        patternNames.put("CUP_AND_HANDLE", "Cup and Handle");
        patternNames.put("ROUNDING_BOTTOM", "Rounding Bottom");
        patternNames.put("ROUNDING_TOP", "Rounding Top");
        patternNames.put("ABOVE_200_EMA", "Above 200 EMA");
        patternNames.put("BELOW_200_EMA", "Below 200 EMA");
        
        // Return mapped name or formatted default
        String mapped = patternNames.get(patternType);
        if (mapped != null) {
            return mapped;
        }
        
        // Fallback: format the enum name
        String formatted = patternType.replace("_", " ").toLowerCase();
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Identify primary patterns (most significant ones)
     * Prioritizes higher timeframes and avoids conflicting patterns
     */
    private List<String> identifyPrimaryPatterns(Map<String, TimeframeAnalysis> timeframes, 
                                                  Map<String, Integer> patternCounts) {
        List<String> primary = new ArrayList<>();
        
        // Priority: Higher timeframes first, then by confidence
        String[] priorityTimeframes = {"1M", "1w", "1d", "4h", "1h", "15m"};
        
        // Track conflicting patterns (e.g., both bullish and bearish engulfing)
        Set<String> bullishPatterns = new HashSet<>();
        Set<String> bearishPatterns = new HashSet<>();
        
        // Collect patterns from higher timeframes with high confidence
        for (String tf : priorityTimeframes) {
            TimeframeAnalysis analysis = timeframes.get(tf);
            if (analysis != null) {
                for (ChartPattern pattern : analysis.getPatterns()) {
                    if (pattern.getConfidence().compareTo(BigDecimal.valueOf(70)) >= 0) {
                        String patternName = formatPatternName(pattern.getType().name());
                        
                        // Categorize patterns into bullish/bearish (exact matches to avoid false positives)
                        // Bullish patterns
                        if (patternName.equals("W Pattern") || patternName.equals("Inverse Head and Shoulders") ||
                            patternName.equals("Hammer") || patternName.equals("Inverted Hammer") ||
                            patternName.equals("Morning Star") || patternName.equals("Three White Soldiers") ||
                            patternName.equals("Ascending Triangle") || patternName.equals("Bullish Flag") ||
                            patternName.equals("Bullish Pennant") || patternName.equals("Falling Wedge") ||
                            patternName.equals("Cup and Handle") || patternName.equals("Rounding Bottom") ||
                            patternName.equals("Piercing Pattern") || patternName.equals("Bullish Harami") ||
                            patternName.equals("Bullish Engulfing") || patternName.equals("Bullish Order Block") ||
                            patternName.equals("Above 200 EMA")) {
                            bullishPatterns.add(patternName);
                        } 
                        // Bearish patterns
                        else if (patternName.equals("M Pattern") || patternName.equals("Head and Shoulders") ||
                                 patternName.equals("Shooting Star") || patternName.equals("Hanging Man") ||
                                 patternName.equals("Evening Star") || patternName.equals("Three Black Crows") ||
                                 patternName.equals("Descending Triangle") || patternName.equals("Bearish Flag") ||
                                 patternName.equals("Bearish Pennant") || patternName.equals("Rising Wedge") ||
                                 patternName.equals("Rounding Top") || patternName.equals("Dark Cloud Cover") ||
                                 patternName.equals("Bearish Harami") || patternName.equals("Bearish Engulfing") ||
                                 patternName.equals("Bearish Order Block") || patternName.equals("Below 200 EMA")) {
                            bearishPatterns.add(patternName);
                        }
                        // Neutral patterns (Doji, Symmetrical Triangle, Fair Value Gap) - don't categorize
                        
                        // Add non-conflicting patterns
                        if (!primary.contains(patternName)) {
                            primary.add(patternName);
                        }
                    }
                }
            }
        }
        
        // Remove conflicting patterns - if both bullish and bearish engulfing exist, remove both
        if (bullishPatterns.contains("Bullish Engulfing") && bearishPatterns.contains("Bearish Engulfing")) {
            System.err.println("WARNING: Conflicting engulfing patterns detected - removing both from primary patterns");
            primary.remove("Bullish Engulfing");
            primary.remove("Bearish Engulfing");
        }
        
        // If no high-confidence patterns, add most frequent ones (excluding conflicts)
        if (primary.isEmpty() && !patternCounts.isEmpty()) {
            patternCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .forEach(entry -> {
                    String pattern = entry.getKey();
                    // Don't add if it conflicts with existing patterns
                    if (!(pattern.contains("Bullish Engulfing") && bearishPatterns.contains("Bearish Engulfing")) &&
                        !(pattern.contains("Bearish Engulfing") && bullishPatterns.contains("Bullish Engulfing"))) {
                        primary.add(pattern);
                    }
                });
        }
        
        return primary;
    }
    
    /**
     * Classify stock under pattern categories
     */
    private Map<String, String> classifyStockPatterns(Map<String, TimeframeAnalysis> timeframes, 
                                                      List<String> primaryPatterns, String overallTrend) {
        Map<String, String> classification = new HashMap<>();
        
        // Determine main pattern category based on primary patterns and higher timeframes
        String mainCategory = "No Clear Pattern";
        String reversalPattern = "None";
        String continuationPattern = "None";
        String trendIndicator = "None";
        
        // Priority 1: Check for reversal patterns (most significant)
        // Major reversal patterns
        if (primaryPatterns.contains("W Pattern") || primaryPatterns.contains("Double Bottom") ||
            primaryPatterns.contains("Inverse Head and Shoulders") || primaryPatterns.contains("Rounding Bottom")) {
            mainCategory = "Bullish Reversal Pattern";
            if (primaryPatterns.contains("W Pattern")) {
                reversalPattern = "W Pattern (Double Bottom)";
            } else if (primaryPatterns.contains("Inverse Head and Shoulders")) {
                reversalPattern = "Inverse Head and Shoulders";
            } else if (primaryPatterns.contains("Rounding Bottom")) {
                reversalPattern = "Rounding Bottom";
            }
        } else if (primaryPatterns.contains("M Pattern") || primaryPatterns.contains("Double Top") ||
                   primaryPatterns.contains("Head and Shoulders") || primaryPatterns.contains("Rounding Top")) {
            mainCategory = "Bearish Reversal Pattern";
            if (primaryPatterns.contains("M Pattern")) {
                reversalPattern = "M Pattern (Double Top)";
            } else if (primaryPatterns.contains("Head and Shoulders")) {
                reversalPattern = "Head and Shoulders";
            } else if (primaryPatterns.contains("Rounding Top")) {
                reversalPattern = "Rounding Top";
            }
        }
        
        // Priority 2: Check for trend indicators from higher timeframes
        String[] priorityTimeframes = {"1M", "1w", "1d"};
        for (String tf : priorityTimeframes) {
            TimeframeAnalysis analysis = timeframes.get(tf);
            if (analysis != null) {
                if ("ABOVE".equals(analysis.getEmaPosition())) {
                    trendIndicator = "Above 200 EMA (Bullish Trend)";
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Trend Pattern";
                    }
                    break;
                } else if ("BELOW".equals(analysis.getEmaPosition())) {
                    trendIndicator = "Below 200 EMA (Bearish Trend)";
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bearish Trend Pattern";
                    }
                    break;
                }
            }
        }
        
        // Priority 3: Check for continuation/reversal patterns based on overall trend
        // A pattern that goes AGAINST the trend is a reversal signal
        // A pattern that goes WITH the trend is a continuation signal
        if (mainCategory.equals("No Clear Pattern") || mainCategory.contains("Trend")) {
            // Check for major continuation patterns
            boolean hasBullishEngulfing = primaryPatterns.contains("Bullish Engulfing");
            boolean hasBearishEngulfing = primaryPatterns.contains("Bearish Engulfing");
            boolean hasCupAndHandle = primaryPatterns.contains("Cup and Handle");
            boolean hasBullishFlag = primaryPatterns.contains("Bullish Flag");
            boolean hasBearishFlag = primaryPatterns.contains("Bearish Flag");
            boolean hasAscendingTriangle = primaryPatterns.contains("Ascending Triangle");
            boolean hasDescendingTriangle = primaryPatterns.contains("Descending Triangle");
            boolean hasThreeWhiteSoldiers = primaryPatterns.contains("Three White Soldiers");
            boolean hasThreeBlackCrows = primaryPatterns.contains("Three Black Crows");
            
            // Check for reversal candlestick patterns
            boolean hasMorningStar = primaryPatterns.contains("Morning Star");
            boolean hasEveningStar = primaryPatterns.contains("Evening Star");
            boolean hasHammer = primaryPatterns.contains("Hammer");
            boolean hasShootingStar = primaryPatterns.contains("Shooting Star");
            
            // If both exist, prefer the one matching overall trend
            if (hasBullishEngulfing && hasBearishEngulfing) {
                if ("BULLISH".equals(overallTrend)) {
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Continuation Pattern";
                    }
                    continuationPattern = "Bullish Engulfing";
                } else if ("BEARISH".equals(overallTrend)) {
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bearish Continuation Pattern";
                    }
                    continuationPattern = "Bearish Engulfing";
                } else {
                    // Neutral trend - prefer bullish if price above EMA
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Continuation Pattern";
                    }
                    continuationPattern = "Bullish Engulfing";
                }
            } else if (hasBullishEngulfing) {
                // Bullish Engulfing pattern detected
                if ("BULLISH".equals(overallTrend)) {
                    // Pattern aligns with trend = Continuation
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Continuation Pattern";
                    }
                    continuationPattern = "Bullish Engulfing";
                } else if ("BEARISH".equals(overallTrend)) {
                    // Pattern opposes trend = Potential Reversal
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Reversal Signal";
                    }
                    reversalPattern = "Bullish Engulfing (Potential Reversal)";
                } else {
                    // Neutral trend
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bullish Continuation Pattern";
                    }
                    continuationPattern = "Bullish Engulfing";
                }
            } else if (hasBearishEngulfing) {
                // Bearish Engulfing pattern detected
                if ("BEARISH".equals(overallTrend)) {
                    // Pattern aligns with trend = Continuation
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bearish Continuation Pattern";
                    }
                    continuationPattern = "Bearish Engulfing";
                } else if ("BULLISH".equals(overallTrend)) {
                    // Pattern opposes trend = Potential Reversal
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bearish Reversal Signal";
                    }
                    reversalPattern = "Bearish Engulfing (Potential Reversal)";
                } else {
                    // Neutral trend
                    if (mainCategory.equals("No Clear Pattern")) {
                        mainCategory = "Bearish Continuation Pattern";
                    }
                    continuationPattern = "Bearish Engulfing";
                }
            }
            
            // Check for other continuation patterns
            if (hasCupAndHandle && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bullish Continuation Pattern";
                continuationPattern = "Cup and Handle";
            } else if (hasBullishFlag && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bullish Continuation Pattern";
                continuationPattern = "Bullish Flag";
            } else if (hasBearishFlag && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bearish Continuation Pattern";
                continuationPattern = "Bearish Flag";
            } else if (hasAscendingTriangle && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bullish Continuation Pattern";
                continuationPattern = "Ascending Triangle";
            } else if (hasDescendingTriangle && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bearish Continuation Pattern";
                continuationPattern = "Descending Triangle";
            } else if (hasThreeWhiteSoldiers && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Strong Bullish Continuation Pattern";
                continuationPattern = "Three White Soldiers";
            } else if (hasThreeBlackCrows && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Strong Bearish Continuation Pattern";
                continuationPattern = "Three Black Crows";
            }
            
            // Check for reversal candlestick patterns
            if (hasMorningStar && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bullish Reversal Signal";
                reversalPattern = "Morning Star";
            } else if (hasEveningStar && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bearish Reversal Signal";
                reversalPattern = "Evening Star";
            } else if (hasHammer && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bullish Reversal Signal";
                reversalPattern = "Hammer";
            } else if (hasShootingStar && mainCategory.equals("No Clear Pattern")) {
                mainCategory = "Bearish Reversal Signal";
                reversalPattern = "Shooting Star";
            }
        }
        
        // Priority 4: Check for order blocks and FVG (support/resistance)
        if (mainCategory.equals("No Clear Pattern")) {
            boolean hasBullishOB = primaryPatterns.contains("Bullish Order Block");
            boolean hasBearishOB = primaryPatterns.contains("Bearish Order Block");
            boolean hasFVG = primaryPatterns.contains("Fair Value Gap");
            
            if (hasBullishOB || (hasFVG && !hasBearishOB)) {
                mainCategory = "Support/Resistance Pattern (Bullish Bias)";
            } else if (hasBearishOB || (hasFVG && !hasBullishOB)) {
                mainCategory = "Support/Resistance Pattern (Bearish Bias)";
            } else if (hasFVG) {
                mainCategory = "Support/Resistance Pattern";
            }
        }
        
        classification.put("mainCategory", mainCategory);
        classification.put("reversalPattern", reversalPattern);
        classification.put("continuationPattern", continuationPattern);
        classification.put("trendIndicator", trendIndicator);
        
        return classification;
    }
    
    /**
     * Generate summary text - concise and focused on key patterns
     */
    private void generateSummary(ChartAnalysisResult result) {
        if (result == null) {
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        Map<String, TimeframeAnalysis> timeframes = result.getTimeframes();
        
        // Safe trend and confidence
        String trend = result.getTrend() != null ? result.getTrend() : "NEUTRAL";
        BigDecimal confidence = result.getConfidence() != null ? result.getConfidence() : BigDecimal.ZERO;
        
        summary.append("Overall trend: ").append(trend).append(" with ").append(confidence).append("% confidence. ");
        
        // Add pattern classification with null safety
        Map<String, String> classification = result.getPatternClassification();
        if (classification != null) {
            String mainCategory = classification.get("mainCategory");
            if (mainCategory != null && !mainCategory.equals("No Clear Pattern")) {
                summary.append("Stock classified as: ").append(mainCategory).append(". ");
            }
        }
        
        // Add primary patterns with null safety
        List<String> primaryPatterns = result.getPrimaryPatterns();
        if (primaryPatterns != null && !primaryPatterns.isEmpty()) {
            summary.append("Key patterns: ");
            int count = 0;
            for (String pattern : primaryPatterns) {
                if (pattern == null || pattern.isEmpty()) continue;
                if (count >= 3) break; // Limit to 3 key patterns
                if (count > 0) summary.append(", ");
                summary.append(pattern);
                count++;
            }
            if (count > 0) {
                summary.append(". ");
            }
        }
        
        // Add key signals from daily and weekly timeframes only with null safety
        if (timeframes != null) {
            TimeframeAnalysis daily = timeframes.get("1d");
            if (daily != null && daily.getSignals() != null && !daily.getSignals().isEmpty()) {
                List<String> keySignals = new ArrayList<>();
                int maxSignals = Math.min(2, daily.getSignals().size());
                for (int i = 0; i < maxSignals; i++) {
                    String signal = daily.getSignals().get(i);
                    if (signal != null && !signal.isEmpty()) {
                        keySignals.add(signal);
                    }
                }
                if (!keySignals.isEmpty()) {
                    summary.append("Daily: ").append(String.join(", ", keySignals)).append(". ");
                }
            }
            
            TimeframeAnalysis weekly = timeframes.get("1w");
            if (weekly != null && weekly.getSignals() != null && !weekly.getSignals().isEmpty()) {
                List<String> keySignals = new ArrayList<>();
                int maxSignals = Math.min(2, weekly.getSignals().size());
                for (int i = 0; i < maxSignals; i++) {
                    String signal = weekly.getSignals().get(i);
                    if (signal != null && !signal.isEmpty()) {
                        keySignals.add(signal);
                    }
                }
                if (!keySignals.isEmpty()) {
                    summary.append("Weekly: ").append(String.join(", ", keySignals)).append(". ");
                }
            }
        }
        
        String summaryText = summary.toString();
        result.setSummary(summaryText.isEmpty() ? "Analysis completed" : summaryText.trim());
    }
    
    /**
     * Aggregate daily candles to intraday timeframes (simplified)
     */
    private List<CandleData> aggregateToTimeframe(List<CandleData> dailyCandles, String targetTimeframe) {
        // For now, return daily candles as-is
        // In production, you'd need actual intraday data
        return dailyCandles;
    }
    
    /**
     * Enhance historical candles with live market data
     */
    private List<CandleData> enhanceWithLiveData(String symbol, List<CandleData> historicalCandles, 
                                                 String timeframe, boolean[] liveDataUsed) {
        if (historicalCandles == null || historicalCandles.isEmpty()) {
            return historicalCandles;
        }
        
        try {
            // Fetch live market data
            RealTimeNseDataService.RealTimeMarketData liveData = realTimeNseDataService.getRealTimeData(symbol);
            
            if (liveData != null && liveData.getCurrentPrice() != null) {
                System.out.println("ChartAnalysisService - Enhancing " + timeframe + " with live data for " + symbol);
                
                // Get the latest historical candle
                CandleData latestHistorical = historicalCandles.get(historicalCandles.size() - 1);
                LocalDateTime latestTimestamp = latestHistorical.getTimestamp();
                LocalDateTime now = LocalDateTime.now();
                
                // Check if we need to update the latest candle or create a new one
                boolean shouldUpdateLatest = shouldUpdateCandle(latestTimestamp, now, timeframe);
                
                if (shouldUpdateLatest) {
                    // Update the latest candle with live data
                    // NOTE: This update is for current price/EMA display only
                    // Pattern detection uses the original historical candle to ensure consistency
                    updateCandleWithLiveData(latestHistorical, liveData, now);
                    liveDataUsed[0] = true;
                    System.out.println("ChartAnalysisService - Updated latest candle with live price: " + liveData.getCurrentPrice() + 
                        " (for display only, patterns use stable historical data)");
                } else {
                    // Create a new live candle
                    CandleData liveCandle = createLiveCandle(liveData, latestHistorical, now, timeframe);
                    if (liveCandle != null) {
                        historicalCandles.add(liveCandle);
                        liveDataUsed[0] = true;
                        System.out.println("ChartAnalysisService - Added new live candle with price: " + liveData.getCurrentPrice());
                    }
                }
            } else {
                // Fallback: try comprehensive NSE data
                try {
                    com.trading.model.NseStockData nseData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
                    if (nseData != null && nseData.getLastPrice() != null) {
                        CandleData latestHistorical = historicalCandles.get(historicalCandles.size() - 1);
                        LocalDateTime now = LocalDateTime.now();
                        
                        boolean shouldUpdateLatest = shouldUpdateCandle(latestHistorical.getTimestamp(), now, timeframe);
                        
                        if (shouldUpdateLatest) {
                            // Update with NSE data
                            latestHistorical.setClose(nseData.getLastPrice());
                            if (nseData.getDayHigh() != null && nseData.getDayHigh().compareTo(latestHistorical.getHigh()) > 0) {
                                latestHistorical.setHigh(nseData.getDayHigh());
                            }
                            if (nseData.getDayLow() != null && nseData.getDayLow().compareTo(latestHistorical.getLow()) < 0) {
                                latestHistorical.setLow(nseData.getDayLow());
                            }
                            if (nseData.getTotalTradedVolume() != null) {
                                latestHistorical.setVolume(nseData.getTotalTradedVolume());
                            }
                            latestHistorical.setTimestamp(now);
                            liveDataUsed[0] = true;
                            System.out.println("ChartAnalysisService - Updated candle with NSE live data: " + nseData.getLastPrice());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ChartAnalysisService - Could not fetch live NSE data: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("ChartAnalysisService - Error enhancing with live data: " + e.getMessage());
            // Continue with historical data only
        }
        
        return historicalCandles;
    }
    
    /**
     * Determine if we should update the latest candle or create a new one
     */
    private boolean shouldUpdateCandle(LocalDateTime candleTimestamp, LocalDateTime now, String timeframe) {
        long hoursDiff = java.time.Duration.between(candleTimestamp, now).toHours();
        
        switch (timeframe) {
            case "15m":
                return hoursDiff < 1; // Update if within 1 hour
            case "1h":
                return hoursDiff < 2; // Update if within 2 hours
            case "4h":
                return hoursDiff < 8; // Update if within 8 hours
            case "1d":
                return hoursDiff < 24; // Update if same day
            case "1w":
                return hoursDiff < 168; // Update if same week
            case "1M":
                return hoursDiff < 720; // Update if same month
            default:
                return hoursDiff < 24;
        }
    }
    
    /**
     * Update existing candle with live data
     */
    private void updateCandleWithLiveData(CandleData candle, RealTimeNseDataService.RealTimeMarketData liveData, LocalDateTime now) {
        if (liveData.getCurrentPrice() != null) {
            candle.setClose(liveData.getCurrentPrice());
        }
        
        // Update high if live high is higher
        if (liveData.getHigh() != null && candle.getHigh() != null && 
            liveData.getHigh().compareTo(candle.getHigh()) > 0) {
            candle.setHigh(liveData.getHigh());
        }
        
        // Update low if live low is lower
        if (liveData.getLow() != null && candle.getLow() != null && 
            liveData.getLow().compareTo(candle.getLow()) < 0) {
            candle.setLow(liveData.getLow());
        }
        
        // Update volume if available
        if (liveData.getVolume() != null && liveData.getVolume().compareTo(BigDecimal.ZERO) > 0) {
            candle.setVolume(liveData.getVolume());
        }
        
        // Update timestamp to current time
        candle.setTimestamp(now);
    }
    
    /**
     * Create a new live candle from live market data
     */
    private CandleData createLiveCandle(RealTimeNseDataService.RealTimeMarketData liveData, 
                                       CandleData previousCandle, LocalDateTime now, String timeframe) {
        if (liveData.getCurrentPrice() == null) {
            return null;
        }
        
        // Use previous close as open for the new candle, or current price if no previous
        BigDecimal open = previousCandle != null && previousCandle.getClose() != null ? 
            previousCandle.getClose() : liveData.getCurrentPrice();
        
        BigDecimal close = liveData.getCurrentPrice();
        BigDecimal high = liveData.getHigh() != null ? liveData.getHigh() : close;
        BigDecimal low = liveData.getLow() != null ? liveData.getLow() : close;
        
        // Ensure high >= low and high/low encompass open and close
        if (high.compareTo(open) < 0) high = open;
        if (high.compareTo(close) < 0) high = close;
        if (low.compareTo(open) > 0) low = open;
        if (low.compareTo(close) > 0) low = close;
        
        BigDecimal volume = liveData.getVolume() != null ? liveData.getVolume() : BigDecimal.ZERO;
        
        return new CandleData(now, open, high, low, close, volume);
    }
    
    /**
     * Generate simulated candles when historical data is unavailable
     */
    private List<CandleData> generateSimulatedCandles(String symbol, String timeframe, int periods) {
        // Try to get current price from live data first
        try {
            RealTimeNseDataService.RealTimeMarketData liveData = realTimeNseDataService.getRealTimeData(symbol);
            if (liveData != null && liveData.getCurrentPrice() != null) {
                BigDecimal basePrice = liveData.getCurrentPrice();
                List<CandleData> candles = new ArrayList<>();
                
                // Generate simulated candles around current price
                for (int i = periods; i >= 0; i--) {
                    BigDecimal variation = basePrice.multiply(BigDecimal.valueOf(0.02 * Math.random() - 0.01));
                    BigDecimal open = basePrice.add(variation);
                    BigDecimal close = open.add(basePrice.multiply(BigDecimal.valueOf(0.01 * Math.random() - 0.005)));
                    BigDecimal high = open.max(close).add(basePrice.multiply(BigDecimal.valueOf(0.005)));
                    BigDecimal low = open.min(close).subtract(basePrice.multiply(BigDecimal.valueOf(0.005)));
                    
                    candles.add(new CandleData(
                        LocalDateTime.now().minusDays(i),
                        open, high, low, close,
                        BigDecimal.valueOf(1000000)
                    ));
                }
                
                // Add live candle at the end
                CandleData liveCandle = createLiveCandle(liveData, 
                    candles.isEmpty() ? null : candles.get(candles.size() - 1), 
                    LocalDateTime.now(), timeframe);
                if (liveCandle != null) {
                    candles.add(liveCandle);
                }
                
                return candles;
            }
        } catch (Exception e) {
            System.err.println("Error generating simulated candles with live data: " + e.getMessage());
        }
        
        // Fallback to comprehensive NSE data
        try {
            com.trading.model.NseStockData currentData = comprehensiveNseDataService.getComprehensiveStockData(symbol);
            if (currentData != null && currentData.getLastPrice() != null) {
                BigDecimal basePrice = currentData.getLastPrice();
                List<CandleData> candles = new ArrayList<>();
                
                // Generate simulated candles around current price
                for (int i = periods; i >= 0; i--) {
                    BigDecimal variation = basePrice.multiply(BigDecimal.valueOf(0.02 * Math.random() - 0.01));
                    BigDecimal open = basePrice.add(variation);
                    BigDecimal close = open.add(basePrice.multiply(BigDecimal.valueOf(0.01 * Math.random() - 0.005)));
                    BigDecimal high = open.max(close).add(basePrice.multiply(BigDecimal.valueOf(0.005)));
                    BigDecimal low = open.min(close).subtract(basePrice.multiply(BigDecimal.valueOf(0.005)));
                    
                    candles.add(new CandleData(
                        LocalDateTime.now().minusDays(i),
                        open, high, low, close,
                        BigDecimal.valueOf(1000000)
                    ));
                }
                return candles;
            }
        } catch (Exception e) {
            System.err.println("Error generating simulated candles: " + e.getMessage());
        }
        
        return new ArrayList<>();
    }
}

