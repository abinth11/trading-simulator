package com.tradingsim.matching.service;

import com.tradingsim.matching.controller.EngineController.OrderBookStreamSnapshot;
import com.tradingsim.matching.engine.OrderBook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EngineStreamService {

    public static OrderBookStreamSnapshot buildSnapshot(String symbol, OrderBook orderBook) {
        return new OrderBookStreamSnapshot(
                symbol,
                orderBook.getBestBidPrice().orElse(BigDecimal.ZERO),
                orderBook.getBestAskPrice().orElse(BigDecimal.ZERO),
                orderBook.getBuyDepth(),
                orderBook.getSellDepth(),
                Instant.now()
        );
    }
}
