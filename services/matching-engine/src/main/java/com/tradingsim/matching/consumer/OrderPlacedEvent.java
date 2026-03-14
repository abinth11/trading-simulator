package com.tradingsim.matching.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPlacedEvent {
    private UUID orderId;
    private UUID userId;
    private String symbol;
    private String side;        // BUY | SELL
    private String orderType;   // LIMIT | MARKET
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant placedAt;
}
