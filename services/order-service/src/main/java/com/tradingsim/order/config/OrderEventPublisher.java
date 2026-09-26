package com.tradingsim.order.config;

import com.tradingsim.order.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order events for the matching engine. Both methods must run inside the transaction that
 * saves the order change: events go through the outbox and reach Kafka only after it commits.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OutboxWriter outboxWriter;

    @Value("${kafka.topics.order-placed}")
    private String orderPlacedTopic;

    @Value("${kafka.topics.order-cancelled}")
    private String orderCancelledTopic;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        outboxWriter.enqueue(orderPlacedTopic, event.orderId().toString(), event);
    }

    public void publishOrderCancelled(UUID orderId, UUID userId, String symbol) {
        var event = new OrderCancelledEvent(orderId, userId, symbol, Instant.now());
        outboxWriter.enqueue(orderCancelledTopic, orderId.toString(), event);
    }

    public record OrderPlacedEvent(
            UUID orderId,
            UUID userId,
            String symbol,
            String side,
            String orderType,
            BigDecimal price,
            BigDecimal quantity,
            Instant placedAt
    ) {}

    public record OrderCancelledEvent(
            UUID orderId,
            UUID userId,
            String symbol,
            Instant cancelledAt
    ) {}
}
