package com.tradingsim.matching;

import com.tradingsim.matching.consumer.OrderEventConsumer;
import com.tradingsim.matching.consumer.OrderPlacedEvent;
import com.tradingsim.matching.engine.MatchingEngineRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock MatchingEngineRouter router;
    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks OrderEventConsumer consumer;

    private final UUID orderId = UUID.randomUUID();

    private OrderPlacedEvent event() {
        return new OrderPlacedEvent(orderId, UUID.randomUUID(), "TCS", "BUY", "LIMIT",
                new BigDecimal("100"), new BigDecimal("5"), Instant.now());
    }

    private void givenOrderStatus(String... status) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(orderId))).thenReturn(List.of(status));
    }

    @Test
    void pendingOrder_isSubmittedToEngine() {
        givenOrderStatus("PENDING");

        consumer.onOrderPlaced(event());

        verify(router).submitOrder(argThat(o -> o.getId().equals(orderId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL", "FILLED", "CANCELLING", "CANCELLED"})
    void orderAlreadyHandledOrBeingCancelled_isSkipped(String status) {
        givenOrderStatus(status);

        consumer.onOrderPlaced(event());

        verify(router, never()).submitOrder(any());
    }

    @Test
    void unknownOrder_isSkipped() {
        givenOrderStatus(); // no row

        consumer.onOrderPlaced(event());

        verify(router, never()).submitOrder(any());
    }
}
