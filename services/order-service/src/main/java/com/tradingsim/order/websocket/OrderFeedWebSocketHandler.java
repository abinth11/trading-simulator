package com.tradingsim.order.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsim.order.service.AdminOrderStreamService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFeedWebSocketHandler extends TextWebSocketHandler {

    private static final long STREAM_INTERVAL_SECONDS = 2L;

    private final AdminOrderStreamService adminOrderStreamService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> sessionTasks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        int orderLimit = parsePositiveInt(session, "orderLimit", 20);
        int tradeLimit = parsePositiveInt(session, "tradeLimit", 12);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> sendSnapshot(session, orderLimit, tradeLimit),
                0,
                STREAM_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        sessionTasks.put(session.getId(), future);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Order feed websocket transport error: {}", exception.getMessage());
        cancelSession(session.getId());
    }

    @PreDestroy
    void shutdown() {
        sessionTasks.values().forEach((task) -> task.cancel(true));
        scheduler.shutdownNow();
    }

    private void sendSnapshot(WebSocketSession session, int orderLimit, int tradeLimit) {
        if (!session.isOpen()) {
            cancelSession(session.getId());
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "order-feed",
                    "payload", adminOrderStreamService.buildSnapshot(orderLimit, tradeLimit)
            ));
            session.sendMessage(new TextMessage(message));
        } catch (IOException exception) {
            log.debug("Closing order feed websocket session: {}", exception.getMessage());
            cancelSession(session.getId());
            try {
                session.close();
            } catch (IOException ignored) {
            }
        } catch (Exception exception) {
            log.warn("Failed to publish order feed websocket snapshot", exception);
        }
    }

    private static int parsePositiveInt(WebSocketSession session, String key, int fallback) {
        String value = session.getUri() != null
                ? org.springframework.web.util.UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key)
                : null;
        if (value == null) {
            return fallback;
        }

        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void cancelSession(String sessionId) {
        ScheduledFuture<?> future = sessionTasks.remove(sessionId);
        if (future != null) {
            future.cancel(true);
        }
    }
}
