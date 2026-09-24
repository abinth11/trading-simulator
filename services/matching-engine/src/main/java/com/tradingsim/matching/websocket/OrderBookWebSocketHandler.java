package com.tradingsim.matching.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsim.matching.controller.EngineController.OrderBookStreamSnapshot;
import com.tradingsim.matching.engine.MatchingEngineRouter;
import com.tradingsim.matching.service.EngineStreamService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWebSocketHandler extends TextWebSocketHandler {

    private static final long STREAM_INTERVAL_MILLIS = 1000L;

    private final MatchingEngineRouter router;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> sessionTasks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String symbol = resolveSymbol(session);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> sendSnapshot(session, symbol),
                0,
                STREAM_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
        sessionTasks.put(session.getId(), future);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Order book websocket transport error: {}", exception.getMessage());
        cancelSession(session.getId());
    }

    @PreDestroy
    void shutdown() {
        sessionTasks.values().forEach((task) -> task.cancel(true));
        scheduler.shutdownNow();
    }

    private void sendSnapshot(WebSocketSession session, String symbol) {
        if (!session.isOpen()) {
            cancelSession(session.getId());
            return;
        }

        try {
            OrderBookStreamSnapshot snapshot = router.getOrderBook(symbol)
                    .map(book -> EngineStreamService.buildSnapshot(symbol, book))
                    .orElseGet(() -> new OrderBookStreamSnapshot(
                            symbol,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            0,
                            0,
                            Instant.now()
                    ));

            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "order-book",
                    "payload", snapshot
            ));
            session.sendMessage(new TextMessage(message));
        } catch (IOException exception) {
            log.debug("Closing order book websocket session: {}", exception.getMessage());
            cancelSession(session.getId());
            try {
                session.close();
            } catch (IOException ignored) {
            }
        } catch (Exception exception) {
            log.warn("Failed to publish order book websocket snapshot", exception);
        }
    }

    private static String resolveSymbol(WebSocketSession session) {
        String symbol = session.getUri() != null
                ? org.springframework.web.util.UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("symbol")
                : null;
        return symbol != null && !symbol.isBlank() ? symbol.toUpperCase() : "N/A";
    }

    private void cancelSession(String sessionId) {
        ScheduledFuture<?> future = sessionTasks.remove(sessionId);
        if (future != null) {
            future.cancel(true);
        }
    }
}
