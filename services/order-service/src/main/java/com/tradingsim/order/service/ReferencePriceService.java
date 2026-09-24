package com.tradingsim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Best-known current price for a symbol, used to size MARKET orders and to seed simulation quotes.
 *
 * Lookup order (freshest first):
 *   1. Last traded price in Redis (written by the matching engine on every trade)
 *   2. Last persisted trade
 *   3. Most recent limit order price
 *   4. NSE bootstrap price
 */
@Service
@RequiredArgsConstructor
public class ReferencePriceService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final NseMarketDataService nseMarketDataService;

    public BigDecimal resolve(String symbol) {
        String cachedPrice = redisTemplate.opsForValue().get("price:last:" + symbol);
        if (cachedPrice != null) {
            return new BigDecimal(cachedPrice);
        }

        try {
            BigDecimal tradePrice = jdbcTemplate.queryForObject(
                    "SELECT price FROM trades WHERE symbol = ? ORDER BY executed_at DESC LIMIT 1",
                    BigDecimal.class,
                    symbol
            );
            if (tradePrice != null) {
                return tradePrice;
            }
        } catch (DataAccessException ignored) {
        }

        try {
            BigDecimal orderPrice = jdbcTemplate.queryForObject(
                    "SELECT price FROM orders WHERE symbol = ? AND price IS NOT NULL ORDER BY updated_at DESC LIMIT 1",
                    BigDecimal.class,
                    symbol
            );
            if (orderPrice != null) {
                return orderPrice;
            }
        } catch (DataAccessException ignored) {
        }

        return nseMarketDataService.getBootstrapPrice(symbol);
    }
}
