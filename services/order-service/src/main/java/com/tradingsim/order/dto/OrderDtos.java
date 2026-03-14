package com.tradingsim.order.dto;

import com.tradingsim.order.entity.Order;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderDtos {

    @Getter @Setter @NoArgsConstructor
    public static class PlaceOrderRequest {

        @NotBlank(message = "Symbol is required")
        @Size(max = 20, message = "Symbol too long")
        private String symbol;

        @NotNull(message = "Side is required (BUY or SELL)")
        private Order.Side side;

        @NotNull(message = "Order type is required")
        private Order.OrderType orderType = Order.OrderType.LIMIT;

        // Required for LIMIT orders, null for MARKET
        @DecimalMin(value = "0.01", message = "Price must be positive")
        private BigDecimal price;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.000001", message = "Quantity must be positive")
        private BigDecimal quantity;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrderResponse {
        private UUID id;
        private UUID userId;
        private String symbol;
        private Order.Side side;
        private Order.OrderType orderType;
        private BigDecimal price;
        private BigDecimal quantity;
        private BigDecimal filledQuantity;
        private BigDecimal remainingQuantity;
        private Order.OrderStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public static OrderResponse from(Order o) {
            return OrderResponse.builder()
                    .id(o.getId())
                    .userId(o.getUserId())
                    .symbol(o.getSymbol())
                    .side(o.getSide())
                    .orderType(o.getOrderType())
                    .price(o.getPrice())
                    .quantity(o.getQuantity())
                    .filledQuantity(o.getFilledQuantity())
                    .remainingQuantity(o.getRemainingQuantity())
                    .status(o.getStatus())
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .build();
        }
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private Instant timestamp;
    }
}
