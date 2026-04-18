package com.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String symbol;
    
    private String name;
    private String exchange;
    private String sector;
    private String industry;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal marketCap;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal volume;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal peRatio;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal pbRatio;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal debtToEquity;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal roe;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal roa;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal revenueGrowth;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal profitGrowth;
    
    private LocalDateTime lastUpdated;
    
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PriceData> priceHistory;
    
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TradingSignal> signals;
}
