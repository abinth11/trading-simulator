package com.tradingsim.order.service;

import com.tradingsim.order.dto.AdminOrderDtos.LiveOrderFeedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderStreamService {

    private final AdminOrderService adminOrderService;
    private final MarketSimulationService marketSimulationService;

    public LiveOrderFeedResponse buildSnapshot(int orderLimit, int tradeLimit) {
        return new LiveOrderFeedResponse(
                adminOrderService.getRecentOrders(orderLimit),
                adminOrderService.getRecentTrades(tradeLimit),
                adminOrderService.getSummary(),
                adminOrderService.getStatusBreakdown(),
                adminOrderService.getSymbolActivity(),
                adminOrderService.getTimeline(),
                marketSimulationService.getStatus(),
                Instant.now()
        );
    }
}
