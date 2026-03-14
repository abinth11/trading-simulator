package com.tradingsim.order.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.order-placed}")
    private String orderPlacedTopic;

    @Value("${kafka.topics.order-cancelled}")
    private String orderCancelledTopic;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        kafkaTemplate.send(orderPlacedTopic, event.orderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish OrderPlaced: {}", ex.getMessage());
                    else log.debug("Published OrderPlaced: {}", event.orderId());
                });
    }

    public void publishOrderCancelled(UUID orderId, UUID userId, String symbol) {
        var event = new OrderCancelledEvent(orderId, userId, symbol, Instant.now());
        kafkaTemplate.send(orderCancelledTopic, orderId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish OrderCancelled: {}", ex.getMessage());
                    else log.debug("Published OrderCancelled: {}", orderId);
                });
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
