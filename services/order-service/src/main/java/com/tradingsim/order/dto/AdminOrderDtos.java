package com.tradingsim.order.dto;

import com.tradingsim.order.dto.MarketSimulationDtos.MarketSimulationStatusResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AdminOrderDtos {

    public record AdminOrderResponse(
            UUID id,
            UUID userId,
            String username,
            String symbol,
            String side,
            String orderType,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal filledQuantity,
            BigDecimal remainingQuantity,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AdminTradeResponse(
            UUID id,
            String symbol,
            BigDecimal price,
            BigDecimal quantity,
            String buyer,
            String seller,
            Instant executedAt
    ) {}

    public record AdminOrderSummaryResponse(
            long totalOrders,
            long openOrders,
            long filledOrders,
            long cancelledOrders,
            long rejectedOrders,
            long tradesToday,
            BigDecimal tradedNotional
    ) {}

    public record StatusBreakdownItem(String status, long count) {}

    public record SymbolActivityItem(
            String symbol,
            long orders,
            long filledOrders,
            BigDecimal tradedNotional
    ) {}

    public record TimelinePoint(
            String bucket,
            long orders,
            long fills,
            BigDecimal volume
    ) {}

    public record LiveOrderFeedResponse(
            List<AdminOrderResponse> recentOrders,
            List<AdminTradeResponse> recentTrades,
            AdminOrderSummaryResponse summary,
            List<StatusBreakdownItem> statusBreakdown,
            List<SymbolActivityItem> symbolActivity,
            List<TimelinePoint> timeline,
            MarketSimulationStatusResponse simulationStatus,
            Instant generatedAt
    ) {}
}
