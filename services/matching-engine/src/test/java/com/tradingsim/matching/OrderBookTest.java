package com.tradingsim.matching;

import com.tradingsim.matching.engine.MatchResult;
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
        List<TradeEvent> trades = book.addOrder(order(Side.BUY,  100.0, 10)).trades();
        trades.addAll(book.addOrder(order(Side.SELL, 101.0, 10)).trades());

        assertThat(trades).isEmpty();
        assertThat(book.getBuyDepth()).isEqualTo(1);
        assertThat(book.getSellDepth()).isEqualTo(1);
    }

    // ── Full fill when prices cross ───────────────────────────────
    @Test
    void fullFill_whenPricesCross() {
        book.addOrder(order(Side.SELL, 100.0, 10));
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 100.0, 10)).trades();

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
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 100.0, 10)).trades(); // buy 10

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
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 101.0, 12)).trades();

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

        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 101.0, 5)).trades();

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
        List<TradeEvent> trades = book.addOrder(order(Side.BUY, 105.0, 10)).trades(); // buyer willing to pay more

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

    // ── MARKET orders (immediate-or-cancel) ───────────────────────
    private Order marketOrder(Side side, double qty, Double cap) {
        Order order = order(side, 0, qty);
        order.setType(OrderType.MARKET);
        order.setPrice(cap == null ? null : BigDecimal.valueOf(cap));
        return order;
    }

    @Test
    void marketBuy_sweepsLevelsAtRestingPrices() {
        book.addOrder(order(Side.SELL, 100.0, 5));
        book.addOrder(order(Side.SELL, 101.0, 5));
        Order buy = marketOrder(Side.BUY, 8, null);

        List<TradeEvent> trades = book.addOrder(buy).trades();

        assertThat(trades).hasSize(2);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0");
        assertThat(trades.get(1).getPrice()).isEqualByComparingTo("101.0");
        assertThat(trades.get(1).getQuantity()).isEqualByComparingTo("3");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(book.getSellDepth()).isEqualTo(1); // 101.0 sell keeps 2
        assertThat(book.getBuyDepth()).isZero();      // market order never rests
    }

    @Test
    void marketBuy_withNoLiquidity_isCancelledWithoutTrades() {
        Order buy = marketOrder(Side.BUY, 10, null);

        MatchResult result = book.addOrder(buy);

        assertThat(result.trades()).isEmpty();
        assertThat(result.cancelled()).containsExactly(buy); // reported so the DB row is cancelled
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.getBuyDepth()).isZero();
    }

    @Test
    void marketBuy_partialFill_cancelsRemainder() {
        book.addOrder(order(Side.SELL, 100.0, 4));
        Order buy = marketOrder(Side.BUY, 10, null);

        List<TradeEvent> trades = book.addOrder(buy).trades();

        assertThat(trades).hasSize(1);
        assertThat(buy.getFilledQuantity()).isEqualByComparingTo("4");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.getBuyDepth()).isZero();
        assertThat(book.getSellDepth()).isZero();
    }

    @Test
    void marketBuy_stopsAtProtectionCap() {
        book.addOrder(order(Side.SELL, 100.0, 5));
        book.addOrder(order(Side.SELL, 110.0, 5)); // beyond the cap
        Order buy = marketOrder(Side.BUY, 10, 105.0);

        List<TradeEvent> trades = book.addOrder(buy).trades();

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("100.0");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.getBestAskPrice()).hasValueSatisfying(p ->
                assertThat(p).isEqualByComparingTo("110.0"));
    }

    @Test
    void marketSell_fillsAgainstBestBidsAndRespectsCap() {
        book.addOrder(order(Side.BUY, 100.0, 5));
        book.addOrder(order(Side.BUY, 99.0, 5));
        book.addOrder(order(Side.BUY, 90.0, 5)); // below the cap
        Order sell = marketOrder(Side.SELL, 15, 95.0);

        List<TradeEvent> trades = book.addOrder(sell).trades();

        assertThat(trades).extracting(TradeEvent::getPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100.0"), new BigDecimal("99.0"));
        assertThat(sell.getFilledQuantity()).isEqualByComparingTo("10");
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.getBuyDepth()).isEqualTo(1);
    }

    // ── Self-trade prevention ─────────────────────────────────────
    private Order orderFor(UUID userId, Side side, double price, double qty) {
        Order order = order(side, price, qty);
        order.setUserId(userId);
        return order;
    }

    @Test
    void selfTrade_limit_cancelsRestingOrderAndIncomingRests() {
        UUID trader = UUID.randomUUID();
        Order restingSell = orderFor(trader, Side.SELL, 100.0, 5);
        book.addOrder(restingSell);

        Order buy = orderFor(trader, Side.BUY, 101.0, 5);
        MatchResult result = book.addOrder(buy);

        assertThat(result.trades()).isEmpty();
        assertThat(result.cancelled()).containsExactly(restingSell);
        assertThat(restingSell.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.getSellDepth()).isZero();
        assertThat(book.getBestBidPrice()).hasValueSatisfying(p ->
                assertThat(p).isEqualByComparingTo("101.0")); // the new order rests
        assertThat(book.cancelOrder(restingSell.getId())).isFalse(); // gone from the index too
    }

    @Test
    void selfTrade_limit_skipsOwnOrderAndFillsAgainstOthers() {
        UUID trader = UUID.randomUUID();
        Order ownSell = orderFor(trader, Side.SELL, 100.0, 5);
        book.addOrder(ownSell);
        book.addOrder(order(Side.SELL, 101.0, 5)); // someone else

        MatchResult result = book.addOrder(orderFor(trader, Side.BUY, 101.0, 5));

        assertThat(result.cancelled()).containsExactly(ownSell);
        assertThat(result.trades()).hasSize(1);
        TradeEvent trade = result.trades().get(0);
        assertThat(trade.getBuyerId()).isNotEqualTo(trade.getSellerId());
        assertThat(trade.getPrice()).isEqualByComparingTo("101.0");
        assertThat(book.getSellDepth()).isZero();
        assertThat(book.getBuyDepth()).isZero();
    }

    @Test
    void selfTrade_market_cancelsRestingOrderThenExpires() {
        UUID trader = UUID.randomUUID();
        Order ownBuy = orderFor(trader, Side.BUY, 100.0, 5);
        book.addOrder(ownBuy);

        Order sell = marketOrder(Side.SELL, 5, null);
        sell.setUserId(trader);
        MatchResult result = book.addOrder(sell);

        assertThat(result.trades()).isEmpty();
        assertThat(result.cancelled()).containsExactly(ownBuy, sell);
        assertThat(book.getBuyDepth()).isZero();
    }
}
