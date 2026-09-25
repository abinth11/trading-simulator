package com.tradingsim.matching.consumer;

import com.tradingsim.matching.engine.MatchingEngineRouter;
import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.OrderStatus;
import com.tradingsim.matching.model.Order.OrderType;
import com.tradingsim.matching.model.Order.Side;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final MatchingEngineRouter router;

    @KafkaListener(
            topics = "${kafka.topics.order-placed}",
            groupId = "matching-engine-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlaced: {} {} {} @ {}",
                event.getOrderId(), event.getSide(), event.getSymbol(), event.getPrice());

        Order order = Order.builder()
                .id(event.getOrderId())
                .userId(event.getUserId())
                .symbol(event.getSymbol())
                .side(Side.valueOf(event.getSide()))
                .type(OrderType.valueOf(event.getOrderType()))
                .price(event.getPrice())
                .quantity(event.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .createdAt(event.getPlacedAt() != null ? event.getPlacedAt() : Instant.now())
                .build();

        router.submitOrder(order);
    }

    // Messages carry no type headers, and the consumer default type is OrderPlacedEvent,
    // so this listener must name its own payload type or every cancel fails to convert.
    @KafkaListener(
            topics = "${kafka.topics.order-cancelled}",
            groupId = "matching-engine-group",
            properties = "spring.json.value.default.type=com.tradingsim.matching.consumer.OrderCancelledEvent"
    )
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelled: {} symbol={}", event.getOrderId(), event.getSymbol());
        router.cancelOrder(event.getSymbol(), event.getOrderId());
    }
}
