package com.tradingsim.portfolio.service;

import com.tradingsim.portfolio.consumer.TradeExecutedEvent;
import com.tradingsim.portfolio.dto.PortfolioDtos.*;
import com.tradingsim.portfolio.entity.PortfolioHolding;
import com.tradingsim.portfolio.repository.PortfolioHoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioHoldingRepository holdingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    // ── Process a trade — called by Kafka consumer ─────────────────
    @Transactional
    public void processTrade(TradeExecutedEvent event) {
        // Record the settlement first: it makes redelivery a no-op, and it tells order-service
        // this trade's value has moved into cash/holdings and no longer counts as reserved.
        int inserted = jdbcTemplate.update(
                "INSERT INTO trade_settlements (trade_id) VALUES (?) ON CONFLICT (trade_id) DO NOTHING",
                event.getTradeId());
        if (inserted == 0) {
            log.warn("Duplicate TradeExecuted event skipped: {}", event.getTradeId());
            return;
        }

        log.info("Processing trade: {} {} qty={} price={}",
                event.getTradeId(), event.getSymbol(), event.getQuantity(), event.getPrice());

        // Update buyer: increase holdings, deduct cash
        processBuy(event.getBuyerId(), event.getSymbol(), event.getPrice(), event.getQuantity());

        // Update seller: decrease holdings, add cash
        processSell(event.getSellerId(), event.getSymbol(), event.getPrice(), event.getQuantity());
    }

    // ── Get full portfolio summary for a user ─────────────────────
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID userId) {
        BigDecimal cashBalance = getCashBalance(userId);
        List<PortfolioHolding> holdings = holdingRepository.findByUserId(userId);

        List<HoldingResponse> holdingResponses = holdings.stream()
                .filter(h -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(h -> {
                    BigDecimal lastPrice = getLastPrice(h.getSymbol());
                    BigDecimal marketValue = lastPrice.multiply(h.getQuantity());
                    BigDecimal costBasis = h.getAvgBuyPrice().multiply(h.getQuantity());
                    BigDecimal unrealizedPnl = marketValue.subtract(costBasis);
                    BigDecimal unrealizedPnlPct = costBasis.compareTo(BigDecimal.ZERO) > 0
                            ? unrealizedPnl.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return HoldingResponse.builder()
                            .symbol(h.getSymbol())
                            .quantity(h.getQuantity())
                            .avgBuyPrice(h.getAvgBuyPrice())
                            .lastPrice(lastPrice)
                            .marketValue(marketValue)
                            .unrealizedPnl(unrealizedPnl)
                            .unrealizedPnlPct(unrealizedPnlPct)
                            .build();
                })
                .toList();

        BigDecimal totalMarketValue = holdingResponses.stream()
                .map(HoldingResponse::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedPnl = holdingResponses.stream()
                .map(HoldingResponse::getUnrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PortfolioResponse.builder()
                .userId(userId)
                .cashBalance(cashBalance)
                .totalMarketValue(totalMarketValue)
                .totalPortfolioValue(cashBalance.add(totalMarketValue))
                .totalUnrealizedPnl(totalUnrealizedPnl)
                .holdings(holdingResponses)
                .build();
    }

    // ── Private: process buyer side ───────────────────────────────
    private void processBuy(UUID userId, String symbol, BigDecimal price, BigDecimal quantity) {
        BigDecimal totalCost = price.multiply(quantity);

        // 1. Deduct cash from users table
        jdbcTemplate.update(
                "UPDATE users SET cash_balance = cash_balance - ? WHERE id = ?",
                totalCost, userId);

        // 2. Upsert holding — recalculate weighted average buy price
        PortfolioHolding holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElse(PortfolioHolding.builder()
                        .userId(userId)
                        .symbol(symbol)
                        .build());

        BigDecimal existingQty   = holding.getQuantity();
        BigDecimal existingAvg   = holding.getAvgBuyPrice();
        BigDecimal newTotalQty   = existingQty.add(quantity);

        // Weighted average: (existingQty * existingAvg + newQty * newPrice) / totalQty
        BigDecimal newAvgPrice = existingQty.multiply(existingAvg)
                .add(quantity.multiply(price))
                .divide(newTotalQty, 2, RoundingMode.HALF_UP);

        holding.setQuantity(newTotalQty);
        holding.setAvgBuyPrice(newAvgPrice);
        holdingRepository.save(holding);

        log.debug("BUY processed: user={} symbol={} qty={} newAvg={}", userId, symbol, newTotalQty, newAvgPrice);
    }

    // ── Private: process seller side ──────────────────────────────
    private void processSell(UUID userId, String symbol, BigDecimal price, BigDecimal quantity) {
        BigDecimal proceeds = price.multiply(quantity);

        // 1. Add cash to users table
        jdbcTemplate.update(
                "UPDATE users SET cash_balance = cash_balance + ? WHERE id = ?",
                proceeds, userId);

        // 2. Decrease holdings
        PortfolioHolding holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new IllegalStateException(
                        "Sell trade for user with no holdings: " + userId + " " + symbol));

        BigDecimal newQty = holding.getQuantity().subtract(quantity);
        holding.setQuantity(newQty.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newQty);
        holdingRepository.save(holding);

        log.debug("SELL processed: user={} symbol={} qty={} proceeds={}", userId, symbol, quantity, proceeds);
    }

    private BigDecimal getCashBalance(UUID userId) {
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT cash_balance FROM users WHERE id = ?", BigDecimal.class, userId);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    private BigDecimal getLastPrice(String symbol) {
        String cached = redisTemplate.opsForValue().get("price:last:" + symbol);
        if (cached != null) return new BigDecimal(cached);

        // Fallback to base price from symbols table
        try {
            BigDecimal base = jdbcTemplate.queryForObject(
                    "SELECT base_price FROM symbols WHERE ticker = ?", BigDecimal.class, symbol);
            return base != null ? base : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
