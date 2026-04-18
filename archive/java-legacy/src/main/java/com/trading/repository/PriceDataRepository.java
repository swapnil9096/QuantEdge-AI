package com.trading.repository;

import com.trading.model.PriceData;
import com.trading.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceDataRepository extends JpaRepository<PriceData, Long> {
    
    List<PriceData> findByStockOrderByTimestampAsc(Stock stock);
    
    List<PriceData> findByStockAndTimestampBetweenOrderByTimestampAsc(Stock stock, 
                                                                    LocalDateTime startDate, 
                                                                    LocalDateTime endDate);
    
    @Query("SELECT p FROM PriceData p WHERE p.stock = :stock ORDER BY p.timestamp DESC")
    List<PriceData> findLatestByStock(@Param("stock") Stock stock);
    
    @Query("SELECT p FROM PriceData p WHERE p.stock = :stock ORDER BY p.timestamp DESC LIMIT :limit")
    List<PriceData> findLatestByStockWithLimit(@Param("stock") Stock stock, @Param("limit") int limit);
    
    @Query("SELECT p FROM PriceData p WHERE p.stock.symbol = :symbol ORDER BY p.timestamp DESC")
    List<PriceData> findByStockSymbol(@Param("symbol") String symbol);
    
    @Query("SELECT p FROM PriceData p WHERE p.stock.symbol = :symbol ORDER BY p.timestamp DESC LIMIT :limit")
    List<PriceData> findByStockSymbolWithLimit(@Param("symbol") String symbol, @Param("limit") int limit);
    
    @Query("SELECT p FROM PriceData p WHERE p.stock = :stock AND p.timestamp >= :fromDate ORDER BY p.timestamp ASC")
    List<PriceData> findFromDateByStock(@Param("stock") Stock stock, @Param("fromDate") LocalDateTime fromDate);
}
