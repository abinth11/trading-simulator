package com.tradingsim.matching.engine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable view of a symbol's book. Built on the engine thread so HTTP and
 * WebSocket readers never touch the live PriorityQueues.
 */
public record OrderBookSnapshot(
        String symbol,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        int buyDepth,
        int sellDepth,
        Instant generatedAt
) {
    static OrderBookSnapshot from(OrderBook book) {
        return new OrderBookSnapshot(
                book.getSymbol(),
                book.getBestBidPrice().orElse(BigDecimal.ZERO),
                book.getBestAskPrice().orElse(BigDecimal.ZERO),
                book.getBuyDepth(),
                book.getSellDepth(),
                Instant.now()
        );
    }
}
