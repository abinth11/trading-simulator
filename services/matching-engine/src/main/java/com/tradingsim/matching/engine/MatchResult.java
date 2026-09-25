package com.tradingsim.matching.engine;

import com.tradingsim.matching.model.Order;
import com.tradingsim.matching.model.TradeEvent;

import java.util.List;

/**
 * Outcome of adding one order to the book.
 *
 * @param trades    trades executed, in execution order
 * @param cancelled orders the engine cancelled while matching: a MARKET order's unfilled
 *                  remainder, or a resting order removed by self-trade prevention
 */
public record MatchResult(List<TradeEvent> trades, List<Order> cancelled) {}
