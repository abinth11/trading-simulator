package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.OrderType;
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
 *   best BUY price >= best SELL price → execute at the resting order's price
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
     * Returns the trades produced and any orders the engine cancelled along the way.
     */
    public MatchResult addOrder(Order order) {
        log.debug("Adding {} {} {} order: price={} qty={} id={}",
                order.getSide(), order.getType(), symbol, order.getPrice(), order.getQuantity(), order.getId());

        MatchResult result = new MatchResult(new ArrayList<>(), new ArrayList<>());

        if (order.getType() == OrderType.MARKET) {
            matchMarket(order, result);
            return result;
        }

        orderIndex.put(order.getId(), order);

        if (order.getSide() == Side.BUY) {
            buyOrders.offer(order);
        } else {
            sellOrders.offer(order);
        }
        match(order, result);

        return result;
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
     *
     * The book is never crossed before an order arrives, so every match here
     * pairs the incoming order with a resting one.
     */
    private void match(Order incoming, MatchResult result) {
        while (canMatch()) {
            Order bestBuy  = buyOrders.peek();
            Order bestSell = sellOrders.peek();

            // Self-trade prevention: the newer instruction wins, the user's resting order is cancelled
            if (bestBuy.getUserId().equals(bestSell.getUserId())) {
                cancelResting(bestBuy == incoming ? bestSell : bestBuy, result);
                continue;
            }

            // Determine fill quantity — the smaller of the two remaining quantities
            BigDecimal fillQty = bestBuy.getRemainingQuantity()
                    .min(bestSell.getRemainingQuantity());

            // Execute at the resting order's price — it was in the book first, so the incoming
            // order gets any price improvement
            Order resting = bestBuy == incoming ? bestSell : bestBuy;
            result.trades().add(execute(bestBuy, bestSell, resting.getPrice(), fillQty));

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
     * MARKET orders are immediate-or-cancel: they sweep the opposite side at each resting
     * order's price, never rest on the book, and any unfilled remainder is cancelled.
     * A non-null price is the market-protection cap — the order won't fill beyond it.
     */
    private void matchMarket(Order order, MatchResult result) {
        boolean isBuy = order.getSide() == Side.BUY;
        PriorityQueue<Order> opposite = isBuy ? sellOrders : buyOrders;

        while (!order.isFilled() && !opposite.isEmpty() && withinCap(order, opposite.peek().getPrice())) {
            Order resting = opposite.peek();

            // Self-trade prevention: the newer instruction wins, the user's resting order is cancelled
            if (resting.getUserId().equals(order.getUserId())) {
                cancelResting(resting, result);
                continue;
            }

            BigDecimal fillQty = order.getRemainingQuantity().min(resting.getRemainingQuantity());

            result.trades().add(isBuy
                    ? execute(order, resting, resting.getPrice(), fillQty)
                    : execute(resting, order, resting.getPrice(), fillQty));

            if (resting.isFilled()) {
                opposite.poll();
                orderIndex.remove(resting.getId());
            }
        }

        if (!order.isFilled()) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            result.cancelled().add(order);
            log.debug("MARKET order {} expired with {} unfilled", order.getId(), order.getRemainingQuantity());
        }
    }

    /** Removes a resting order that sits at the top of its side and marks it cancelled. */
    private void cancelResting(Order resting, MatchResult result) {
        (resting.getSide() == Side.BUY ? buyOrders : sellOrders).poll();
        orderIndex.remove(resting.getId());
        resting.setStatus(Order.OrderStatus.CANCELLED);
        result.cancelled().add(resting);
        log.info("Self-trade prevented: cancelled resting {} order {} for user {}",
                resting.getSide(), resting.getId(), resting.getUserId());
    }

    private boolean withinCap(Order marketOrder, BigDecimal restingPrice) {
        BigDecimal cap = marketOrder.getPrice();
        if (cap == null) return true;
        return marketOrder.getSide() == Side.BUY
                ? restingPrice.compareTo(cap) <= 0
                : restingPrice.compareTo(cap) >= 0;
    }

    /** Fills both orders by fillQty at execPrice and returns the resulting trade. */
    private TradeEvent execute(Order buy, Order sell, BigDecimal execPrice, BigDecimal fillQty) {
        buy.setFilledQuantity(buy.getFilledQuantity().add(fillQty));
        sell.setFilledQuantity(sell.getFilledQuantity().add(fillQty));

        updateStatus(buy);
        updateStatus(sell);

        TradeEvent trade = TradeEvent.builder()
                .tradeId(UUID.randomUUID())
                .buyOrderId(buy.getId())
                .sellOrderId(sell.getId())
                .buyerId(buy.getUserId())
                .sellerId(sell.getUserId())
                .symbol(symbol)
                .price(execPrice)
                .quantity(fillQty)
                .executedAt(Instant.now())
                .build();

        log.info("TRADE EXECUTED: {}", trade);
        return trade;
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
