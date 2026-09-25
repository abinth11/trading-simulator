package com.tradingsim.portfolio.consumer;

import com.tradingsim.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {

    private final PortfolioService portfolioService;

    // Redeliveries are safe: processTrade records each trade in trade_settlements
    // within the same transaction and skips trades it has already settled.
    @KafkaListener(
            topics = "${kafka.topics.trade-executed}",
            groupId = "portfolio-service-group"
    )
    public void onTradeExecuted(TradeExecutedEvent event) {
        try {
            portfolioService.processTrade(event);
        } catch (Exception e) {
            log.error("Failed to process trade {}: {}", event.getTradeId(), e.getMessage(), e);
            throw e; // rethrow so Kafka retries
        }
    }
}
