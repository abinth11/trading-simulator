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
        // Always route through the symbol's engine, even a new one: the cancel must be finalised,
        // and the engine thread orders it against any AddOrder for the same order
        getOrCreateEngine(symbol).cancelOrder(orderId);
    }

    public Optional<OrderBookSnapshot> getSnapshot(String symbol) {
        return Optional.ofNullable(engines.get(symbol)).map(SymbolEngine::getSnapshot);
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