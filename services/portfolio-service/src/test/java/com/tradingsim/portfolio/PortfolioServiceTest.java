package com.tradingsim.portfolio;

import com.tradingsim.portfolio.consumer.TradeExecutedEvent;
import com.tradingsim.portfolio.entity.PortfolioHolding;
import com.tradingsim.portfolio.repository.PortfolioHoldingRepository;
import com.tradingsim.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioHoldingRepository holdingRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock StringRedisTemplate redisTemplate;

    @InjectMocks PortfolioService portfolioService;

    private final UUID buyerId  = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        givenSettlementIsNew(true);
        lenient().when(holdingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // Lenient: the other jdbcTemplate.update calls (cash changes) use different arguments
    private void givenSettlementIsNew(boolean isNew) {
        lenient().when(jdbcTemplate.update(contains("trade_settlements"), any(UUID.class)))
                .thenReturn(isNew ? 1 : 0);
    }

    // Every trade settles both sides, so buyer-focused tests still need a seller with shares
    private void givenSellerHolds(String quantity) {
        when(holdingRepository.findByUserIdAndSymbol(sellerId, "RELIANCE"))
                .thenReturn(Optional.of(PortfolioHolding.builder()
                        .userId(sellerId).symbol("RELIANCE")
                        .quantity(new BigDecimal(quantity))
                        .avgBuyPrice(new BigDecimal("2700.00"))
                        .build()));
    }

    @Test
    void processTrade_buyer_getsHoldings() {
        when(holdingRepository.findByUserIdAndSymbol(buyerId, "RELIANCE"))
                .thenReturn(Optional.empty()); // no existing holding
        givenSellerHolds("50");

        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                buyerId, sellerId, "RELIANCE",
                new BigDecimal("2885.00"), new BigDecimal("10"),
                Instant.now()
        );

        portfolioService.processTrade(event);

        // Verify holding was saved
        verify(holdingRepository).save(argThat(h ->
                h.getQuantity().compareTo(new BigDecimal("10")) == 0 &&
                h.getAvgBuyPrice().compareTo(new BigDecimal("2885.00")) == 0
        ));

        // Verify cash was deducted
        verify(jdbcTemplate).update(
                contains("cash_balance = cash_balance -"),
                eq(new BigDecimal("28850.00")), eq(buyerId)
        );
    }

    @Test
    void processTrade_weightedAverage_calculatedCorrectly() {
        // Existing holding: 10 shares @ 2800
        PortfolioHolding existing = PortfolioHolding.builder()
                .userId(buyerId).symbol("RELIANCE")
                .quantity(new BigDecimal("10"))
                .avgBuyPrice(new BigDecimal("2800.00"))
                .build();

        when(holdingRepository.findByUserIdAndSymbol(buyerId, "RELIANCE"))
                .thenReturn(Optional.of(existing));
        givenSellerHolds("50");

        // New trade: buy 10 more @ 2900
        // Expected avg: (10*2800 + 10*2900) / 20 = 2850
        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                buyerId, sellerId, "RELIANCE",
                new BigDecimal("2900.00"), new BigDecimal("10"),
                Instant.now()
        );

        portfolioService.processTrade(event);

        verify(holdingRepository).save(argThat(h ->
                h.getQuantity().compareTo(new BigDecimal("20")) == 0 &&
                h.getAvgBuyPrice().compareTo(new BigDecimal("2850.00")) == 0
        ));
    }

    @Test
    void processTrade_seller_holdingsDecreased() {
        PortfolioHolding holding = PortfolioHolding.builder()
                .userId(sellerId).symbol("RELIANCE")
                .quantity(new BigDecimal("20"))
                .avgBuyPrice(new BigDecimal("2800.00"))
                .build();

        when(holdingRepository.findByUserIdAndSymbol(sellerId, "RELIANCE"))
                .thenReturn(Optional.of(holding));
        when(holdingRepository.findByUserIdAndSymbol(buyerId, "RELIANCE"))
                .thenReturn(Optional.empty());

        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                buyerId, sellerId, "RELIANCE",
                new BigDecimal("2900.00"), new BigDecimal("5"),
                Instant.now()
        );

        portfolioService.processTrade(event);

        // Verify qty reduced from 20 to 15
        verify(holdingRepository).save(argThat(h ->
                h.getQuantity().compareTo(new BigDecimal("15")) == 0
        ));

        // Verify cash added (5 * 2900 = 14500)
        verify(jdbcTemplate).update(
                contains("cash_balance = cash_balance +"),
                eq(new BigDecimal("14500.00")), eq(sellerId)
        );
    }

    @Test
    void processTrade_alreadySettled_isSkipped() {
        givenSettlementIsNew(false); // Kafka redelivered a trade we already applied

        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                buyerId, sellerId, "RELIANCE",
                new BigDecimal("2900.00"), new BigDecimal("5"),
                Instant.now()
        );

        portfolioService.processTrade(event);

        verify(jdbcTemplate, never()).update(contains("cash_balance"), any(BigDecimal.class), any(UUID.class));
        verifyNoInteractions(holdingRepository);
    }
}
