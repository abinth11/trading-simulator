package com.tradingsim.order.service;

import com.tradingsim.order.dto.MarketSimulationDtos.MarketSimulationStatusResponse;
import com.tradingsim.order.dto.MarketSimulationDtos.StartMarketSimulationRequest;
import com.tradingsim.order.dto.OrderDtos.PlaceOrderRequest;
import com.tradingsim.order.entity.Order.OrderType;
import com.tradingsim.order.entity.Order.Side;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSimulationService {

    private static final long DEFAULT_INTERVAL_MS = 3000L;
    private static final int DEFAULT_ORDERS_PER_TICK = 2;
    private static final BigDecimal DEFAULT_CASH_BALANCE = new BigDecimal("2500000.00");
    private static final BigDecimal DEFAULT_HOLDING_QTY = new BigDecimal("2500.000000");

    private static final List<BotSeed> BOT_SEEDS = List.of(
            new BotSeed(
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    "maker_bot_1",
                    "maker1@tradingsim.local"
            ),
            new BotSeed(
                    UUID.fromString("88888888-8888-8888-8888-888888888888"),
                    "maker_bot_2",
                    "maker2@tradingsim.local"
            )
    );

    private final OrderService orderService;
    private final JdbcTemplate jdbcTemplate;
    private final NseMarketDataService nseMarketDataService;
    private final ReferencePriceService referencePriceService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> runningTask = new AtomicReference<>();
    private final AtomicReference<SimulationConfig> currentConfig = new AtomicReference<>(
            new SimulationConfig(DEFAULT_INTERVAL_MS, DEFAULT_ORDERS_PER_TICK, NseMarketDataService.NIFTY_50_SYMBOLS)
    );
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> stoppedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastTickAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicLong ticksExecuted = new AtomicLong();
    private final AtomicLong generatedOrders = new AtomicLong();
    private final AtomicLong successfulOrders = new AtomicLong();
    private final AtomicLong failedOrders = new AtomicLong();

    public synchronized MarketSimulationStatusResponse start(StartMarketSimulationRequest request) {
        if (isRunning()) {
            return getStatus();
        }

        SimulationConfig config = normalize(request, nseMarketDataService.getSymbols());
        bootstrapBots(config.symbols());
        resetMetrics();
        currentConfig.set(config);
        startedAt.set(Instant.now());
        stoppedAt.set(null);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                this::runTickSafely,
                0,
                config.intervalMs(),
                TimeUnit.MILLISECONDS
        );
        runningTask.set(future);
        log.info("Market simulation started: intervalMs={}, ordersPerTick={}, symbols={}",
                config.intervalMs(), config.ordersPerTick(), config.symbols());
        return getStatus();
    }

    public synchronized MarketSimulationStatusResponse stop() {
        ScheduledFuture<?> future = runningTask.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        stoppedAt.set(Instant.now());
        log.info("Market simulation stopped.");
        return getStatus();
    }

    public List<String> getConfiguredSymbols() {
        return nseMarketDataService.getSymbols();
    }

    public MarketSimulationStatusResponse getStatus() {
        SimulationConfig config = currentConfig.get();
        return new MarketSimulationStatusResponse(
                isRunning(),
                config.intervalMs(),
                config.ordersPerTick(),
                config.symbols(),
                startedAt.get(),
                stoppedAt.get(),
                lastTickAt.get(),
                ticksExecuted.get(),
                generatedOrders.get(),
                successfulOrders.get(),
                failedOrders.get(),
                lastError.get()
        );
    }

    @PreDestroy
    void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    private void runTickSafely() {
        try {
            runTick();
            lastError.set(null);
        } catch (Exception exception) {
            failedOrders.incrementAndGet();
            lastError.set(exception.getMessage());
            log.warn("Market simulation tick failed: {}", exception.getMessage(), exception);
        }
    }

    private void runTick() {
        SimulationConfig config = currentConfig.get();
        // Only the bots bootstrapBots() funded — other BOT users may have no cash or holdings
        List<BotSeed> bots = BOT_SEEDS;

        for (int i = 0; i < config.ordersPerTick(); i += 2) {
            String symbol = randomSymbol(config.symbols());
            BigDecimal referencePrice = referencePriceService.resolve(symbol);
            BigDecimal midpoint = movePrice(referencePrice);
            BigDecimal priceStep = midpoint.multiply(randomBetween("0.0002", "0.0008")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellPrice = midpoint.subtract(priceStep).max(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal buyPrice = midpoint.add(priceStep).setScale(2, RoundingMode.HALF_UP);
            BigDecimal quantity = randomQuantity(symbol);

            BotSeed buyer = bots.get(ThreadLocalRandom.current().nextInt(bots.size()));
            BotSeed seller = bots.stream()
                    .filter(bot -> !bot.id().equals(buyer.id()))
                    .findAny()
                    .orElse(bots.get(0));

            submitOrder(buyer.id(), symbol, Side.BUY, buyPrice, quantity);
            submitOrder(seller.id(), symbol, Side.SELL, sellPrice, quantity);
        }

        ticksExecuted.incrementAndGet();
        lastTickAt.set(Instant.now());
    }

    private void submitOrder(UUID userId, String symbol, Side side, BigDecimal price, BigDecimal quantity) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setSymbol(symbol);
        request.setSide(side);
        request.setOrderType(OrderType.LIMIT);
        request.setPrice(price);
        request.setQuantity(quantity);

        generatedOrders.incrementAndGet();
        try {
            orderService.placeOrder(request, userId);
            successfulOrders.incrementAndGet();
        } catch (Exception exception) {
            failedOrders.incrementAndGet();
            lastError.set(exception.getMessage());
            log.debug("Simulation order rejected for user {} {} {}: {}", userId, side, symbol, exception.getMessage());
        }
    }

    private void bootstrapBots(List<String> symbols) {
        for (BotSeed bot : BOT_SEEDS) {
            jdbcTemplate.update("""
                    INSERT INTO users (id, email, username, password_hash, cash_balance, role, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, 'simulation', ?, 'BOT', TRUE, NOW(), NOW())
                    ON CONFLICT (id) DO UPDATE
                    SET cash_balance = EXCLUDED.cash_balance,
                        role = 'BOT',
                        is_active = TRUE,
                        updated_at = NOW()
                    """,
                    bot.id(), bot.email(), bot.username(), DEFAULT_CASH_BALANCE);

            for (String symbol : symbols) {
                BigDecimal avgPrice = nseMarketDataService.getBootstrapPrice(symbol);
                jdbcTemplate.update("""
                        INSERT INTO portfolio_holdings (id, user_id, symbol, quantity, avg_buy_price, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW())
                        ON CONFLICT (user_id, symbol) DO UPDATE
                        SET quantity = GREATEST(portfolio_holdings.quantity, EXCLUDED.quantity),
                            avg_buy_price = EXCLUDED.avg_buy_price,
                            updated_at = NOW()
                        """,
                        UUID.randomUUID(), bot.id(), symbol, DEFAULT_HOLDING_QTY, avgPrice);
            }
        }

        // Prewarm the matching engine: place one buy+sell pair per symbol so all order
        // books are created immediately and show up as active in the dashboard.
        List<BotSeed> bots = BOT_SEEDS;
        BotSeed buyer = bots.get(0);
        BotSeed seller = bots.get(1);
        for (String symbol : symbols) {
            BigDecimal ref = nseMarketDataService.getBootstrapPrice(symbol);
            BigDecimal step = ref.multiply(new BigDecimal("0.0005")).setScale(2, RoundingMode.HALF_UP);
            submitOrder(buyer.id(), symbol, Side.BUY,  ref.add(step),      new BigDecimal("1.000000"));
            submitOrder(seller.id(), symbol, Side.SELL, ref.subtract(step), new BigDecimal("1.000000"));
        }
        log.info("Prewarmed {} order books in the matching engine", symbols.size());
    }

    private static SimulationConfig normalize(StartMarketSimulationRequest request, List<String> defaultSymbols) {
        long intervalMs = request != null && request.intervalMs() != null
                ? Math.max(1000L, request.intervalMs())
                : DEFAULT_INTERVAL_MS;

        int ordersPerTick = request != null && request.ordersPerTick() != null
                ? Math.max(2, request.ordersPerTick())
                : DEFAULT_ORDERS_PER_TICK;
        if (ordersPerTick % 2 != 0) {
            ordersPerTick += 1;
        }

        List<String> symbols = request != null && request.symbols() != null && !request.symbols().isEmpty()
                ? request.symbols().stream().map(String::toUpperCase).distinct().toList()
                : defaultSymbols;

        return new SimulationConfig(intervalMs, ordersPerTick, symbols);
    }

    private void resetMetrics() {
        ticksExecuted.set(0);
        generatedOrders.set(0);
        successfulOrders.set(0);
        failedOrders.set(0);
        lastTickAt.set(null);
        lastError.set(null);
    }

    private boolean isRunning() {
        ScheduledFuture<?> future = runningTask.get();
        return future != null && !future.isCancelled() && !future.isDone();
    }

    private static String randomSymbol(List<String> symbols) {
        return symbols.get(ThreadLocalRandom.current().nextInt(symbols.size()));
    }

    private static BigDecimal movePrice(BigDecimal referencePrice) {
        BigDecimal drift = randomBetween("-0.0030", "0.0030");
        return referencePrice.multiply(BigDecimal.ONE.add(drift)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal randomBetween(String min, String max) {
        double minValue = Double.parseDouble(min);
        double maxValue = Double.parseDouble(max);
        double value = ThreadLocalRandom.current().nextDouble(minValue, maxValue);
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal randomQuantity(String symbol) {
        return (switch (symbol) {
            case "RELIANCE", "TCS", "HDFC" -> BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(5, 21));
            case "INFY" -> BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(20, 151));
            case "WIPRO" -> BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(50, 301));
            default -> BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(5, 51));
        }).setScale(6, RoundingMode.HALF_UP);
    }

    private record SimulationConfig(long intervalMs, int ordersPerTick, List<String> symbols) {}

    private record BotSeed(UUID id, String username, String email) {}
}
