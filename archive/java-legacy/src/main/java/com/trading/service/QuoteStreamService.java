package com.trading.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableScheduling
public class QuoteStreamService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RealTimeDataService realTimeDataService;
    private final StockValidationService stockValidationService;
    private final LiveMarketDataService liveMarketDataService;

    private final ConcurrentHashMap<String, RealTimeDataService.RealTimeStockData> latestQuotes = new ConcurrentHashMap<>();

    public QuoteStreamService(SimpMessagingTemplate messagingTemplate,
                              RealTimeDataService realTimeDataService,
                              StockValidationService stockValidationService,
                              LiveMarketDataService liveMarketDataService) {
        this.messagingTemplate = messagingTemplate;
        this.realTimeDataService = realTimeDataService;
        this.stockValidationService = stockValidationService;
        this.liveMarketDataService = liveMarketDataService;
    }

    // Broadcast on-demand only to prevent API spam
    // @Scheduled(fixedRateString = "${stream.interval.ms:30000}")
    public void broadcastQuotes() {
        List<String> symbols = stockValidationService.getAllValidSymbols();
        for (String symbol : symbols) {
            try {
                // Get live market data
                LiveMarketDataService.LiveMarketData liveData = liveMarketDataService.getLiveMarketData(symbol);
                StockValidationService.StockInfo stockInfo = stockValidationService.getStockInfo(symbol);
                
                RealTimeDataService.RealTimeStockData data = RealTimeDataService.RealTimeStockData.builder()
                        .symbol(symbol)
                        .name(stockInfo.getName())
                        .exchange(stockInfo.getExchange())
                        .sector(stockInfo.getSector())
                        .cap(stockInfo.getCap())
                        .currentPrice(liveData.getCurrentPrice())
                        .open(liveData.getOpen())
                        .high(liveData.getHigh())
                        .low(liveData.getLow())
                        .previousClose(liveData.getPreviousClose())
                        .volume(liveData.getVolume())
                        .change(liveData.getChange())
                        .changePercent(liveData.getChangePercent())
                        .lastUpdated(liveData.getTimestamp())
                        .build();
                
                latestQuotes.put(symbol, data);
                messagingTemplate.convertAndSend("/topic/quotes/" + symbol, data);
            } catch (Exception e) {
                // Fallback to existing data or skip
                System.err.println("Error fetching live data for " + symbol + ": " + e.getMessage());
            }
        }
    }

    public RealTimeDataService.RealTimeStockData getLatestQuote(String symbol) {
        return latestQuotes.get(symbol);
    }
}


