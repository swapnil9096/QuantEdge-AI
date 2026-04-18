package com.trading.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {
    private String stockSymbol;
    private String stockName;
    private BigDecimal currentPrice;
    private LocalDateTime analysisTime;
    
    // Technical Analysis Results
    private TechnicalAnalysis technicalAnalysis;
    
    // Fundamental Analysis Results
    private FundamentalAnalysis fundamentalAnalysis;
    
    // Multibagger Potential
    private MultibaggerAnalysis multibaggerAnalysis;
    
    // Trading Signal
    private TradingSignal tradingSignal;
    
    // Overall Score and Recommendation
    private BigDecimal overallScore;
    private String recommendation;
    private BigDecimal confidence;
    
    // Risk Assessment
    private RiskAssessment riskAssessment;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TechnicalAnalysis {
        private BigDecimal rsi;
        private BigDecimal macd;
        private BigDecimal macdSignal;
        private BigDecimal macdHistogram;
        private BigDecimal sma20;
        private BigDecimal sma50;
        private BigDecimal bollingerUpper;
        private BigDecimal bollingerMiddle;
        private BigDecimal bollingerLower;
        private BigDecimal stochasticK;
        private BigDecimal stochasticD;
        private BigDecimal williamsR;
        private BigDecimal atr;
        private String trend;
        private String pattern;
        private BigDecimal technicalScore;
        private List<String> technicalSignals;
        
        public static TechnicalAnalysisBuilder builder() {
            return new TechnicalAnalysisBuilder();
        }
        
        public static class TechnicalAnalysisBuilder {
            private BigDecimal rsi;
            private BigDecimal macd;
            private BigDecimal macdSignal;
            private BigDecimal macdHistogram;
            private BigDecimal sma20;
            private BigDecimal sma50;
            private BigDecimal bollingerUpper;
            private BigDecimal bollingerMiddle;
            private BigDecimal bollingerLower;
            private BigDecimal stochasticK;
            private BigDecimal stochasticD;
            private BigDecimal williamsR;
            private BigDecimal atr;
            private String trend;
            private String pattern;
            private BigDecimal technicalScore;
            private List<String> technicalSignals;
            
            public TechnicalAnalysisBuilder rsi(BigDecimal rsi) {
                this.rsi = rsi;
                return this;
            }
            
            public TechnicalAnalysisBuilder macd(BigDecimal macd) {
                this.macd = macd;
                return this;
            }
            
            public TechnicalAnalysisBuilder macdSignal(BigDecimal macdSignal) {
                this.macdSignal = macdSignal;
                return this;
            }
            
            public TechnicalAnalysisBuilder macdHistogram(BigDecimal macdHistogram) {
                this.macdHistogram = macdHistogram;
                return this;
            }
            
            public TechnicalAnalysisBuilder sma20(BigDecimal sma20) {
                this.sma20 = sma20;
                return this;
            }
            
            public TechnicalAnalysisBuilder sma50(BigDecimal sma50) {
                this.sma50 = sma50;
                return this;
            }
            
            public TechnicalAnalysisBuilder bollingerUpper(BigDecimal bollingerUpper) {
                this.bollingerUpper = bollingerUpper;
                return this;
            }
            
            public TechnicalAnalysisBuilder bollingerMiddle(BigDecimal bollingerMiddle) {
                this.bollingerMiddle = bollingerMiddle;
                return this;
            }
            
            public TechnicalAnalysisBuilder bollingerLower(BigDecimal bollingerLower) {
                this.bollingerLower = bollingerLower;
                return this;
            }
            
            public TechnicalAnalysisBuilder stochasticK(BigDecimal stochasticK) {
                this.stochasticK = stochasticK;
                return this;
            }
            
            public TechnicalAnalysisBuilder stochasticD(BigDecimal stochasticD) {
                this.stochasticD = stochasticD;
                return this;
            }
            
            public TechnicalAnalysisBuilder williamsR(BigDecimal williamsR) {
                this.williamsR = williamsR;
                return this;
            }
            
            public TechnicalAnalysisBuilder atr(BigDecimal atr) {
                this.atr = atr;
                return this;
            }
            
            public TechnicalAnalysisBuilder trend(String trend) {
                this.trend = trend;
                return this;
            }
            
            public TechnicalAnalysisBuilder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }
            
            public TechnicalAnalysisBuilder technicalScore(BigDecimal technicalScore) {
                this.technicalScore = technicalScore;
                return this;
            }
            
            public TechnicalAnalysisBuilder technicalSignals(List<String> technicalSignals) {
                this.technicalSignals = technicalSignals;
                return this;
            }
            
            public TechnicalAnalysis build() {
                TechnicalAnalysis analysis = new TechnicalAnalysis();
                analysis.rsi = this.rsi;
                analysis.macd = this.macd;
                analysis.macdSignal = this.macdSignal;
                analysis.macdHistogram = this.macdHistogram;
                analysis.sma20 = this.sma20;
                analysis.sma50 = this.sma50;
                analysis.bollingerUpper = this.bollingerUpper;
                analysis.bollingerMiddle = this.bollingerMiddle;
                analysis.bollingerLower = this.bollingerLower;
                analysis.stochasticK = this.stochasticK;
                analysis.stochasticD = this.stochasticD;
                analysis.williamsR = this.williamsR;
                analysis.atr = this.atr;
                analysis.trend = this.trend;
                analysis.pattern = this.pattern;
                analysis.technicalScore = this.technicalScore;
                analysis.technicalSignals = this.technicalSignals;
                return analysis;
            }
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FundamentalAnalysis {
        private BigDecimal peRatio;
        private BigDecimal pbRatio;
        private BigDecimal debtToEquity;
        private BigDecimal roe;
        private BigDecimal roa;
        private BigDecimal revenueGrowth;
        private BigDecimal profitGrowth;
        private BigDecimal marketCap;
        private String sector;
        private String industry;
        private BigDecimal fundamentalScore;
        private List<String> fundamentalSignals;
        
        public static FundamentalAnalysisBuilder builder() {
            return new FundamentalAnalysisBuilder();
        }
        
        public static class FundamentalAnalysisBuilder {
            private BigDecimal peRatio;
            private BigDecimal pbRatio;
            private BigDecimal debtToEquity;
            private BigDecimal roe;
            private BigDecimal roa;
            private BigDecimal revenueGrowth;
            private BigDecimal profitGrowth;
            private BigDecimal marketCap;
            private String sector;
            private String industry;
            private BigDecimal fundamentalScore;
            private List<String> fundamentalSignals;
            
            public FundamentalAnalysisBuilder peRatio(BigDecimal peRatio) {
                this.peRatio = peRatio;
                return this;
            }
            
            public FundamentalAnalysisBuilder pbRatio(BigDecimal pbRatio) {
                this.pbRatio = pbRatio;
                return this;
            }
            
            public FundamentalAnalysisBuilder debtToEquity(BigDecimal debtToEquity) {
                this.debtToEquity = debtToEquity;
                return this;
            }
            
            public FundamentalAnalysisBuilder roe(BigDecimal roe) {
                this.roe = roe;
                return this;
            }
            
            public FundamentalAnalysisBuilder roa(BigDecimal roa) {
                this.roa = roa;
                return this;
            }
            
            public FundamentalAnalysisBuilder revenueGrowth(BigDecimal revenueGrowth) {
                this.revenueGrowth = revenueGrowth;
                return this;
            }
            
            public FundamentalAnalysisBuilder profitGrowth(BigDecimal profitGrowth) {
                this.profitGrowth = profitGrowth;
                return this;
            }
            
            public FundamentalAnalysisBuilder marketCap(BigDecimal marketCap) {
                this.marketCap = marketCap;
                return this;
            }
            
            public FundamentalAnalysisBuilder sector(String sector) {
                this.sector = sector;
                return this;
            }
            
            public FundamentalAnalysisBuilder industry(String industry) {
                this.industry = industry;
                return this;
            }
            
            public FundamentalAnalysisBuilder fundamentalScore(BigDecimal fundamentalScore) {
                this.fundamentalScore = fundamentalScore;
                return this;
            }
            
            public FundamentalAnalysisBuilder fundamentalSignals(List<String> fundamentalSignals) {
                this.fundamentalSignals = fundamentalSignals;
                return this;
            }
            
            public FundamentalAnalysis build() {
                FundamentalAnalysis analysis = new FundamentalAnalysis();
                analysis.peRatio = this.peRatio;
                analysis.pbRatio = this.pbRatio;
                analysis.debtToEquity = this.debtToEquity;
                analysis.roe = this.roe;
                analysis.roa = this.roa;
                analysis.revenueGrowth = this.revenueGrowth;
                analysis.profitGrowth = this.profitGrowth;
                analysis.marketCap = this.marketCap;
                analysis.sector = this.sector;
                analysis.industry = this.industry;
                analysis.fundamentalScore = this.fundamentalScore;
                analysis.fundamentalSignals = this.fundamentalSignals;
                return analysis;
            }
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MultibaggerAnalysis {
        private BigDecimal growthPotential;
        private BigDecimal marketCapGrowth;
        private BigDecimal revenueGrowth;
        private BigDecimal profitGrowth;
        private String growthPhase;
        private BigDecimal multibaggerScore;
        private List<String> multibaggerFactors;
        
        public static MultibaggerAnalysisBuilder builder() {
            return new MultibaggerAnalysisBuilder();
        }
        
        public static class MultibaggerAnalysisBuilder {
            private BigDecimal growthPotential;
            private BigDecimal marketCapGrowth;
            private BigDecimal revenueGrowth;
            private BigDecimal profitGrowth;
            private String growthPhase;
            private BigDecimal multibaggerScore;
            private List<String> multibaggerFactors;
            
            public MultibaggerAnalysisBuilder growthPotential(BigDecimal growthPotential) {
                this.growthPotential = growthPotential;
                return this;
            }
            
            public MultibaggerAnalysisBuilder marketCapGrowth(BigDecimal marketCapGrowth) {
                this.marketCapGrowth = marketCapGrowth;
                return this;
            }
            
            public MultibaggerAnalysisBuilder revenueGrowth(BigDecimal revenueGrowth) {
                this.revenueGrowth = revenueGrowth;
                return this;
            }
            
            public MultibaggerAnalysisBuilder profitGrowth(BigDecimal profitGrowth) {
                this.profitGrowth = profitGrowth;
                return this;
            }
            
            public MultibaggerAnalysisBuilder growthPhase(String growthPhase) {
                this.growthPhase = growthPhase;
                return this;
            }
            
            public MultibaggerAnalysisBuilder multibaggerScore(BigDecimal multibaggerScore) {
                this.multibaggerScore = multibaggerScore;
                return this;
            }
            
            public MultibaggerAnalysisBuilder multibaggerFactors(List<String> multibaggerFactors) {
                this.multibaggerFactors = multibaggerFactors;
                return this;
            }
            
            public MultibaggerAnalysis build() {
                MultibaggerAnalysis analysis = new MultibaggerAnalysis();
                analysis.growthPotential = this.growthPotential;
                analysis.marketCapGrowth = this.marketCapGrowth;
                analysis.revenueGrowth = this.revenueGrowth;
                analysis.profitGrowth = this.profitGrowth;
                analysis.growthPhase = this.growthPhase;
                analysis.multibaggerScore = this.multibaggerScore;
                analysis.multibaggerFactors = this.multibaggerFactors;
                return analysis;
            }
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskAssessment {
        private BigDecimal volatility;
        private BigDecimal beta;
        private String riskLevel;
        private List<String> riskFactors;
        private BigDecimal riskScore;
        
        public static RiskAssessmentBuilder builder() {
            return new RiskAssessmentBuilder();
        }
        
        public static class RiskAssessmentBuilder {
            private BigDecimal volatility;
            private BigDecimal beta;
            private String riskLevel;
            private List<String> riskFactors;
            private BigDecimal riskScore;
            
            public RiskAssessmentBuilder volatility(BigDecimal volatility) {
                this.volatility = volatility;
                return this;
            }
            
            public RiskAssessmentBuilder beta(BigDecimal beta) {
                this.beta = beta;
                return this;
            }
            
            public RiskAssessmentBuilder riskLevel(String riskLevel) {
                this.riskLevel = riskLevel;
                return this;
            }
            
            public RiskAssessmentBuilder riskFactors(List<String> riskFactors) {
                this.riskFactors = riskFactors;
                return this;
            }
            
            public RiskAssessmentBuilder riskScore(BigDecimal riskScore) {
                this.riskScore = riskScore;
                return this;
            }
            
            public RiskAssessment build() {
                RiskAssessment assessment = new RiskAssessment();
                assessment.volatility = this.volatility;
                assessment.beta = this.beta;
                assessment.riskLevel = this.riskLevel;
                assessment.riskFactors = this.riskFactors;
                assessment.riskScore = this.riskScore;
                return assessment;
            }
        }
    }
}
