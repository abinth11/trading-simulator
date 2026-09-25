package com.tradingsim.matching.publisher;

import com.tradingsim.matching.engine.EngineEventHandler;
import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.TradeEvent;
import com.tradingsim.matching.publisher.EngineEvents.PriceUpdatedEvent;
import com.tradingsim.matching.publisher.EngineEvents.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventPublisher implements EngineEventHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${kafka.topics.trade-executed}")
    private String tradeExecutedTopic;

    @Value("${kafka.topics.price-updated}")
    private String priceUpdatedTopic;

    /**
     * Called by MatchingEngineRouter after each batch of trades.
     * 1. Persist each trade to the trades table
     * 2. Update order statuses in DB
     * 3. Publish TradeExecuted to Kafka (Portfolio + Logger consume this)
     * 4. Publish PriceUpdated to Kafka + Redis (Market Data + WS consume this)
     */
    @Override
    @Transactional
    public void onTrades(List<TradeEvent> trades) {
        for (TradeEvent trade : trades) {
            // 1. Persist trade record
            persistTrade(trade);

            // 2. Update filled quantities on both orders
            updateOrderFill(trade.getBuyOrderId(), trade.getQuantity());
            updateOrderFill(trade.getSellOrderId(), trade.getQuantity());

            // 3. Publish TradeExecuted event
            var tradeEvent = new TradeExecutedEvent(
                    trade.getTradeId(),
                    trade.getBuyOrderId(),
                    trade.getSellOrderId(),
                    trade.getBuyerId(),
                    trade.getSellerId(),
                    trade.getSymbol(),
                    trade.getPrice(),
                    trade.getQuantity(),
                    trade.getExecutedAt()
            );

            kafkaTemplate.send(tradeExecutedTopic, trade.getSymbol(), tradeEvent)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.error("Failed to publish TradeExecuted: {}", ex.getMessage());
                        else log.debug("Published TradeExecuted: {}", trade.getTradeId());
                    });

            // 4. Update last price in Redis (fast read for WS and order validation)
            String priceKey = "price:last:" + trade.getSymbol();
            redisTemplate.opsForValue().set(priceKey, trade.getPrice().toPlainString());

            // 5. Publish PriceUpdated event
            var priceEvent = new PriceUpdatedEvent(
                    trade.getSymbol(),
                    trade.getPrice(),
                    trade.getQuantity(),
                    Instant.now()
            );

            kafkaTemplate.send(priceUpdatedTopic, trade.getSymbol(), priceEvent)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.error("Failed to publish PriceUpdated: {}", ex.getMessage());
                    });

            log.info("Trade published: {} {} @ {} qty={}",
                    trade.getTradeId(), trade.getSymbol(), trade.getPrice(), trade.getQuantity());
        }
    }

    /**
     * Persists an engine-side cancellation (MARKET remainder or self-trade prevention).
     * filled_quantity is left as-is, so a partly filled order shows how much executed.
     */
    @Override
    public void onOrderCancelled(Order order) {
        jdbcTemplate.update("""
                UPDATE orders
                SET status = 'CANCELLED',
                    updated_at = NOW()
                WHERE id = ? AND status IN ('PENDING', 'PARTIAL')
                """, order.getId());

        log.info("{} order {} cancelled by engine: filled {} of {}",
                order.getType(), order.getId(), order.getFilledQuantity(), order.getQuantity());
    }

    private void persistTrade(TradeEvent trade) {
        jdbcTemplate.update("""
                INSERT INTO trades (id, buy_order_id, sell_order_id, buyer_id, seller_id,
                                    symbol, price, quantity, executed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                trade.getTradeId(),
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                trade.getBuyerId(),
                trade.getSellerId(),
                trade.getSymbol(),
                trade.getPrice(),
                trade.getQuantity(),
                Timestamp.from(trade.getExecutedAt())
        );
    }

    private void updateOrderFill(java.util.UUID orderId, java.math.BigDecimal qty) {
        // A fill that was already queued when the user cancelled still records its quantity,
        // but must not revive the order — an open status would reserve funds again.
        jdbcTemplate.update("""
                UPDATE orders
                SET filled_quantity = filled_quantity + ?,
                    status = CASE
                        WHEN status = 'CANCELLED' THEN 'CANCELLED'
                        WHEN filled_quantity + ? >= quantity THEN 'FILLED'
                        ELSE 'PARTIAL'
                    END,
                    updated_at = NOW()
                WHERE id = ?
                """, qty, qty, orderId);
    }
}
