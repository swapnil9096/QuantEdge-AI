package com.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;
    
    private LocalDateTime timestamp;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal open;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal high;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal low;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal close;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal volume;
    
    // Technical Indicators
    @Column(precision = 10, scale = 4)
    private BigDecimal rsi;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal macd;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal macdSignal;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal macdHistogram;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal sma20;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal sma50;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal ema12;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal ema26;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal bollingerUpper;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal bollingerMiddle;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal bollingerLower;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal stochasticK;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal stochasticD;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal williamsR;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal atr;
}
