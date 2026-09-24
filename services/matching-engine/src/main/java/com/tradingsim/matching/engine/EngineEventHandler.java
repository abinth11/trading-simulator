package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.TradeEvent;

import java.util.List;

/**
 * Receives the outcomes of matching from a SymbolEngine thread.
 * Calls for a symbol arrive in order, on that symbol's engine thread.
 */
public interface EngineEventHandler {

    /** A batch of trades produced by one incoming order. */
    void onTrades(List<TradeEvent> trades);

    /** A MARKET order finished matching with quantity left over; the remainder was cancelled. */
    void onOrderExpired(Order order);
}
