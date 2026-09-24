package com.tradingsim.matching;

import com.tradingsim.matching.engine.OrderBook;
import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.Order.*;
import com.tradingsim.matching.model.TradeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook("RELIANCE");
    }

    // ── Helper ────────────────────────────────────────────────────
    private Order order(Side side, double price, double qty) {
        return Order.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .symbol("RELIANCE")
                .side(side)
                .type(OrderType.LIMIT)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    // ── No match when prices don't cross ─────────────────────────
    @Test
    void noMatch_whenBuyPriceLowerThanSellPrice() {
        List<TradeEvent> trades = book.addOrder(order(Side.BUY,  100.0, 10));
        trades.addAll(book.addOrder(order(Side.SELL, 101.0, 10)));

        assertThat(trades).isEmpty();
        assertThat(book.getBuyDepth()).isEqualTo(1);
        assertThat(book.getSellDepth()).isEqualTo(1);
    }

    // ── Full fill when prices cross ───────────────────────────────
    @Test
    void fullFill_whenPricesCross() {
        book.addOrder(order(Side.SELL, 100.0, 10));
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 100.0, 10));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0");
        assertThat(trades.get(0).getQuantity()).isEqualByComparingTo("10");

        // Both orders consumed — book should be empty
        assertThat(book.getBuyDepth()).isEqualTo(0);
        assertThat(book.getSellDepth()).isEqualTo(0);
    }

    // ── Partial fill ──────────────────────────────────────────────
    @Test
    void partialFill_whenBuyQuantityLargerThanSell() {
        book.addOrder(order(Side.SELL, 100.0, 5));   // sell 5
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 100.0, 10)); // buy 10

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getQuantity()).isEqualByComparingTo("5"); // only 5 filled

        // Sell fully consumed, buy still has 5 remaining
        assertThat(book.getSellDepth()).isEqualTo(0);
        assertThat(book.getBuyDepth()).isEqualTo(1);
        assertThat(book.getBestBidPrice()).isPresent();
    }

    // ── Multiple fills from one large order ───────────────────────
    @Test
    void multipleFills_oneOrderConsumesManySells() {
        book.addOrder(order(Side.SELL, 100.0, 5));
        book.addOrder(order(Side.SELL, 100.5, 5));
        book.addOrder(order(Side.SELL, 101.0, 5));

        // Buy 12 — should fill the 100.0 and 100.5 sells (10 total), partial on 101.0
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 101.0, 12));

        assertThat(trades).hasSize(3); // filled 100.0 and 100.5 sells, partial on 101.0
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0");
        assertThat(trades.get(1).getPrice()).isEqualByComparingTo("100.5");
        assertThat(trades.get(2).getPrice()).isEqualByComparingTo("101.0");
        assertThat(trades.get(2).getQuantity()).isEqualByComparingTo("2");
        assertThat(book.getSellDepth()).isEqualTo(1); // 101.0 sell remains with 3 qty
    }

    // ── Price-time priority ───────────────────────────────────────
    @Test
    void priceTimePriority_bestPriceMatchedFirst() throws InterruptedException {
        Order sell1 = order(Side.SELL, 101.0, 5);
        Thread.sleep(1); // ensure time ordering
        Order sell2 = order(Side.SELL, 100.0, 5); // better price — should fill first

        book.addOrder(sell1);
        book.addOrder(sell2);

        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 101.0, 5));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0"); // sell2 matched first
        assertThat(trades.get(0).getSellOrderId()).isEqualTo(sell2.getId());
    }

    // ── Cancellation ──────────────────────────────────────────────
    @Test
    void cancelOrder_removesFromBook() {
        Order buy = order(Side.BUY, 100.0, 10);
        book.addOrder(buy);
        assertThat(book.getBuyDepth()).isEqualTo(1);

        boolean cancelled = book.cancelOrder(buy.getId());

        assertThat(cancelled).isTrue();
        assertThat(book.getBuyDepth()).isEqualTo(0);
    }

    @Test
    void cancelOrder_returnsFalse_ifNotFound() {
        boolean result = book.cancelOrder(UUID.randomUUID());
        assertThat(result).isFalse();
    }

    // ── Execution price is sell price ─────────────────────────────
    @Test
    void executionPrice_isSellPrice() {
        book.addOrder(order(Side.SELL, 100.0, 10));
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 105.0, 10)); // buyer willing to pay more

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0"); // executed at sell price
    }

    // ── Best bid/ask ──────────────────────────────────────────────
    @Test
    void bestBidAsk_returnsCorrectPrices() {
        book.addOrder(order(Side.BUY, 99.0, 5));
        book.addOrder(order(Side.BUY, 100.0, 5));  // best bid
        book.addOrder(order(Side.SELL, 101.0, 5)); // best ask
        book.addOrder(order(Side.SELL, 102.0, 5));

        assertThat(book.getBestBidPrice()).hasValueSatisfying(p ->
                assertThat(p).isEqualByComparingTo("100.0"));
        assertThat(book.getBestAskPrice()).hasValueSatisfying(p ->
                assertThat(p).isEqualByComparingTo("101.0"));
    }
}
