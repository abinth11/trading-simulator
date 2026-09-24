package com.tradingsim.order;

import com.tradingsim.order.config.OrderEventPublisher;
import com.tradingsim.order.config.OrderEventPublisher.OrderPlacedEvent;
import com.tradingsim.order.dto.OrderDtos.PlaceOrderRequest;
import com.tradingsim.order.entity.Order.OrderType;
import com.tradingsim.order.entity.Order.Side;
import com.tradingsim.order.exception.InsufficientBalanceException;
import com.tradingsim.order.exception.ValidationException;
import com.tradingsim.order.repository.OrderRepository;
import com.tradingsim.order.service.BalanceService;
import com.tradingsim.order.service.OrderService;
import com.tradingsim.order.service.ReferencePriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventPublisher eventPublisher;
    @Mock StringRedisTemplate redisTemplate;
    @Mock BalanceService balanceService;
    @Mock ReferencePriceService referencePriceService;

    @InjectMocks OrderService orderService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "marketProtectionPct", new BigDecimal("5"));
    }

    private PlaceOrderRequest request(Side side, OrderType type, String price, String qty) {
        var req = new PlaceOrderRequest();
        req.setSymbol("infy");
        req.setSide(side);
        req.setOrderType(type);
        req.setPrice(price == null ? null : new BigDecimal(price));
        req.setQuantity(new BigDecimal(qty));
        return req;
    }

    private OrderPlacedEvent publishedEvent() {
        var captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishOrderPlaced(captor.capture());
        return captor.getValue();
    }

    @Test
    void marketBuy_isCheckedAndSentWithProtectionCap() {
        when(referencePriceService.resolve("INFY")).thenReturn(new BigDecimal("1000.00"));
        when(balanceService.getCashBalance(userId)).thenReturn(new BigDecimal("100000.00"));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = orderService.placeOrder(request(Side.BUY, OrderType.MARKET, null, "10"), userId);

        assertThat(response.getPrice()).isNull(); // stored as a MARKET order, no limit price
        assertThat(publishedEvent().price()).isEqualByComparingTo("1050.00"); // reference + 5%
    }

    @Test
    void marketBuy_rejectedWhenCashCannotCoverCap() {
        when(referencePriceService.resolve("INFY")).thenReturn(new BigDecimal("1000.00"));
        // 10 x 1050 cap = 10,500 needed; 10,000 covers the reference price but not the cap
        when(balanceService.getCashBalance(userId)).thenReturn(new BigDecimal("10000.00"));

        assertThatThrownBy(() -> orderService.placeOrder(request(Side.BUY, OrderType.MARKET, null, "10"), userId))
                .isInstanceOf(InsufficientBalanceException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void marketSell_capIsBelowReference() {
        when(referencePriceService.resolve("INFY")).thenReturn(new BigDecimal("1000.00"));
        when(balanceService.getHoldings(userId, "infy")).thenReturn(new BigDecimal("10"));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.placeOrder(request(Side.SELL, OrderType.MARKET, null, "10"), userId);

        assertThat(publishedEvent().price()).isEqualByComparingTo("950.00"); // reference - 5%
    }

    @Test
    void marketOrder_withPrice_isRejected() {
        assertThatThrownBy(() -> orderService.placeOrder(request(Side.BUY, OrderType.MARKET, "1000", "1"), userId))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void limitOrder_withoutPrice_isRejected() {
        assertThatThrownBy(() -> orderService.placeOrder(request(Side.BUY, OrderType.LIMIT, null, "1"), userId))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void limitBuy_usesLimitPrice() {
        when(balanceService.getCashBalance(userId)).thenReturn(new BigDecimal("100000.00"));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.placeOrder(request(Side.BUY, OrderType.LIMIT, "1234.50", "2"), userId);

        assertThat(publishedEvent().price()).isEqualByComparingTo("1234.50");
        verifyNoInteractions(referencePriceService);
    }
}
