package com.tradingsim.matching.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeEvent {

    private UUID tradeId;
    private UUID buyOrderId;
    private UUID sellOrderId;
    private UUID buyerId;
    private UUID sellerId;
    private String symbol;
    private BigDecimal price;       // execution price (sell order price — price-time priority)
    private BigDecimal quantity;    // how much was filled in this match
    private Instant executedAt;

    @Override
    public String toString() {
        return String.format("TradeEvent[%s] %s | price=%s qty=%s buyer=%s seller=%s",
                tradeId, symbol, price, quantity, buyerId, sellerId);
    }
}
