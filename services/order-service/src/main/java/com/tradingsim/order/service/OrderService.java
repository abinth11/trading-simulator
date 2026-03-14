package com.tradingsim.order.service;

import com.tradingsim.order.config.OrderEventPublisher;
import com.tradingsim.order.config.OrderEventPublisher.OrderPlacedEvent;
import com.tradingsim.order.dto.OrderDtos.*;
import com.tradingsim.order.entity.Order;
import com.tradingsim.order.entity.Order.*;
import com.tradingsim.order.exception.InsufficientBalanceException;
import com.tradingsim.order.exception.OrderNotFoundException;
import com.tradingsim.order.exception.ValidationException;
import com.tradingsim.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final BalanceService balanceService;

    // ── Place order ───────────────────────────────────────────────
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest req, UUID userId) {
        // 1. Validate LIMIT order has a price
        if (req.getOrderType() == OrderType.LIMIT && req.getPrice() == null) {
            throw new ValidationException("Price is required for LIMIT orders");
        }

        // 2. Balance check for BUY orders
        if (req.getSide() == Side.BUY) {
            BigDecimal required = req.getPrice().multiply(req.getQuantity());
            BigDecimal available = balanceService.getCashBalance(userId);
            if (available.compareTo(required) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance. Required: %.2f, Available: %.2f",
                                required, available));
            }
        }

        // 3. SELL orders: check holdings (basic check via Redis cache)
        if (req.getSide() == Side.SELL) {
            BigDecimal holdings = balanceService.getHoldings(userId, req.getSymbol());
            if (holdings.compareTo(req.getQuantity()) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient holdings. Required: %s, Available: %s",
                                req.getQuantity(), holdings));
            }
        }

        // 4. Persist order with PENDING status
        Order order = Order.builder()
                .userId(userId)
                .symbol(req.getSymbol().toUpperCase())
                .side(req.getSide())
                .orderType(req.getOrderType())
                .price(req.getPrice())
                .quantity(req.getQuantity())
                .build();

        order = orderRepository.save(order);
        log.info("Order placed: {} {} {} @ {} qty={}", order.getId(), order.getSide(),
                order.getSymbol(), order.getPrice(), order.getQuantity());

        // 5. Publish OrderPlaced event → Matching Engine picks this up
        eventPublisher.publishOrderPlaced(new OrderPlacedEvent(
                order.getId(),
                order.getUserId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getOrderType().name(),
                order.getPrice(),
                order.getQuantity(),
                Instant.now()
        ));

        return OrderResponse.from(order);
    }

    // ── Cancel order ──────────────────────────────────────────────
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.FILLED) {
            throw new ValidationException("Cannot cancel a fully filled order");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ValidationException("Order is already cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        eventPublisher.publishOrderCancelled(orderId, userId, order.getSymbol());
        log.info("Order cancelled: {}", orderId);

        return OrderResponse.from(order);
    }

    // ── Get single order ──────────────────────────────────────────
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    // ── Get all orders for user ───────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }
}
