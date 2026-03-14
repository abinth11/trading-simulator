package com.tradingsim.order.repository;

import com.tradingsim.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    List<Order> findBySymbolAndStatus(String symbol, Order.OrderStatus status);

    List<Order> findByStatus(Order.OrderStatus status);
}
