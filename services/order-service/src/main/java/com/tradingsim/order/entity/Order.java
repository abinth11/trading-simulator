package com.tradingsim.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    @Builder.Default
    private OrderType orderType = OrderType.LIMIT;

    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    // Worst price this order may execute at — limit price, or the protection cap for MARKET orders
    @Column(name = "price_cap", precision = 18, scale = 2)
    private BigDecimal priceCap;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 6)
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    public boolean isFilled() {
        return filledQuantity.compareTo(quantity) >= 0;
    }

    public enum Side { BUY, SELL }

    public enum OrderType { LIMIT, MARKET }

    public enum OrderStatus {
        PENDING, PARTIAL, FILLED, CANCELLED, REJECTED
    }
}
