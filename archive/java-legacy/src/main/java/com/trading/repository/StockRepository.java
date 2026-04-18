package com.trading.repository;

import com.trading.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    
    Optional<Stock> findBySymbol(String symbol);
    
    List<Stock> findBySector(String sector);
    
    List<Stock> findByIndustry(String industry);
    
    @Query("SELECT s FROM Stock s WHERE s.marketCap BETWEEN :minCap AND :maxCap")
    List<Stock> findByMarketCapRange(@Param("minCap") java.math.BigDecimal minCap, 
                                    @Param("maxCap") java.math.BigDecimal maxCap);
    
    @Query("SELECT s FROM Stock s WHERE s.peRatio BETWEEN :minPE AND :maxPE")
    List<Stock> findByPERatioRange(@Param("minPE") java.math.BigDecimal minPE, 
                                  @Param("maxPE") java.math.BigDecimal maxPE);
    
    @Query("SELECT s FROM Stock s WHERE s.revenueGrowth > :minGrowth AND s.profitGrowth > :minGrowth")
    List<Stock> findHighGrowthStocks(@Param("minGrowth") java.math.BigDecimal minGrowth);
    
    @Query("SELECT s FROM Stock s WHERE s.roe > :minROE AND s.roa > :minROA")
    List<Stock> findHighProfitabilityStocks(@Param("minROE") java.math.BigDecimal minROE, 
                                           @Param("minROA") java.math.BigDecimal minROA);
    
    @Query("SELECT s FROM Stock s WHERE s.debtToEquity < :maxDebt")
    List<Stock> findLowDebtStocks(@Param("maxDebt") java.math.BigDecimal maxDebt);
    
    @Query("SELECT s FROM Stock s WHERE s.symbol LIKE %:symbol%")
    List<Stock> findBySymbolContaining(@Param("symbol") String symbol);
}
