package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * SymbolEngine wraps an OrderBook and processes orders on a single dedicated thread.
 *
 * This is the key architectural decision that eliminates race conditions:
 *   - One thread per symbol
 *   - No locks needed inside OrderBook — only one thread ever touches it
 *   - Thread safety is achieved by confinement, not synchronisation
 *
 * Uses a BlockingQueue as the order intake. Orders from the REST API
 * or bots are enqueued; the single engine thread dequeues and processes.
 */
@Slf4j
public class SymbolEngine implements Runnable {

    private final String symbol;
    private final OrderBook orderBook;
    private final BlockingQueue<EngineCommand> commandQueue;
    private final EngineEventHandler eventHandler;

    // Order IDs this engine has already added or cancelled (engine thread only). Kafka delivery is
    // at-least-once and the startup rebuild can overlap redelivery, so the same order may be
    // submitted twice — sometimes while the first copy is still queued. Bounded so it can't grow forever.
    private static final int SEEN_ORDER_CAPACITY = 100_000;
    private final Set<UUID> seenOrderIds = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > SEEN_ORDER_CAPACITY;
        }
    });

    public SymbolEngine(String symbol, EngineEventHandler eventHandler) {
        this.symbol = symbol;
        this.orderBook = new OrderBook(symbol);
        this.commandQueue = new LinkedBlockingQueue<>();
        this.eventHandler = eventHandler;
    }

    // ── Public API (called from other threads) ────────────────────

    public void submitOrder(Order order) {
        commandQueue.offer(new EngineCommand.AddOrder(order));
    }

    public void cancelOrder(UUID orderId) {
        commandQueue.offer(new EngineCommand.CancelOrder(orderId));
    }

    /** Stops after the commands already queued have been processed. */
    public void shutdown() {
        commandQueue.offer(new EngineCommand.Shutdown());
    }

    public OrderBook getOrderBook() {
        return orderBook; // read-only snapshot use — safe for metrics
    }

    // ── Engine thread loop ────────────────────────────────────────

    @Override
    public void run() {
        log.info("SymbolEngine started for {}", symbol);

        while (true) {
            try {
                EngineCommand cmd = commandQueue.take(); // blocks until an order arrives

                if (cmd instanceof EngineCommand.Shutdown) {
                    log.info("SymbolEngine shutting down for {}", symbol);
                    break;
                }

                if (cmd instanceof EngineCommand.AddOrder addCmd) {
                    Order order = addCmd.order();
                    if (!seenOrderIds.add(order.getId())) {
                        log.debug("Ignoring duplicate or already-cancelled order {}", order.getId());
                        continue;
                    }

                    MatchResult result = orderBook.addOrder(order);
                    if (!result.trades().isEmpty()) {
                        eventHandler.onTrades(result.trades()); // publish to event bus
                    }
                    // Trades are published first so a MARKET order's fills land before its remainder is cancelled
                    result.cancelled().forEach(eventHandler::onOrderCancelled);
                }

                if (cmd instanceof EngineCommand.CancelOrder cancelCmd) {
                    UUID orderId = cancelCmd.orderId();
                    boolean removed = orderBook.cancelOrder(orderId);
                    // If the order hasn't arrived yet, remembering it makes the late AddOrder a no-op
                    seenOrderIds.add(orderId);
                    log.debug("Cancel order {}: {}", orderId, removed ? "removed from book" : "not in book");
                    // Either way the order can no longer trade, so the cancel can be finalised
                    eventHandler.onCancelRequestProcessed(orderId);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("SymbolEngine interrupted for {}", symbol);
                break;
            } catch (Exception e) {
                // Critical: never let the engine thread die on a bad order
                log.error("Error processing command in SymbolEngine for {}: {}", symbol, e.getMessage(), e);
            }
        }

        log.info("SymbolEngine stopped for {}", symbol);
    }

    // ── Command sealed interface (ADT pattern) ────────────────────

    public sealed interface EngineCommand
            permits EngineCommand.AddOrder, EngineCommand.CancelOrder, EngineCommand.Shutdown {

        record AddOrder(Order order) implements EngineCommand {}
        record CancelOrder(UUID orderId) implements EngineCommand {}
        record Shutdown() implements EngineCommand {}
    }
}
