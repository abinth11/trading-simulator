package com.tradingsim.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads cash balance and holdings for order validation.
 *
 * Strategy (fast path first):
 *   1. Try Redis cache (written by Portfolio Service on every trade)
 *   2. Fall back to direct DB read if cache miss
 *
 * This avoids a synchronous HTTP call to Portfolio Service,
 * keeping the order placement path fast and decoupled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String CASH_KEY   = "balance:cash:%s";       // balance:cash:{userId}
    private static final String HOLDING_KEY = "balance:holding:%s:%s"; // balance:holding:{userId}:{symbol}

    public BigDecimal getCashBalance(UUID userId) {
        // Fast path: Redis
        String cached = redisTemplate.opsForValue().get(String.format(CASH_KEY, userId));
        if (cached != null) {
            log.debug("Cache hit for cash balance: {}", userId);
            return new BigDecimal(cached);
        }

        // Fallback: DB
        log.debug("Cache miss for cash balance, reading from DB: {}", userId);
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT cash_balance FROM users WHERE id = ?",
                BigDecimal.class, userId);

        return balance != null ? balance : BigDecimal.ZERO;
    }

    public BigDecimal getHoldings(UUID userId, String symbol) {
        // Fast path: Redis
        String cached = redisTemplate.opsForValue()
                .get(String.format(HOLDING_KEY, userId, symbol.toUpperCase()));
        if (cached != null) {
            log.debug("Cache hit for holdings: {} {}", userId, symbol);
            return new BigDecimal(cached);
        }

        // Fallback: DB
        log.debug("Cache miss for holdings, reading from DB: {} {}", userId, symbol);
        try {
            BigDecimal qty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM portfolio_holdings WHERE user_id = ? AND symbol = ?",
                    BigDecimal.class, userId, symbol.toUpperCase());
            return qty != null ? qty : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO; // no holdings yet
        }
    }
}
