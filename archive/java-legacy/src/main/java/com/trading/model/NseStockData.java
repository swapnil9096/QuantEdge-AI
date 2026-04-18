package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class NseStockData {
    
    // Basic Price Information
    private String symbol;
    private String companyName;
    private BigDecimal lastPrice;
    private BigDecimal open;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal previousClose;
    private BigDecimal totalTradedVolume;
    private BigDecimal totalTradedValue;
    private BigDecimal marketCap;
    
    // Price Change Information
    private BigDecimal change;
    private BigDecimal changePercent;
    
    // Trade Information
    private BigDecimal high52Week;
    private BigDecimal low52Week;
    private BigDecimal close;
    private BigDecimal lastUpdateTime;
    private BigDecimal yearHigh;
    private BigDecimal yearLow;
    
    // Market Depth Information
    private List<BidAsk> bid;
    private List<BidAsk> ask;
    
    // Company Information
    private String industry;
    private String sector;
    private String isin;
    private String series;
    private String faceValue;
    
    // Additional Metrics
    private BigDecimal pe;
    private BigDecimal pb;
    private BigDecimal dividendYield;
    private BigDecimal bookValue;
    private BigDecimal eps;
    
    // Timestamp
    private LocalDateTime timestamp;
    private String dataSource;
    
    // Getters
    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getDayHigh() { return dayHigh; }
    public BigDecimal getDayLow() { return dayLow; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public BigDecimal getTotalTradedVolume() { return totalTradedVolume; }
    public BigDecimal getTotalTradedValue() { return totalTradedValue; }
    public BigDecimal getMarketCap() { return marketCap; }
    public BigDecimal getChange() { return change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public BigDecimal getHigh52Week() { return high52Week; }
    public BigDecimal getLow52Week() { return low52Week; }
    public BigDecimal getClose() { return close; }
    public BigDecimal getYearHigh() { return yearHigh; }
    public BigDecimal getYearLow() { return yearLow; }
    public List<BidAsk> getBid() { return bid; }
    public List<BidAsk> getAsk() { return ask; }
    public String getIndustry() { return industry; }
    public String getSector() { return sector; }
    public String getIsin() { return isin; }
    public String getSeries() { return series; }
    public String getFaceValue() { return faceValue; }
    public BigDecimal getPe() { return pe; }
    public BigDecimal getPb() { return pb; }
    public BigDecimal getDividendYield() { return dividendYield; }
    public BigDecimal getBookValue() { return bookValue; }
    public BigDecimal getEps() { return eps; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getDataSource() { return dataSource; }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private NseStockData data = new NseStockData();
        
        public Builder symbol(String symbol) { data.symbol = symbol; return this; }
        public Builder companyName(String companyName) { data.companyName = companyName; return this; }
        public Builder lastPrice(BigDecimal lastPrice) { data.lastPrice = lastPrice; return this; }
        public Builder open(BigDecimal open) { data.open = open; return this; }
        public Builder dayHigh(BigDecimal dayHigh) { data.dayHigh = dayHigh; return this; }
        public Builder dayLow(BigDecimal dayLow) { data.dayLow = dayLow; return this; }
        public Builder previousClose(BigDecimal previousClose) { data.previousClose = previousClose; return this; }
        public Builder totalTradedVolume(BigDecimal totalTradedVolume) { data.totalTradedVolume = totalTradedVolume; return this; }
        public Builder totalTradedValue(BigDecimal totalTradedValue) { data.totalTradedValue = totalTradedValue; return this; }
        public Builder marketCap(BigDecimal marketCap) { data.marketCap = marketCap; return this; }
        public Builder change(BigDecimal change) { data.change = change; return this; }
        public Builder changePercent(BigDecimal changePercent) { data.changePercent = changePercent; return this; }
        public Builder high52Week(BigDecimal high52Week) { data.high52Week = high52Week; return this; }
        public Builder low52Week(BigDecimal low52Week) { data.low52Week = low52Week; return this; }
        public Builder close(BigDecimal close) { data.close = close; return this; }
        public Builder yearHigh(BigDecimal yearHigh) { data.yearHigh = yearHigh; return this; }
        public Builder yearLow(BigDecimal yearLow) { data.yearLow = yearLow; return this; }
        public Builder bid(List<BidAsk> bid) { data.bid = bid; return this; }
        public Builder ask(List<BidAsk> ask) { data.ask = ask; return this; }
        public Builder industry(String industry) { data.industry = industry; return this; }
        public Builder sector(String sector) { data.sector = sector; return this; }
        public Builder isin(String isin) { data.isin = isin; return this; }
        public Builder series(String series) { data.series = series; return this; }
        public Builder faceValue(String faceValue) { data.faceValue = faceValue; return this; }
        public Builder pe(BigDecimal pe) { data.pe = pe; return this; }
        public Builder pb(BigDecimal pb) { data.pb = pb; return this; }
        public Builder dividendYield(BigDecimal dividendYield) { data.dividendYield = dividendYield; return this; }
        public Builder bookValue(BigDecimal bookValue) { data.bookValue = bookValue; return this; }
        public Builder eps(BigDecimal eps) { data.eps = eps; return this; }
        public Builder timestamp(LocalDateTime timestamp) { data.timestamp = timestamp; return this; }
        public Builder dataSource(String dataSource) { data.dataSource = dataSource; return this; }
        
        public NseStockData build() { return data; }
    }
    
    public static class BidAsk {
        private BigDecimal price;
        private BigDecimal quantity;
        private Integer orders;
        
        // Getters for BidAsk
        public BigDecimal getPrice() { return price; }
        public BigDecimal getQuantity() { return quantity; }
        public Integer getOrders() { return orders; }
        
        // Builder for BidAsk
        public static BidAskBuilder builder() {
            return new BidAskBuilder();
        }
        
        public static class BidAskBuilder {
            private BidAsk data = new BidAsk();
            
            public BidAskBuilder price(BigDecimal price) { data.price = price; return this; }
            public BidAskBuilder quantity(BigDecimal quantity) { data.quantity = quantity; return this; }
            public BidAskBuilder orders(Integer orders) { data.orders = orders; return this; }
            
            public BidAsk build() { return data; }
        }
    }
    
    // Trade Info nested class
    public static class TradeInfo {
        private BigDecimal totalTradedVolume;
        private BigDecimal totalTradedValue;
        private BigDecimal totalMarketCap;
        private BigDecimal ffmc;
        private BigDecimal impactCost;
    }
    
    // Market Depth nested class
    public static class MarketDepth {
        private List<BidAsk> buy;
        private List<BidAsk> sell;
    }
    
    // Company Info nested class
    public static class CompanyInfo {
        private String companyName;
        private String industry;
        private String sector;
        private String isin;
        private String series;
        private String faceValue;
    }
    
    // Price Band nested class
    public static class PriceBand {
        private BigDecimal lowerBand;
        private BigDecimal upperBand;
    }
}
