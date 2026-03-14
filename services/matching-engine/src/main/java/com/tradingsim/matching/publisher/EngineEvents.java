package com.tradingsim.matching.publisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class EngineEvents {

    public record TradeExecutedEvent(
            UUID tradeId,
            UUID buyOrderId,
            UUID sellOrderId,
            UUID buyerId,
            UUID sellerId,
            String symbol,
            BigDecimal price,
            BigDecimal quantity,
            Instant executedAt
    ) {}

    public record PriceUpdatedEvent(
            String symbol,
            BigDecimal lastPrice,
            BigDecimal volume,
            Instant updatedAt
    ) {}
}
