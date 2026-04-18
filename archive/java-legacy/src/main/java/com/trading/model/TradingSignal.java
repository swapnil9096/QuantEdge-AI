package com.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trading_signals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingSignal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;
    
    @Enumerated(EnumType.STRING)
    private SignalType signalType;
    
    @Enumerated(EnumType.STRING)
    private SignalStrength strength;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal entryPrice;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal targetPrice;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal stopLoss;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal expectedReturn;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;
    
    private LocalDateTime signalTime;
    private LocalDateTime expiryTime;
    
    @Column(columnDefinition = "TEXT")
    private String reasoning;
    
    @Column(columnDefinition = "TEXT")
    private String technicalAnalysis;
    
    @Column(columnDefinition = "TEXT")
    private String fundamentalAnalysis;
    
    private boolean isActive;
    private boolean isExecuted;
    
    public enum SignalType {
        BUY, SELL, HOLD
    }
    
    public enum SignalStrength {
        WEAK, MODERATE, STRONG, VERY_STRONG
    }
}
