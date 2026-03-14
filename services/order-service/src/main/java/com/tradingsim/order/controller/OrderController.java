package com.tradingsim.order.controller;

import com.tradingsim.order.context.Context;
import com.tradingsim.order.dto.OrderDtos.*;
import com.tradingsim.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // POST /api/v1/orders
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest req,
            Context context) {
        context.validateLoggedIn();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(req, context.getUserId()));
    }

    // GET /api/v1/orders
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(Context context) {
        context.validateLoggedIn();
        return ResponseEntity.ok(orderService.getUserOrders(context.getUserId()));
    }

    // GET /api/v1/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID id,
            Context context) {
        context.validateLoggedIn();
        return ResponseEntity.ok(orderService.getOrder(id, context.getUserId()));
    }

    // DELETE /api/v1/orders/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            Context context) {
        context.validateLoggedIn();
        return ResponseEntity.ok(orderService.cancelOrder(id, context.getUserId()));
    }
}
