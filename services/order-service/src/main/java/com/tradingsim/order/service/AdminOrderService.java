package com.tradingsim.order.service;

import com.tradingsim.order.dto.AdminOrderDtos.AdminOrderResponse;
import com.tradingsim.order.dto.AdminOrderDtos.AdminOrderSummaryResponse;
import com.tradingsim.order.dto.AdminOrderDtos.AdminTradeResponse;
import com.tradingsim.order.dto.AdminOrderDtos.StatusBreakdownItem;
import com.tradingsim.order.dto.AdminOrderDtos.SymbolActivityItem;
import com.tradingsim.order.dto.AdminOrderDtos.TimelinePoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> getRecentOrders(int limit) {
        return jdbcTemplate.query("""
                SELECT o.id, o.user_id, u.username, o.symbol, o.side, o.order_type, o.price,
                       o.quantity, o.filled_quantity, o.status, o.created_at, o.updated_at
                FROM orders o
                JOIN users u ON u.id = o.user_id
                ORDER BY o.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new AdminOrderResponse(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("username"),
                        rs.getString("symbol"),
                        rs.getString("side"),
                        rs.getString("order_type"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("filled_quantity"),
                        rs.getBigDecimal("quantity").subtract(rs.getBigDecimal("filled_quantity")),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at"))
                ),
                limit
        );
    }

    @Transactional(readOnly = true)
    public List<AdminTradeResponse> getRecentTrades(int limit) {
        return jdbcTemplate.query("""
                SELECT t.id, t.symbol, t.price, t.quantity, t.executed_at,
                       buyer.username AS buyer_name,
                       seller.username AS seller_name
                FROM trades t
                JOIN users buyer ON buyer.id = t.buyer_id
                JOIN users seller ON seller.id = t.seller_id
                ORDER BY t.executed_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new AdminTradeResponse(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("symbol"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("buyer_name"),
                        rs.getString("seller_name"),
                        toInstant(rs.getTimestamp("executed_at"))
                ),
                limit
        );
    }

    @Transactional(readOnly = true)
    public AdminOrderSummaryResponse getSummary() {
        Long totalOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        Long openOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status IN ('PENDING', 'PARTIAL', 'CANCELLING')", Long.class);
        Long filledOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'FILLED'", Long.class);
        Long cancelledOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED'", Long.class);
        Long rejectedOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'REJECTED'", Long.class);
        Long tradesToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE executed_at >= CURRENT_DATE", Long.class);
        BigDecimal tradedNotional = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(price * quantity), 0) FROM trades WHERE executed_at >= CURRENT_DATE",
                BigDecimal.class);

        return new AdminOrderSummaryResponse(
                value(totalOrders),
                value(openOrders),
                value(filledOrders),
                value(cancelledOrders),
                value(rejectedOrders),
                value(tradesToday),
                tradedNotional != null ? tradedNotional : BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public List<StatusBreakdownItem> getStatusBreakdown() {
        return jdbcTemplate.query("""
                SELECT status, COUNT(*) AS count
                FROM orders
                GROUP BY status
                ORDER BY count DESC
                """,
                (rs, rowNum) -> new StatusBreakdownItem(
                        rs.getString("status"),
                        rs.getLong("count")
                )
        );
    }

    @Transactional(readOnly = true)
    public List<SymbolActivityItem> getSymbolActivity() {
        return jdbcTemplate.query("""
                SELECT o.symbol,
                       COUNT(*) AS orders,
                       COUNT(*) FILTER (WHERE o.status = 'FILLED') AS filled_orders,
                       COALESCE(SUM(t.price * t.quantity), 0) AS traded_notional
                FROM orders o
                LEFT JOIN trades t ON t.symbol = o.symbol
                GROUP BY o.symbol
                ORDER BY orders DESC, o.symbol ASC
                """,
                (rs, rowNum) -> new SymbolActivityItem(
                        rs.getString("symbol"),
                        rs.getLong("orders"),
                        rs.getLong("filled_orders"),
                        rs.getBigDecimal("traded_notional")
                )
        );
    }

    @Transactional(readOnly = true)
    public List<TimelinePoint> getTimeline() {
        return jdbcTemplate.query("""
                WITH hourly_orders AS (
                    SELECT to_char(date_trunc('hour', created_at), 'HH24:00') AS bucket,
                           COUNT(*) AS orders,
                           COUNT(*) FILTER (WHERE status = 'FILLED') AS fills
                    FROM orders
                    WHERE created_at >= CURRENT_DATE
                    GROUP BY date_trunc('hour', created_at)
                ),
                hourly_trades AS (
                    SELECT to_char(date_trunc('hour', executed_at), 'HH24:00') AS bucket,
                           COALESCE(SUM(price * quantity), 0) AS volume
                    FROM trades
                    WHERE executed_at >= CURRENT_DATE
                    GROUP BY date_trunc('hour', executed_at)
                )
                SELECT COALESCE(o.bucket, t.bucket) AS bucket,
                       COALESCE(o.orders, 0) AS orders,
                       COALESCE(o.fills, 0) AS fills,
                       COALESCE(t.volume, 0) AS volume
                FROM hourly_orders o
                FULL OUTER JOIN hourly_trades t ON t.bucket = o.bucket
                ORDER BY bucket ASC
                """,
                (rs, rowNum) -> new TimelinePoint(
                        rs.getString("bucket"),
                        rs.getLong("orders"),
                        rs.getLong("fills"),
                        rs.getBigDecimal("volume")
                )
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private static long value(Long number) {
        return number != null ? number : 0L;
    }
}
