package com.tradingsim.matching;

import com.tradingsim.matching.engine.EngineEventHandler;
import com.tradingsim.matching.engine.SymbolEngine;
import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.OrderStatus;
import com.tradingsim.matching.model.Order.OrderType;
import com.tradingsim.matching.model.Order.Side;
import com.tradingsim.matching.model.TradeEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolEngineTest {

    /** Records everything the engine reports. */
    static class RecordingHandler implements EngineEventHandler {
        final List<TradeEvent> trades = new CopyOnWriteArrayList<>();
        final List<UUID> engineCancelled = new CopyOnWriteArrayList<>();
        final List<UUID> cancelRequestsProcessed = new CopyOnWriteArrayList<>();

        @Override public void onTrades(List<TradeEvent> batch) { trades.addAll(batch); }
        @Override public void onOrderCancelled(Order order) { engineCancelled.add(order.getId()); }
        @Override public void onCancelRequestProcessed(UUID orderId) { cancelRequestsProcessed.add(orderId); }
    }

    private final RecordingHandler handler = new RecordingHandler();
    private final SymbolEngine engine = new SymbolEngine("TCS", handler);

    private static Order order(Side side, double price, double qty) {
        return Order.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .symbol("TCS")
                .side(side)
                .type(OrderType.LIMIT)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    // A copy with the same id, as a redelivered Kafka message would produce
    private static Order copyOf(Order o) {
        Order copy = order(o.getSide(), o.getPrice().doubleValue(), o.getQuantity().doubleValue());
        copy.setId(o.getId());
        copy.setUserId(o.getUserId());
        return copy;
    }

    /** Runs every queued command on the calling thread, then stops. */
    private void drain() {
        engine.shutdown();
        engine.run();
    }

    @Test
    void duplicateOrder_isAddedOnce() {
        Order buy = order(Side.BUY, 100, 5);
        engine.submitOrder(buy);
        engine.submitOrder(copyOf(buy));
        drain();

        assertThat(engine.getOrderBook().getBuyDepth()).isEqualTo(1);
    }

    @Test
    void duplicateOfFilledOrder_doesNotTradeAgain() {
        Order sell = order(Side.SELL, 100, 5);
        Order buy = order(Side.BUY, 100, 5);
        engine.submitOrder(sell);
        engine.submitOrder(buy);            // fills and leaves the book
        engine.submitOrder(copyOf(buy));    // redelivered while the first was still queued
        engine.submitOrder(order(Side.SELL, 100, 5));
        drain();

        assertThat(handler.trades).hasSize(1);
        assertThat(engine.getOrderBook().getBuyDepth()).isZero();
        assertThat(engine.getOrderBook().getSellDepth()).isEqualTo(1);
    }

    @Test
    void cancel_removesRestingOrderAndIsReported() {
        Order buy = order(Side.BUY, 100, 5);
        engine.submitOrder(buy);
        engine.cancelOrder(buy.getId());
        drain();

        assertThat(engine.getOrderBook().getBuyDepth()).isZero();
        assertThat(handler.cancelRequestsProcessed).containsExactly(buy.getId());
    }

    @Test
    void cancelBeforeOrderArrives_ignoresTheLateOrder() {
        Order buy = order(Side.BUY, 100, 5);
        engine.cancelOrder(buy.getId());   // cancel consumed before the order event
        engine.submitOrder(buy);
        drain();

        assertThat(engine.getOrderBook().getBuyDepth()).isZero();
        assertThat(handler.cancelRequestsProcessed).containsExactly(buy.getId());
    }

    @Test
    void cancelAfterFill_isStillReported() {
        Order buy = order(Side.BUY, 100, 5);
        engine.submitOrder(buy);
        engine.submitOrder(order(Side.SELL, 100, 5)); // fills the buy before the cancel is processed
        engine.cancelOrder(buy.getId());
        drain();

        assertThat(handler.trades).hasSize(1);
        // Reported so the handler can finalise — it leaves a FILLED order as FILLED
        assertThat(handler.cancelRequestsProcessed).containsExactly(buy.getId());
    }
}
