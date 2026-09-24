package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MatchingEngineRouter {

    private final ConcurrentHashMap<String, SymbolEngine> engines = new ConcurrentHashMap<>();
    private final EngineEventHandler eventHandler;

    public MatchingEngineRouter(EngineEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @PostConstruct
    public void init() {
        log.info("MatchingEngineRouter initialized. Engines start lazily on first order per symbol.");
    }

    public void submitOrder(Order order) {
        getOrCreateEngine(order.getSymbol()).submitOrder(order);
    }

    public void cancelOrder(String symbol, UUID orderId) {
        SymbolEngine engine = engines.get(symbol);
        if (engine == null) {
            log.warn("Cancel request for unknown symbol: {}", symbol);
            return;
        }
        engine.cancelOrder(orderId);
    }

    public Optional<OrderBook> getOrderBook(String symbol) {
        return Optional.ofNullable(engines.get(symbol)).map(SymbolEngine::getOrderBook);
    }

    public Set<String> getActiveSymbols() {
        return Collections.unmodifiableSet(engines.keySet());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down {} symbol engines...", engines.size());
        engines.values().forEach(SymbolEngine::shutdown);
    }

    private SymbolEngine getOrCreateEngine(String symbol) {
        return engines.computeIfAbsent(symbol, s -> {
            log.info("Creating new SymbolEngine for: {}", s);
            SymbolEngine engine = new SymbolEngine(s, eventHandler);
            Thread.ofVirtual().name("engine-" + s).start(engine);
            return engine;
        });
    }
}