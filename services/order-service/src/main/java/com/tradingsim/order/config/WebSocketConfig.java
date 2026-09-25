package com.tradingsim.order.config;

import com.tradingsim.order.websocket.AdminHandshakeInterceptor;
import com.tradingsim.order.websocket.OrderFeedWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderFeedWebSocketHandler orderFeedWebSocketHandler;
    private final AdminHandshakeInterceptor adminHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderFeedWebSocketHandler, "/ws/admin/orders")
                .addInterceptors(adminHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
