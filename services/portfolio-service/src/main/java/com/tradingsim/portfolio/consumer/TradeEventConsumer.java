package com.tradingsim.portfolio.consumer;

import com.tradingsim.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {

    private final PortfolioService portfolioService;
    private final StringRedisTemplate redisTemplate;

    // Deduplication TTL — 24 hours is enough to handle any redelivery window
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final String DEDUP_KEY   = "portfolio:processed:%s";

    @KafkaListener(
            topics = "${kafka.topics.trade-executed}",
            groupId = "portfolio-service-group"
    )
    public void onTradeExecuted(TradeExecutedEvent event) {
        String dedupKey = String.format(DEDUP_KEY, event.getTradeId());

        // Idempotency check — if already processed, skip
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", DEDUP_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("Duplicate TradeExecuted event skipped: {}", event.getTradeId());
            return;
        }

        try {
            portfolioService.processTrade(event);
        } catch (Exception e) {
            // Remove dedup key so the event can be retried
            redisTemplate.delete(dedupKey);
            log.error("Failed to process trade {}: {}", event.getTradeId(), e.getMessage(), e);
            throw e; // rethrow so Kafka retries
        }
    }
}
