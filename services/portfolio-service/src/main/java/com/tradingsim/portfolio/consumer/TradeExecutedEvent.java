package com.tradingsim.portfolio.consumer;

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
public class TradeExecutedEvent {
    private UUID tradeId;
    private UUID buyOrderId;
    private UUID sellOrderId;
    private UUID buyerId;
    private UUID sellerId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant executedAt;
}
