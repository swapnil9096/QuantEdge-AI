package com.trading.util;

import com.trading.model.PriceData;
import com.trading.model.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Slf4j
public class SampleDataGenerator {

    private final Random random = new Random();

    public Stock generateSampleStock(String symbol) {
        return Stock.builder()
                .symbol(symbol)
                .name(getCompanyName(symbol))
                .exchange("NSE")
                .sector(getSector(symbol))
                .industry(getIndustry(symbol))
                .currentPrice(generatePrice(symbol))
                .marketCap(generateMarketCap(symbol))
                .volume(generateVolume())
                .peRatio(generatePERatio())
                .pbRatio(generatePBRatio())
                .debtToEquity(generateDebtToEquity())
                .roe(generateROE())
                .roa(generateROA())
                .revenueGrowth(generateRevenueGrowth())
                .profitGrowth(generateProfitGrowth())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public List<PriceData> generatePriceHistory(String symbol, int days) {
        List<PriceData> priceHistory = new ArrayList<>();
        BigDecimal basePrice = generatePrice(symbol);
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        BigDecimal currentPrice = basePrice;
        
        for (int i = 0; i < days; i++) {
            LocalDateTime timestamp = startDate.plusDays(i);
            
            // Generate OHLC data
            BigDecimal open = currentPrice;
            BigDecimal close = generateNextPrice(currentPrice);
            BigDecimal high = close.multiply(new BigDecimal(1.02 + random.nextDouble() * 0.02));
            BigDecimal low = close.multiply(new BigDecimal(0.98 - random.nextDouble() * 0.02));
            BigDecimal volume = generateVolume();
            
            PriceData priceData = PriceData.builder()
                    .timestamp(timestamp)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();
            
            priceHistory.add(priceData);
            currentPrice = close;
        }
        
        return priceHistory;
    }

    private String getCompanyName(String symbol) {
        switch (symbol.toUpperCase()) {
            case "RELIANCE":
                return "Reliance Industries Ltd";
            case "TCS":
                return "Tata Consultancy Services Ltd";
            case "INFY":
                return "Infosys Ltd";
            case "HDFC":
                return "HDFC Bank Ltd";
            case "ICICIBANK":
                return "ICICI Bank Ltd";
            case "WIPRO":
                return "Wipro Ltd";
            case "BHARTIARTL":
                return "Bharti Airtel Ltd";
            case "ITC":
                return "ITC Ltd";
            case "SBIN":
                return "State Bank of India";
            case "KOTAKBANK":
                return "Kotak Mahindra Bank Ltd";
            default:
                return symbol + " Ltd";
        }
    }

    private String getSector(String symbol) {
        switch (symbol.toUpperCase()) {
            case "RELIANCE":
                return "Oil & Gas";
            case "TCS":
            case "INFY":
            case "WIPRO":
                return "Information Technology";
            case "HDFC":
            case "ICICIBANK":
            case "SBIN":
            case "KOTAKBANK":
                return "Banking";
            case "BHARTIARTL":
                return "Telecommunications";
            case "ITC":
                return "FMCG";
            default:
                return "Diversified";
        }
    }

    private String getIndustry(String symbol) {
        switch (symbol.toUpperCase()) {
            case "RELIANCE":
                return "Refineries";
            case "TCS":
            case "INFY":
            case "WIPRO":
                return "IT Services";
            case "HDFC":
            case "ICICIBANK":
            case "SBIN":
            case "KOTAKBANK":
                return "Private Banking";
            case "BHARTIARTL":
                return "Telecom Services";
            case "ITC":
                return "Tobacco";
            default:
                return "General";
        }
    }

    private BigDecimal generatePrice(String symbol) {
        switch (symbol.toUpperCase()) {
            case "RELIANCE":
                return new BigDecimal(2500 + random.nextInt(500));
            case "TCS":
                return new BigDecimal(3500 + random.nextInt(500));
            case "INFY":
                return new BigDecimal(1500 + random.nextInt(300));
            case "HDFC":
                return new BigDecimal(1600 + random.nextInt(200));
            case "ICICIBANK":
                return new BigDecimal(1000 + random.nextInt(200));
            case "WIPRO":
                return new BigDecimal(400 + random.nextInt(100));
            case "BHARTIARTL":
                return new BigDecimal(800 + random.nextInt(200));
            case "ITC":
                return new BigDecimal(450 + random.nextInt(50));
            case "SBIN":
                return new BigDecimal(600 + random.nextInt(100));
            case "KOTAKBANK":
                return new BigDecimal(1800 + random.nextInt(200));
            default:
                return new BigDecimal(100 + random.nextInt(1000));
        }
    }

    private BigDecimal generateMarketCap(String symbol) {
        switch (symbol.toUpperCase()) {
            case "RELIANCE":
                return new BigDecimal(1690000 + random.nextInt(100000));
            case "TCS":
                return new BigDecimal(1200000 + random.nextInt(100000));
            case "INFY":
                return new BigDecimal(600000 + random.nextInt(50000));
            case "HDFC":
                return new BigDecimal(800000 + random.nextInt(50000));
            case "ICICIBANK":
                return new BigDecimal(700000 + random.nextInt(50000));
            case "WIPRO":
                return new BigDecimal(200000 + random.nextInt(20000));
            case "BHARTIARTL":
                return new BigDecimal(450000 + random.nextInt(30000));
            case "ITC":
                return new BigDecimal(550000 + random.nextInt(30000));
            case "SBIN":
                return new BigDecimal(300000 + random.nextInt(20000));
            case "KOTAKBANK":
                return new BigDecimal(350000 + random.nextInt(20000));
            default:
                return new BigDecimal(10000 + random.nextInt(100000));
        }
    }

    private BigDecimal generateVolume() {
        return new BigDecimal(100000 + random.nextInt(2000000));
    }

    private BigDecimal generatePERatio() {
        // Generate P/E ratio between 10 and 40
        double pe = 10 + random.nextDouble() * 30;
        return new BigDecimal(pe).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generatePBRatio() {
        // Generate P/B ratio between 0.5 and 8
        double pb = 0.5 + random.nextDouble() * 7.5;
        return new BigDecimal(pb).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateDebtToEquity() {
        // Generate debt-to-equity between 0 and 2
        double debt = random.nextDouble() * 2;
        return new BigDecimal(debt).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateROE() {
        // Generate ROE between 5 and 30
        double roe = 5 + random.nextDouble() * 25;
        return new BigDecimal(roe).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateROA() {
        // Generate ROA between 2 and 20
        double roa = 2 + random.nextDouble() * 18;
        return new BigDecimal(roa).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateRevenueGrowth() {
        // Generate revenue growth between -10 and 50
        double growth = -10 + random.nextDouble() * 60;
        return new BigDecimal(growth).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateProfitGrowth() {
        // Generate profit growth between -20 and 60
        double growth = -20 + random.nextDouble() * 80;
        return new BigDecimal(growth).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateNextPrice(BigDecimal currentPrice) {
        // Generate next price with some volatility
        double volatility = 0.02; // 2% daily volatility
        double change = (random.nextGaussian() * volatility);
        BigDecimal multiplier = new BigDecimal(1 + change);
        return currentPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public List<Stock> generateSampleStocks() {
        List<Stock> stocks = new ArrayList<>();
        String[] symbols = {"RELIANCE", "TCS", "INFY", "HDFC", "ICICIBANK", "WIPRO", "BHARTIARTL", "ITC", "SBIN", "KOTAKBANK"};
        
        for (String symbol : symbols) {
            stocks.add(generateSampleStock(symbol));
        }
        
        return stocks;
    }

    public List<PriceData> generateSamplePriceHistory(String symbol) {
        return generatePriceHistory(symbol, 100); // Generate 100 days of data
    }
    
    public List<PriceData> generateSamplePriceHistory(String symbol, int days) {
        return generatePriceHistory(symbol, days);
    }
}
