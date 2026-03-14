package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.TradeEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

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
    private final Consumer<List<TradeEvent>> tradeEventHandler;
    private volatile boolean running = true;

    public SymbolEngine(String symbol, Consumer<List<TradeEvent>> tradeEventHandler) {
        this.symbol = symbol;
        this.orderBook = new OrderBook(symbol);
        this.commandQueue = new LinkedBlockingQueue<>();
        this.tradeEventHandler = tradeEventHandler;
    }

    // ── Public API (called from other threads) ────────────────────

    public void submitOrder(Order order) {
        commandQueue.offer(new EngineCommand.AddOrder(order));
    }

    public void cancelOrder(UUID orderId) {
        commandQueue.offer(new EngineCommand.CancelOrder(orderId));
    }

    public void shutdown() {
        running = false;
        commandQueue.offer(new EngineCommand.Shutdown()); // unblock the queue
    }

    public OrderBook getOrderBook() {
        return orderBook; // read-only snapshot use — safe for metrics
    }

    // ── Engine thread loop ────────────────────────────────────────

    @Override
    public void run() {
        log.info("SymbolEngine started for {}", symbol);

        while (running) {
            try {
                EngineCommand cmd = commandQueue.take(); // blocks until an order arrives

                if (cmd instanceof EngineCommand.Shutdown) {
                    log.info("SymbolEngine shutting down for {}", symbol);
                    break;
                }

                if (cmd instanceof EngineCommand.AddOrder addCmd) {
                    List<TradeEvent> trades = orderBook.addOrder(addCmd.order());
                    if (!trades.isEmpty()) {
                        tradeEventHandler.accept(trades); // publish to event bus
                    }
                }

                if (cmd instanceof EngineCommand.CancelOrder cancelCmd) {
                    boolean cancelled = orderBook.cancelOrder(cancelCmd.orderId());
                    log.debug("Cancel order {}: {}", cancelCmd.orderId(), cancelled ? "OK" : "NOT_FOUND");
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
