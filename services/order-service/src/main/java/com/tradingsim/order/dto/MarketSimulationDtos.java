package com.tradingsim.order.dto;

import java.time.Instant;
import java.util.List;

public class MarketSimulationDtos {

    public record StartMarketSimulationRequest(
            Long intervalMs,
            Integer ordersPerTick,
            List<String> symbols
    ) {}

    public record MarketSimulationStatusResponse(
            boolean running,
            long intervalMs,
            int ordersPerTick,
            List<String> symbols,
            Instant startedAt,
            Instant stoppedAt,
            Instant lastTickAt,
            long ticksExecuted,
            long generatedOrders,
            long successfulOrders,
            long failedOrders,
            String lastError
    ) {}
}
