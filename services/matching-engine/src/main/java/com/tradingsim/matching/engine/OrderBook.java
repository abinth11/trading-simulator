package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.Side;
import com.tradingsim.matching.model.TradeEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * OrderBook for a single symbol.
 *
 * Data structures:
 *   BUY  side → max-heap  (highest price gets matched first)
 *   SELL side → min-heap  (lowest price gets matched first)
 *
 * Matching rule (price-time priority):
 *   best BUY price >= best SELL price → execute at SELL price
 *
 * This class is NOT thread-safe by design.
 * Thread safety is enforced by the SymbolEngine wrapper (one thread per symbol).
 */
@Slf4j
public class OrderBook {

    private final String symbol;

    // BUY orders: highest price first, then earliest time first (price-time priority)
    @Getter
    private final PriorityQueue<Order> buyOrders = new PriorityQueue<>(
            Comparator
                .comparing(Order::getPrice, Comparator.reverseOrder()) // highest price first
                .thenComparing(Order::getCreatedAt)                    // earliest time first
    );

    // SELL orders: lowest price first, then earliest time first
    @Getter
    private final PriorityQueue<Order> sellOrders = new PriorityQueue<>(
            Comparator
                .comparing(Order::getPrice)         // lowest price first
                .thenComparing(Order::getCreatedAt) // earliest time first
    );

    // Fast lookup by orderId (for cancellations)
    private final Map<UUID, Order> orderIndex = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Add an order to the book and attempt to match it immediately.
     * Returns a list of TradeEvents produced (empty if no match).
     */
    public List<TradeEvent> addOrder(Order order) {
        log.debug("Adding {} {} order: price={} qty={} id={}",
                order.getSide(), symbol, order.getPrice(), order.getQuantity(), order.getId());

        orderIndex.put(order.getId(), order);
        List<TradeEvent> trades = new ArrayList<>();

        if (order.getSide() == Side.BUY) {
            buyOrders.offer(order);
            match(trades);
        } else {
            sellOrders.offer(order);
            match(trades);
        }

        return trades;
    }

    /**
     * Cancel an order by ID. Returns true if found and cancelled.
     */
    public boolean cancelOrder(UUID orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) return false;

        order.setStatus(Order.OrderStatus.CANCELLED);

        // Remove from the appropriate heap
        // Note: PriorityQueue.remove() is O(n) — acceptable for a portfolio project.
        // Production systems use a more sophisticated cancel mechanism (lazy deletion).
        if (order.getSide() == Side.BUY) {
            buyOrders.remove(order);
        } else {
            sellOrders.remove(order);
        }

        log.debug("Cancelled order {} for symbol {}", orderId, symbol);
        return true;
    }

    /**
     * Core matching loop.
     * Runs until no more matches are possible.
     */
    private void match(List<TradeEvent> trades) {
        while (canMatch()) {
            Order bestBuy  = buyOrders.peek();
            Order bestSell = sellOrders.peek();

            // Determine fill quantity — the smaller of the two remaining quantities
            BigDecimal fillQty = bestBuy.getRemainingQuantity()
                    .min(bestSell.getRemainingQuantity());

            // Execute at the sell price (price-time priority convention)
            BigDecimal execPrice = bestSell.getPrice();

            // Update filled quantities
            bestBuy.setFilledQuantity(bestBuy.getFilledQuantity().add(fillQty));
            bestSell.setFilledQuantity(bestSell.getFilledQuantity().add(fillQty));

            // Update order statuses
            updateStatus(bestBuy);
            updateStatus(bestSell);

            // Build trade event
            TradeEvent trade = TradeEvent.builder()
                    .tradeId(UUID.randomUUID())
                    .buyOrderId(bestBuy.getId())
                    .sellOrderId(bestSell.getId())
                    .buyerId(bestBuy.getUserId())
                    .sellerId(bestSell.getUserId())
                    .symbol(symbol)
                    .price(execPrice)
                    .quantity(fillQty)
                    .executedAt(Instant.now())
                    .build();

            trades.add(trade);
            log.info("TRADE EXECUTED: {}", trade);

            // Remove fully filled orders from the book
            if (bestBuy.isFilled()) {
                buyOrders.poll();
                orderIndex.remove(bestBuy.getId());
            }
            if (bestSell.isFilled()) {
                sellOrders.poll();
                orderIndex.remove(bestSell.getId());
            }
        }
    }

    /**
     * A match is possible when:
     * 1. Both sides have orders
     * 2. Best buy price >= best sell price
     */
    private boolean canMatch() {
        if (buyOrders.isEmpty() || sellOrders.isEmpty()) return false;

        BigDecimal bestBuyPrice  = buyOrders.peek().getPrice();
        BigDecimal bestSellPrice = sellOrders.peek().getPrice();

        return bestBuyPrice.compareTo(bestSellPrice) >= 0;
    }

    private void updateStatus(Order order) {
        if (order.isFilled()) {
            order.setStatus(Order.OrderStatus.FILLED);
        } else if (order.isPartiallyFilled()) {
            order.setStatus(Order.OrderStatus.PARTIAL);
        }
    }

    // ── Snapshot helpers (for API / WebSocket feeds) ──────────────

    public int getBuyDepth()  { return buyOrders.size(); }
    public int getSellDepth() { return sellOrders.size(); }

    public Optional<BigDecimal> getBestBidPrice() {
        return buyOrders.isEmpty()
                ? Optional.empty()
                : Optional.of(buyOrders.peek().getPrice());
    }

    public Optional<BigDecimal> getBestAskPrice() {
        return sellOrders.isEmpty()
                ? Optional.empty()
                : Optional.of(sellOrders.peek().getPrice());
    }

    public String getSymbol() { return symbol; }
}
