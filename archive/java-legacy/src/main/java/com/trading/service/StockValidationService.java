package com.trading.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to validate stock symbols using dynamic NSE symbol fetching
 * Delegates to NseSymbolService for actual symbol validation
 */
@Service
public class StockValidationService {
    
    @Autowired
    private NseSymbolService nseSymbolService;
    
    public boolean isValidStock(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return false;
        }
        return nseSymbolService.isValidSymbol(symbol);
    }
    
    public StockInfo getStockInfo(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        NseSymbolService.StockInfo nseInfo = nseSymbolService.getSymbolInfo(symbol);
        if (nseInfo != null) {
            return new StockInfo(
                nseInfo.getSymbol(),
                nseInfo.getName(),
                nseInfo.getExchange(),
                nseInfo.getSector(),
                nseInfo.getCap()
            );
        }
        return null;
    }
    
    public List<String> getAllValidSymbols() {
        Set<String> symbols = nseSymbolService.getAllValidSymbols();
        return new ArrayList<>(symbols);
    }
    
    public List<StockInfo> getStocksBySector(String sector) {
        Set<String> symbols = nseSymbolService.getAllValidSymbols();
        return symbols.stream()
                .map(symbol -> getStockInfo(symbol))
                .filter(stock -> stock != null && stock.getSector().equalsIgnoreCase(sector))
                .collect(Collectors.toList());
    }
    
    public List<StockInfo> getStocksByCap(String cap) {
        Set<String> symbols = nseSymbolService.getAllValidSymbols();
        return symbols.stream()
                .map(symbol -> getStockInfo(symbol))
                .filter(stock -> stock != null && stock.getCap().equalsIgnoreCase(cap))
                .collect(Collectors.toList());
    }
    
    public static class StockInfo {
        private final String symbol;
        private final String name;
        private final String exchange;
        private final String sector;
        private final String cap;
        
        public StockInfo(String symbol, String name, String exchange, String sector, String cap) {
            this.symbol = symbol;
            this.name = name;
            this.exchange = exchange;
            this.sector = sector;
            this.cap = cap;
        }
        
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public String getExchange() { return exchange; }
        public String getSector() { return sector; }
        public String getCap() { return cap; }
    }
}
