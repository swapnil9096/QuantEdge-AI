package com.trading.repository;

import com.trading.model.Stock;
import com.trading.model.TradingSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradingSignalRepository extends JpaRepository<TradingSignal, Long> {
    
    List<TradingSignal> findByStockOrderBySignalTimeDesc(Stock stock);
    
    List<TradingSignal> findByStockAndIsActiveTrueOrderBySignalTimeDesc(Stock stock);
    
    List<TradingSignal> findBySignalTypeAndIsActiveTrueOrderBySignalTimeDesc(TradingSignal.SignalType signalType);
    
    List<TradingSignal> findBySignalTypeAndStrengthAndIsActiveTrueOrderBySignalTimeDesc(
            TradingSignal.SignalType signalType, TradingSignal.SignalStrength strength);
    
    @Query("SELECT t FROM TradingSignal t WHERE t.stock = :stock AND t.signalTime >= :fromDate ORDER BY t.signalTime DESC")
    List<TradingSignal> findByStockAndSignalTimeAfter(@Param("stock") Stock stock, @Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT t FROM TradingSignal t WHERE t.isActive = true AND t.expiryTime > :currentTime ORDER BY t.confidence DESC")
    List<TradingSignal> findActiveSignals(@Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT t FROM TradingSignal t WHERE t.stock.symbol = :symbol AND t.isActive = true ORDER BY t.signalTime DESC")
    List<TradingSignal> findActiveByStockSymbol(@Param("symbol") String symbol);
    
    @Query("SELECT t FROM TradingSignal t WHERE t.confidence >= :minConfidence AND t.isActive = true ORDER BY t.confidence DESC")
    List<TradingSignal> findHighConfidenceSignals(@Param("minConfidence") java.math.BigDecimal minConfidence);
    
    @Query("SELECT t FROM TradingSignal t WHERE t.expectedReturn >= :minReturn AND t.isActive = true ORDER BY t.expectedReturn DESC")
    List<TradingSignal> findHighReturnSignals(@Param("minReturn") java.math.BigDecimal minReturn);
    
    @Query("SELECT COUNT(t) FROM TradingSignal t WHERE t.stock = :stock AND t.signalType = :signalType AND t.isExecuted = true")
    Long countExecutedSignalsByStockAndType(@Param("stock") Stock stock, @Param("signalType") TradingSignal.SignalType signalType);
    
    @Query("SELECT AVG(t.confidence) FROM TradingSignal t WHERE t.stock = :stock AND t.isExecuted = true")
    Double getAverageConfidenceByStock(@Param("stock") Stock stock);
}
