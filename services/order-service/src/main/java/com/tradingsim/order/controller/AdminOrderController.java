package com.tradingsim.order.controller;

import com.tradingsim.order.dto.AdminOrderDtos.AdminOrderResponse;
import com.tradingsim.order.dto.AdminOrderDtos.AdminOrderSummaryResponse;
import com.tradingsim.order.dto.AdminOrderDtos.AdminTradeResponse;
import com.tradingsim.order.dto.AdminOrderDtos.StatusBreakdownItem;
import com.tradingsim.order.dto.AdminOrderDtos.SymbolActivityItem;
import com.tradingsim.order.dto.AdminOrderDtos.TimelinePoint;
import com.tradingsim.order.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("/recent")
    public ResponseEntity<List<AdminOrderResponse>> getRecentOrders(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(adminOrderService.getRecentOrders(limit));
    }

    @GetMapping("/trades/recent")
    public ResponseEntity<List<AdminTradeResponse>> getRecentTrades(
            @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(adminOrderService.getRecentTrades(limit));
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminOrderSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminOrderService.getSummary());
    }

    @GetMapping("/status-breakdown")
    public ResponseEntity<List<StatusBreakdownItem>> getStatusBreakdown() {
        return ResponseEntity.ok(adminOrderService.getStatusBreakdown());
    }

    @GetMapping("/symbol-activity")
    public ResponseEntity<List<SymbolActivityItem>> getSymbolActivity() {
        return ResponseEntity.ok(adminOrderService.getSymbolActivity());
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TimelinePoint>> getTimeline() {
        return ResponseEntity.ok(adminOrderService.getTimeline());
    }
}
