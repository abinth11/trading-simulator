package com.tradingsim.matching.startup;

import com.tradingsim.matching.engine.MatchingEngineRouter;
import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.OrderStatus;
import com.tradingsim.matching.model.Order.OrderType;
import com.tradingsim.matching.model.Order.Side;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * On startup, reads all PENDING and PARTIAL orders from PostgreSQL
 * and re-submits them to the matching engine.
 *
 * This ensures the order book is fully restored after a crash or restart.
 * Orders are submitted in created_at order to preserve price-time priority.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderBookRebuilder implements ApplicationRunner {

    private final MatchingEngineRouter router;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Rebuilding order books from database...");
        AtomicInteger count = new AtomicInteger(0);

        jdbcTemplate.query("""
                SELECT id, user_id, symbol, side, order_type, price,
                       quantity, filled_quantity, status, created_at
                FROM orders
                WHERE status IN ('PENDING', 'PARTIAL')
                ORDER BY created_at ASC
                """,
                rs -> {
                    Order order = Order.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .userId(UUID.fromString(rs.getString("user_id")))
                            .symbol(rs.getString("symbol"))
                            .side(Side.valueOf(rs.getString("side")))
                            .type(OrderType.valueOf(rs.getString("order_type")))
                            .price(rs.getBigDecimal("price"))
                            .quantity(rs.getBigDecimal("quantity"))
                            .filledQuantity(rs.getBigDecimal("filled_quantity"))
                            .status(OrderStatus.valueOf(rs.getString("status")))
                            .createdAt(rs.getTimestamp("created_at").toInstant())
                            .build();

                    router.submitOrder(order);
                    count.incrementAndGet();
                });

        log.info("Order book rebuild complete. {} orders loaded across {} symbols.",
                count.get(), router.getActiveSymbols().size());
    }
}
