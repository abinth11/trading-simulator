package com.tradingsim.matching.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    private UUID id;
    private UUID userId;
    private String symbol;
    private Side side;
    private OrderType type;

    private BigDecimal price;          // null for MARKET orders
    private BigDecimal quantity;
    private BigDecimal filledQuantity;

    private OrderStatus status;
    private Instant createdAt;

    // ── Derived helpers ───────────────────────────────────────────
    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    public boolean isFilled() {
        return filledQuantity.compareTo(quantity) >= 0;
    }

    public boolean isPartiallyFilled() {
        return filledQuantity.compareTo(BigDecimal.ZERO) > 0 && !isFilled();
    }

    public enum Side {
        BUY, SELL
    }

    public enum OrderType {
        LIMIT, MARKET
    }

    public enum OrderStatus {
        PENDING, PARTIAL, FILLED, CANCELLED, REJECTED
    }
}
