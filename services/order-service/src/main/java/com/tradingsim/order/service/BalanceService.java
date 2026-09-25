package com.tradingsim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Computes what a user can still commit to new orders.
 *
 * Nothing is stored as "reserved" — it is derived from the order and trade tables:
 *
 *   available cash   = cash_balance
 *                      − Σ open BUY orders   (remaining qty × price cap)
 *                      − Σ unsettled BUY trades (price × qty)
 *
 *   available shares = holding quantity
 *                      − Σ open SELL orders  (remaining qty)
 *                      − Σ unsettled SELL trades (qty)
 *
 * A fill moves value from "open order" to "unsettled trade" (engine transaction), and settlement
 * moves it from "unsettled trade" into cash_balance / holdings (portfolio transaction). Each step
 * is atomic, so the reservation is always consistent and needs no release bookkeeping.
 *
 * Callers must hold {@link #lockAccount} for the duration of their transaction so concurrent
 * orders from the same user are checked one at a time.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final JdbcTemplate jdbcTemplate;

    /** Serialises order placement per user until the surrounding transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAccount(UUID userId) {
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, userId);
    }

    public BigDecimal getAvailableCash(UUID userId) {
        BigDecimal available = jdbcTemplate.queryForObject("""
                SELECT u.cash_balance
                     - COALESCE((SELECT SUM(COALESCE(o.price_cap, o.price) * (o.quantity - o.filled_quantity))
                                 FROM orders o
                                 WHERE o.user_id = u.id AND o.side = 'BUY'
                                   AND o.status IN ('PENDING', 'PARTIAL')), 0)
                     - COALESCE((SELECT SUM(t.price * t.quantity)
                                 FROM trades t
                                 WHERE t.buyer_id = u.id
                                   AND NOT EXISTS (SELECT 1 FROM trade_settlements s WHERE s.trade_id = t.id)), 0)
                FROM users u
                WHERE u.id = ?
                """, BigDecimal.class, userId);

        return available != null ? available : BigDecimal.ZERO;
    }

    public BigDecimal getAvailableHoldings(UUID userId, String symbol) {
        BigDecimal available = jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT h.quantity
                                 FROM portfolio_holdings h
                                 WHERE h.user_id = ? AND h.symbol = ?), 0)
                     - COALESCE((SELECT SUM(o.quantity - o.filled_quantity)
                                 FROM orders o
                                 WHERE o.user_id = ? AND o.symbol = ? AND o.side = 'SELL'
                                   AND o.status IN ('PENDING', 'PARTIAL')), 0)
                     - COALESCE((SELECT SUM(t.quantity)
                                 FROM trades t
                                 WHERE t.seller_id = ? AND t.symbol = ?
                                   AND NOT EXISTS (SELECT 1 FROM trade_settlements s WHERE s.trade_id = t.id)), 0)
                """, BigDecimal.class,
                userId, symbol, userId, symbol, userId, symbol);

        return available != null ? available : BigDecimal.ZERO;
    }
}
